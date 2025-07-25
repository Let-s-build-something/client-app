package base.global.verification

import androidx.lifecycle.viewModelScope
import augmy.interactive.shared.ext.ifNull
import data.shared.SharedModel
import io.ktor.util.encodeBase64
import korlibs.io.util.getOrNullLoggingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.none
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.key
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.key.decodeRecoveryKey
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.reflect.KClass

internal val verificationModule = module {
    viewModelOf(::DeviceVerificationModel)
}

data class ComparisonByUserData(
    val emojis: List<Pair<String, Map<String, String>>>,
    val decimals: List<Int>,
    private val onSend: suspend (matches: Boolean) -> Unit
) {
    suspend fun send(matches: Boolean) = withContext(Dispatchers.IO) {
        onSend(matches)
    }
}

sealed class LauncherState {
    var selfTransactionId: String? = null

    class SelfVerification(
        val methods: Set<SelfVerificationMethod>
    ): LauncherState()

    class TheirRequest(
        val fromDevice: String,
        val onReady: () -> Unit
    ): LauncherState()

    data class Bootstrap(
        val methods: List<KClass<out SelfVerificationMethod>>
    ): LauncherState()

    data class ComparisonByUser(
        val data: ComparisonByUserData,
        val senderDeviceId: String
    ) : LauncherState()
    data object Success: LauncherState()
    data object Loading: LauncherState()
    data object Hidden: LauncherState()
    data object Canceled: LauncherState()

    val isFinished: Boolean
        get() = this is Hidden || this is Canceled || this is Success
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceVerificationModel: SharedModel() {
    private val logger = korlibs.logger.Logger("DeviceVerification")
    private val supportedMethods = setOf(Sas)

    private var hasActiveSubscribers = false

    private val _launcherState = MutableStateFlow<LauncherState>(LauncherState.Hidden)
    private val _verificationResult = MutableStateFlow<Result<Unit>?>(null)
    val isLoading = MutableStateFlow(false)
    val launcherState = _launcherState.asStateFlow()

    val verificationResult = _verificationResult.asStateFlow()

    init {
        viewModelScope.launch {
            sharedDataManager.matrixClient
                .filterNotNull()
                .first()
            subscribe()
        }
        viewModelScope.launch {
            awaitCancellation()
            clear()
        }
    }

    val activeVerificationState: StateFlow<ActiveVerificationState?> =
        sharedDataManager.matrixClient
            .filterNotNull()
            .flatMapLatest { client ->
                client.verification.activeDeviceVerification
                    .filterNotNull()
                    .flatMapLatest { verification ->
                        verification.state
                    }
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.Eagerly,
                        initialValue = null
                    )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private fun subscribe() {
        viewModelScope.launch {
            activeVerificationState.collect { state ->
                onActiveState(state)
            }
        }
        viewModelScope.launch {
            sharedDataManager.matrixClient.collect { client ->
                if (client == null) clear() else subscribeToVerificationMethods(client = client)
            }
        }
    }

    fun hide() {
        _launcherState.value = LauncherState.Hidden
    }

    fun clear() {
        logger.debug { "clear()" }
        cancel(restart = false)
    }

    private suspend fun MatrixClient.isRootTrust(): Boolean = withContext(Dispatchers.IO) {
        di.getOrNull<KeyStore>()?.getSecrets()[SecretType.M_CROSS_SIGNING_SELF_SIGNING] != null
    }

    private fun subscribeToVerificationMethods(client: MatrixClient) {
        viewModelScope.launch {
            client.verification.getSelfVerificationMethods().shareIn(this, SharingStarted.Eagerly).collect { verification ->
                logger.debug { "selfVerificationMethods: $verification" }
                if (_launcherState.value is LauncherState.Hidden) {
                    when(verification) {
                        is VerificationService.SelfVerificationMethods.AlreadyCrossSigned -> {
                            if (_launcherState.value is LauncherState.SelfVerification) {
                                clear()
                            }
                        }
                        is VerificationService.SelfVerificationMethods.CrossSigningEnabled -> {
                            logger.debug { "selfVerificationMethod, methods: ${verification.methods.map { it::class }}" }

                            _launcherState.value = if(verification.methods.isEmpty()) {
                                LauncherState.Bootstrap(
                                    listOf(
                                        SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class,
                                        SelfVerificationMethod.AesHmacSha2RecoveryKey::class
                                    )
                                )
                            } else LauncherState.SelfVerification(methods = verification.methods)
                        }
                        is VerificationService.SelfVerificationMethods.NoCrossSigningEnabled -> {
                            // TODO #86c2y7krb this is how we recognize a new user
                            if (_launcherState.value.isFinished) {
                                _launcherState.value = LauncherState.Bootstrap(
                                    listOf(
                                        SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class,
                                        SelfVerificationMethod.AesHmacSha2RecoveryKey::class
                                    )
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun cancel(restart: Boolean = true, manual: Boolean = true) {
        logger.debug { "cancel()" }
        viewModelScope.launch {
            hasActiveSubscribers = false
            if(manual) {
                matrixClient?.verification?.activeDeviceVerification?.value?.cancel()
            }
            isLoading.value = false
            val state = if (manual) LauncherState.Hidden else LauncherState.Canceled

            _launcherState.value = if(restart) {
                val isCrossSigned = matrixClient?.verification?.getSelfVerificationMethods()?.none {
                    it is VerificationService.SelfVerificationMethods.AlreadyCrossSigned
                } != false
                if (_launcherState.value.selfTransactionId != null && !isCrossSigned) {
                    (matrixClient?.verification?.getSelfVerificationMethods()?.firstOrNull()
                            as? VerificationService.SelfVerificationMethods.CrossSigningEnabled)?.let { verification ->
                        LauncherState.SelfVerification(methods = verification.methods)
                    } ?: state
                } else state
            } else state
            if(manual) _launcherState.value.selfTransactionId = null
        }
    }

    fun matchChallenge(matches: Boolean) {
        isLoading.value = true
        viewModelScope.launch {
            (_launcherState.value as? LauncherState.ComparisonByUser)?.data?.send(matches)
        }
    }

    fun verifySelf(method: SelfVerificationMethod, passphrase: String) {
        if(_launcherState.value !is LauncherState.SelfVerification) return

        viewModelScope.launch {
            _verificationResult.value = null
            isLoading.value = true

            when(method) {
                is SelfVerificationMethod.AesHmacSha2RecoveryKey -> {
                    _verificationResult.value = method.verify(passphrase)
                }
                is SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase-> {
                    _verificationResult.value = method.verify(passphrase).also {
                        logger.debug { "verifySelf(), verify: ${it.getOrNullLoggingError()}, isSuccess: ${it.isSuccess}" }
                    }
                }
                is SelfVerificationMethod.CrossSignedDeviceVerification -> {
                    method.createDeviceVerification().getOrNullLoggingError().let {
                        _launcherState.update { prev ->
                            prev.apply {
                                selfTransactionId = it?.transactionId
                            }
                        }
                        logger.debug { "verifySelf, theirDeviceId: ${it?.theirDeviceId}, transactionId: ${it?.transactionId}" }
                        if(it == null) isLoading.value = false
                    }
                }
            }
        }
    }

    private suspend fun onActiveState(state: ActiveVerificationState?) {
        logger.debug { "onActiveState: $state" }
        when(state) {
            is ActiveVerificationState.TheirRequest -> {
                logger.debug { "theirRequest, content: ${state.content}" }
                if (matrixClient?.isRootTrust() == true) {
                    when(val content = state.content) {
                        is VerificationRequestToDeviceEventContent -> {
                            logger.debug { "VerificationRequestToDeviceEventContent, methods: ${content.methods}" }
                            content.methods.forEach { method ->
                                when(method) {
                                    Sas -> {
                                        if (_launcherState.value.selfTransactionId == content.transactionId) {
                                            logger.debug { "marking Sas as ready, deviceId: ${content.fromDevice}" }
                                            state.ready()
                                        } else {
                                            _launcherState.value = LauncherState.TheirRequest(
                                                fromDevice = content.fromDevice,
                                                onReady = {
                                                    isLoading.value = true
                                                    viewModelScope.launch {
                                                        logger.debug { "marking Sas as ready, deviceId: ${content.fromDevice}" }
                                                        state.ready()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    is VerificationMethod.Unknown -> {}
                                }
                            }
                        }
                    }
                }
            }
            is ActiveVerificationState.Start -> {
                when(val method = state.method) {
                    is ActiveSasVerificationMethod -> {
                        viewModelScope.launch {
                            method.state.collect { sasState ->
                                logger.debug { "ActiveSasVerificationMethod, sasState: $sasState" }
                                when(sasState) {
                                    is ActiveSasVerificationState.ComparisonByUser -> {
                                        _launcherState.value = LauncherState.ComparisonByUser(
                                            data = ComparisonByUserData(
                                                onSend = { matches ->
                                                    if(matches) sasState.match() else sasState.noMatch()
                                                },
                                                decimals = sasState.decimal,
                                                emojis = sasState.emojis.mapNotNull {
                                                    emojisWithTranslation[it.first]
                                                }
                                            ),
                                            senderDeviceId = state.senderDeviceId
                                        )
                                        isLoading.value = false
                                    }
                                    is ActiveSasVerificationState.TheirSasStart -> {
                                        if (_launcherState.value is LauncherState.SelfVerification
                                            || (_launcherState.value is LauncherState.TheirRequest)
                                        ) {
                                            logger.debug { "accepting Their Sas, deviceId: ${sasState.content.fromDevice}" }
                                            sasState.accept()
                                        }
                                    }
                                    is ActiveSasVerificationState.Accept -> {}
                                    is ActiveSasVerificationState.OwnSasStart -> {}
                                    is ActiveSasVerificationState.WaitForKeys -> {}
                                    is ActiveSasVerificationState.WaitForMacs -> {}
                                }
                            }
                        }
                    }
                }
            }
            is ActiveVerificationState.Ready -> {
                state.methods.firstOrNull { supportedMethods.contains(it) }?.let { method ->
                    if(_launcherState.value.selfTransactionId == null) {
                        logger.debug { "starting $method" }
                    }else {
                        logger.debug { "NOT starting $method" }
                        state.start(method)
                    }
                }
            }
            is ActiveVerificationState.Done -> {
                if(_launcherState.value !is LauncherState.Hidden) {
                    isLoading.value = false
                    _launcherState.value = LauncherState.Success
                }
            }
            is ActiveVerificationState.WaitForDone -> _launcherState.value = LauncherState.Loading
            is ActiveVerificationState.Cancel -> {
                if (!state.isOurOwn && _launcherState.value !is LauncherState.SelfVerification) {
                    cancel(restart = false, manual = false)
                }
            }
            null -> {
                logger.debug { "deviceVerification state is null" }
            }
            else -> {}
        }
    }

    fun bootstrap(newPassphrase: String) {
        isLoading.value = true
        viewModelScope.launch {
            (if (newPassphrase.contains(' ')) {
                bootstrapCrossSigningRecoveryKey(recoveryKeyString = newPassphrase)
            }else bootstrapCrossSigning(passphrase = newPassphrase))?.let { result ->
                logger.debug { "bootstrapCrossSigning result: $result" }
                if(result is UIA.Success<*>) {
                    finishDeviceVerification()
                    _launcherState.value = LauncherState.Success
                }
                isLoading.value = false
            }.ifNull {
                isLoading.value = false
            }
        }
    }

    private suspend fun bootstrapCrossSigning(passphrase: String): UIA<out Any?>? {
        return matrixClient?.key?.bootstrapCrossSigningFromPassphrase(
            passphrase = passphrase
        )?.result?.getOrThrow()?.let { result ->
            logger.debug { "bootstrapCrossSigningFromPassphrase result: $result" }
            processBootstrapResult(result)
        }
    }

    private suspend fun bootstrapCrossSigningRecoveryKey(
        recoveryKeyString: String
    ): UIA<out Any?>? {
        val recoveryKey = decodeRecoveryKey(recoveryKeyString)
        val passphraseInfo = AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2(
            salt = SecureRandom.nextBytes(32).encodeBase64(),
            iterations = 210_000,
            bits = 32 * 8
        )
        val iv = SecureRandom.nextBytes(16)
        val secretKeyEventContent = AesHmacSha2Key(
            passphrase = passphraseInfo,
            iv = iv.encodeBase64(),
            mac = createAesHmacSha2MacFromKey(key = recoveryKey, iv = iv)
        )

        return matrixClient?.key?.bootstrapCrossSigning(
            recoveryKey = recoveryKey,
            secretKeyEventContent = secretKeyEventContent
        )?.result?.getOrThrow()?.let { result ->
            logger.debug { "bootstrapCrossSigningFromPassphrase result: $result" }
            processBootstrapResult(result)
        }
    }

    private suspend fun <T>processBootstrapResult(result: UIA<T>, count: Int = 0): UIA<out Any?> {
        logger.debug { "processBootstrapResult: $result" }
        return when(result) {
            is UIA.Step -> {
                authService.createLoginRequest()?.let { request ->
                    logger.debug { "auth request: $request, count: $count" }
                    if(count < 3) {
                        result.authenticate(request).getOrNullLoggingError()?.let { res ->
                            logger.debug { "auth res: $res" }
                            processBootstrapResult(result = res, count = count + 1)
                        }
                    }else result
                } ?: result
            }
            is UIA.Error<*> -> {
                authService.createLoginRequest()?.let { request ->
                    if(count < 3) {
                        result.authenticate(request).getOrNullLoggingError()?.let { res ->
                            processBootstrapResult(result = res, count = count + 1)
                        }
                    }else result
                } ?: result
            }
            is UIA.Success<*> -> result
        }
    }

    private fun finishDeviceVerification() {
        viewModelScope.launch {
            matrixClient?.let { client ->
                client.key.getDeviceKeys(client.userId).firstOrNull()?.mapNotNull { key ->
                    key.deviceId.takeIf { it != client.deviceId }
                }?.toSet()?.takeIf { it.isNotEmpty() }?.let { deviceIds ->
                    client.verification.createDeviceVerificationRequest(
                        theirDeviceIds = deviceIds,
                        theirUserId = client.userId
                    )
                }
            }
        }
    }
}
