package ui.login

import androidx.lifecycle.viewModelScope
import base.navigation.NavigationNode
import base.utils.MatrixUtils
import base.utils.MatrixUtils.AUGMY_HOME_SERVER_ADDRESS
import base.utils.MatrixUtils.ErrorCode.USER_IN_USE
import base.utils.MatrixUtils.LOGIN_EMAIL_IDENTITY
import base.utils.deeplinkHost
import base.utils.openLink
import base.utils.orZero
import coil3.toUri
import data.io.app.ClientStatus
import data.io.app.SecureSettingsKeys
import data.io.app.SettingsKeys.KEY_CLIENT_STATUS
import data.io.base.RecaptchaParams
import data.io.matrix.auth.AuthenticationCredentials
import data.io.matrix.auth.AuthenticationData
import data.io.matrix.auth.MatrixAuthenticationPlan
import data.io.matrix.auth.MatrixAuthenticationResponse
import data.io.matrix.auth.MatrixIdentifierData
import data.shared.SharedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import utils.SharedLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun loginModule() = module {
    factory { LoginRepository(get()) }
    viewModelOf(::LoginModel)
}

/** Communication between the UI, the control layers, and control and data layers */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginModel(
    private val dataManager: LoginDataManager,
    private val repository: LoginRepository
): SharedModel() {
    data class HomeServerResponse(
        val state: HomeServerState,
        val plan: MatrixAuthenticationPlan? = null,
        val address: String,
        val supportsEmail: Boolean = false,
        val registrationEnabled: Boolean = true
    ) {
        override fun toString(): String {
            return "{" +
                    "state: $state, " +
                    "plan: $plan," +
                    "address: $address," +
                    "supportsEmail: $supportsEmail" +
                    "}"
        }
    }
    enum class HomeServerState {
        Valid,
        Invalid
    }

    private val _loginResult = MutableSharedFlow<LoginResultType?>()
    private val _isLoading = MutableStateFlow(false)
    private val _matrixAuthResponse = MutableStateFlow<MatrixAuthenticationResponse?>(null)
    private var session: String? = null

    /** current client status */
    val clientStatus = MutableStateFlow(ClientStatus.NEW)

    /** Sends signal to UI about a response that happened */
    val loginResult = _loginResult.asSharedFlow()
    val isLoading = _isLoading.asStateFlow()

    val loginHomeserverResponse = dataManager.loginHomeserverResponse.asStateFlow()
    val registrationHomeserverResponse = dataManager.registrationHomeserverResponse.asStateFlow()

    val supportsEmail = dataManager.loginHomeserverResponse.mapLatest { res ->
        res?.plan?.flows?.any {
            it.stages?.contains(LOGIN_EMAIL_IDENTITY) == true
        } != false || res.address == AUGMY_HOME_SERVER_ADDRESS
    }

    val ssoFlow = dataManager.loginHomeserverResponse.mapLatest { res ->
        res?.plan?.flows?.find {
            it.type == MatrixUtils.LOGIN_SSO || it.type == MatrixUtils.LOGIN_AUGMY_SSO
        }
    }

    val homeserverResponseAddress: String
        get() = registrationHomeserverResponse.value?.address
            ?: loginHomeserverResponse.value?.address
            ?: AUGMY_HOME_SERVER_ADDRESS

    private var ssoNonce: String?
        get() = runBlocking { settings.getStringOrNull(SecureSettingsKeys.KEY_LOGIN_NONCE) }
        set(value) {
            runBlocking {
                if(value == null) settings.remove(SecureSettingsKeys.KEY_LOGIN_NONCE)
                else settings.putString(SecureSettingsKeys.KEY_LOGIN_NONCE, value)
            }
        }

    private var providerId: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ClientStatus.entries.find {
                it.name == settings.getStringOrNull(KEY_CLIENT_STATUS)
            }?.let { status ->
                clientStatus.value = status
            }
        }

        selectHomeServer(homeserverAddress)
    }

    /** Changes loading status */
    fun setLoading(state: Boolean) {
        _isLoading.value = state
    }

    /** Clears current Matrix progress and cached information */
    fun clearMatrixProgress() {
        _matrixProgress.value = null
        ssoNonce = null
        providerId = null
    }

    /** Validates Matrix username availability */
    fun validateUsername(
        address: String,
        username: String
    ) {
        if(username.isEmpty()) return
        viewModelScope.launch {
            repository.validateUsername(
                address = address,
                username = username
            )?.let {
                if(it.code == USER_IN_USE) {
                    _loginResult.emit(LoginResultType.USERNAME_EXISTS)
                }
            }
        }
    }

    private fun MatrixAuthenticationPlan?.handleHomeserverResponse(address: String): HomeServerResponse {
        val registrationEnabled = this?.error?.contains("Registration has been disabled.") != true

        return if (this != null) {
            this@LoginModel.session = this.session ?: this@LoginModel.session
            HomeServerResponse(
                state = if(flows != null) HomeServerState.Valid else HomeServerState.Invalid,
                plan = this,
                address = address,
                /*supportsEmail = if(screenType == LoginScreenType.SIGN_UP) true else {
                    (homeserverAbilityCache[address] ?: (authService.loginWithIdentifier(
                        setupAutoLogin = false,
                        homeserver = address,
                        identifier = MatrixIdentifierData(
                            type = Matrix.Id.THIRD_PARTY,
                            medium = Matrix.Medium.EMAIL,
                            address = "email@email.com"
                        ),
                        password = "-",
                        token = null
                    ).error?.message?.contains("Bad login type") == false)).also { answer ->
                        homeserverAbilityCache[address] = answer
                    }
                },*/
                registrationEnabled = registrationEnabled
            )
        } else {
            HomeServerResponse(
                state = HomeServerState.Invalid,
                address = address,
                registrationEnabled = registrationEnabled
            )
        }
    }

    /** Clears home server information */
    fun selectHomeServer(address: String) {
        _isLoading.value = true

        viewModelScope.launch {
            if (loginHomeserverResponse.value?.address != address
                || loginHomeserverResponse.value?.state == HomeServerState.Valid
            ) {
                dataManager.loginHomeserverResponse.value = repository.dummyMatrixLogin(address = address)
                    ?.handleHomeserverResponse(address)
            }
            if (registrationHomeserverResponse.value?.address == address
                || registrationHomeserverResponse.value?.state == HomeServerState.Valid
            ) {
                dataManager.registrationHomeserverResponse.value = repository.dummyMatrixRegister(address = address)
                    ?.handleHomeserverResponse(address)
            }
            _isLoading.value = false
        }
    }

    private val _matrixProgress = MutableStateFlow<MatrixProgress?>(null)
    val matrixProgress = _matrixProgress.asStateFlow()

    /** Makes a new request for a token on Matrix homeserver */
    fun matrixRequestToken() {
        if(_matrixProgress.value?.secret == null) return
        viewModelScope.launch {
            repository.requestRegistrationToken(
                secret = _matrixProgress.value?.secret ?: "",
                email = _matrixProgress.value?.email ?: "",
                address = homeserverResponseAddress
            ).also { res ->
                res.error?.retryAfterMs?.let {
                    _matrixProgress.value = _matrixProgress.value?.copy(retryAfter = it)
                }
                res.success?.data?.sid?.let {
                    _matrixProgress.value = _matrixProgress.value?.copy(sid = it)
                }
            }
        }
    }

    /** Submits recaptcha result and continues in the flow stages */
    fun matrixStepOver(
        type: String,
        recaptchaJson: String? = null,
        agreements: List<String>? = null
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            val recaptcha = if(recaptchaJson != null) {
                KoinPlatform.getKoin().get<Json>().decodeFromString<RecaptchaParams>(
                    recaptchaJson
                ).token
            }else _matrixProgress.value?.recaptcha
            val userAccepts = agreements ?: _matrixProgress.value?.agreements
            val lastIndex = matrixProgress.value?.response?.flows?.firstOrNull()?.stages?.lastIndex.orZero()

            _matrixProgress.value = _matrixProgress.value?.copy(
                recaptcha = recaptcha,
                agreements = userAccepts,
                index = _matrixProgress.value?.index?.plus(1)?.coerceAtMost(lastIndex).orZero()
            )?.also { progress ->
                repository.registerWithUsername(
                    address = homeserverResponseAddress,
                    username = progress.username ?: "",
                    password = progress.password,
                    authenticationData = AuthenticationData(
                        session = session,
                        type = type,
                        response = recaptcha,
                        userAccepts = userAccepts,
                        credentials = AuthenticationCredentials(
                            clientSecret = progress.secret ?: "",
                            sid = progress.sid ?: ""
                        )
                    ),
                    deviceId = authService.getDeviceId()
                ).also { response ->
                    if(progress.index == lastIndex) {
                        signUpWithPassword(
                            username = progress.username,
                            email = progress.email ?: "",
                            password = progress.password,
                            screenType = LoginScreenType.SIGN_UP,
                            response = response
                        )
                    }
                }
            }
            delay(800)
            _isLoading.value = false
        }
    }

    /** Requests signup with an email and a password */
    @OptIn(ExperimentalUuidApi::class)
    fun signUpWithPassword(
        username: String?,
        email: String,
        password: String,
        screenType: LoginScreenType,
        response: MatrixAuthenticationResponse? = null
    ) {
        SharedLogger.logger.debug { "signUpWithPassword, username: $username, email: $email, screenType: $screenType" }
        _isLoading.value = true
        viewModelScope.launch {
            val res = (if(screenType == LoginScreenType.SIGN_UP && response == null) {
                val secret = Uuid.random().toString()

                // we check for single EP registration first
                val flow = dataManager.registrationHomeserverResponse.value?.plan?.flows?.firstOrNull()
                if(flow?.stages?.size == 1 && flow.stages.firstOrNull() == MatrixUtils.LOGIN_DUMMY) {
                    repository.registerWithUsername(
                        address = homeserverResponseAddress,
                        username = username,
                        password = password,
                        authenticationData = AuthenticationData(
                            session = session,
                            type = MatrixUtils.LOGIN_DUMMY
                        ),
                        deviceId = authService.getDeviceId()
                    )?.also {
                        session = it.session
                    }
                }else {
                    val requiresEmail = flow?.stages?.contains(LOGIN_EMAIL_IDENTITY) == true
                    // if flow contains email verification, we should check for duplicity first
                    val check = if(requiresEmail) {
                        repository.requestRegistrationToken(
                            secret = secret,
                            email = email,
                            address = homeserverResponseAddress
                        )
                    }else null

                    when(check?.error?.code) {
                        MatrixUtils.ErrorCode.CREDENTIALS_IN_USE -> {
                            _isLoading.value = false
                            _loginResult.emit(LoginResultType.EMAIL_EXISTS)
                        }
                        MatrixUtils.ErrorCode.CREDENTIALS_DENIED, MatrixUtils.ErrorCode.FORBIDDEN, MatrixUtils.ErrorCode.UNKNOWN -> {
                            _isLoading.value = false
                            _loginResult.emit(LoginResultType.FAILURE)
                        }
                        else -> {
                            (repository.registerWithUsername(
                                address = homeserverResponseAddress,
                                username = null,
                                password = null,
                                authenticationData = AuthenticationData(
                                    session = null,
                                    type = null
                                ),
                                deviceId = authService.getDeviceId()
                            ) ?: dataManager.registrationHomeserverResponse.value?.plan)?.also { response ->
                                session = response.session
                                _matrixProgress.value = MatrixProgress(
                                    username = username,
                                    email = email,
                                    password = password,
                                    response = response,
                                    secret = secret
                                )
                            }
                        }
                    }
                    return@launch
                }
            }else null) ?: response
            _matrixAuthResponse.value = res

            if(screenType == LoginScreenType.SIGN_UP) {
                if(res?.userId != null || username == null) {
                    _matrixAuthResponse.value = res
                    clearMatrixProgress()
                    val isUser = !username.isNullOrBlank()

                    authService.registerWithIdentifier(
                        homeserver = res?.homeserver ?: homeserverResponseAddress,
                        password = password,
                        response = res,
                        identifier = MatrixIdentifierData(
                            type = if(isUser) MatrixUtils.Id.USER else MatrixUtils.Id.THIRD_PARTY,
                            medium = MatrixUtils.Medium.EMAIL.takeIf { !isUser },
                            address = email.takeIf { !isUser },
                            user = username.takeIf { isUser }
                        )
                    )
                    loginWithCredentials(
                        username = username,
                        email = email,
                        password = password
                    )
                }else {
                    _isLoading.value = false
                    _loginResult.emit(LoginResultType.FAILURE)
                }
            }else loginWithCredentials(username, email, password)
        }
    }

    /** Requests a sign-in with an email and a password */
    private suspend fun loginWithCredentials(
        username: String?,
        email: String,
        password: String
    ) {
        val res = if(_matrixAuthResponse.value?.userId == null) {
            val isUser = !username.isNullOrBlank()
            authService.loginWithIdentifier(
                homeserver = homeserverResponseAddress,
                identifier = MatrixIdentifierData(
                    type = if(isUser) MatrixUtils.Id.USER else MatrixUtils.Id.THIRD_PARTY,
                    medium = MatrixUtils.Medium.EMAIL.takeIf { !isUser },
                    address = email.takeIf { !isUser },
                    user = username.takeIf { isUser }
                ),
                password = password,
                token = null
            ).let {
                if(it.error?.code == MatrixUtils.ErrorCode.FORBIDDEN) {
                    _loginResult.emit(LoginResultType.INVALID_CREDENTIAL)
                    return
                }else if(it.error != null) {
                    _loginResult.emit(LoginResultType.FAILURE)
                    return
                }
                it.success?.data
            }
        }else _matrixAuthResponse.value

        if (res?.userId != null || matrixClient != null) {
            _matrixAuthResponse.value = res
            clearMatrixProgress()
            initUserObject()
        }else _loginResult.emit(LoginResultType.FAILURE)
    }

    /** Requests the URL for redirecting user */
    @OptIn(ExperimentalUuidApi::class)
    fun requestSsoRedirect(idpId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val ssoNonce = Uuid.random().toString()
            this@LoginModel.ssoNonce = "${ssoNonce}_$homeserverResponseAddress"
            val redirectUrl = "${deeplinkHost}${NavigationNode.Login(nonce = ssoNonce).deepLink}"
            openLink(
                "https://${homeserverResponseAddress}/_matrix/client/v3/login/sso/redirect" +
                        (if(idpId != null)"/$idpId" else "") +
                        "?redirectUrl=${redirectUrl.toUri()}"
            )
        }
    }

    fun loginWithToken(nonce: String, token: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val savedNonceInfo = ssoNonce?.split("_")
            val savedNonce = savedNonceInfo?.firstOrNull()
            val savedHomeserver = savedNonceInfo?.lastOrNull()

            if(nonce != savedNonce && savedHomeserver != null) {
                _loginResult.emit(LoginResultType.AUTH_SECURITY)
            }else {
                if (loginHomeserverResponse.value == null) {
                    dataManager.loginHomeserverResponse.value = HomeServerResponse(
                        address = savedHomeserver ?: homeserverAddress,
                        state = HomeServerState.Valid
                    )
                }

                authService.loginWithIdentifier(
                    homeserver = homeserverResponseAddress,
                    identifier = null,
                    password = null,
                    token = token
                ).let {
                    when {
                        it.isSuccess -> {
                            _matrixAuthResponse.value = it.success?.data
                            initUserObject()
                        }
                        it.error?.code == MatrixUtils.ErrorCode.FORBIDDEN -> _loginResult.emit(LoginResultType.INVALID_CREDENTIAL)
                        else -> _loginResult.emit(LoginResultType.FAILURE)
                    }
                    if (!it.isSuccess) {
                        selectHomeServer(savedHomeserver ?: "")
                    }
                }
            }
            _isLoading.value = false
        }
    }

    /** Authenticates user with a token */
    private suspend fun initUserObject() {
        _matrixAuthResponse.value?.accessToken?.let { accessToken ->
            updateClientSettings()
            withContext(Dispatchers.IO) {
                settings.putString(KEY_CLIENT_STATUS, ClientStatus.REGISTERED.name)
                SharedLogger.logger.debug { "User initialized as: ${sharedDataManager.currentUser.value}" }
            }
            _loginResult.emit(
                if (currentUser.value != null) LoginResultType.SUCCESS else LoginResultType.FAILURE
            )
        }
    }
}

data class MatrixProgress(
    val username: String?,
    val email: String?,
    val secret: String?,
    val password: String,
    val recaptcha: String? = null,
    val sid: String? = null,
    val agreements: List<String>? = null,
    val response: MatrixAuthenticationPlan?,
    val index: Int = 0,
    val retryAfter: Int? = null
)
