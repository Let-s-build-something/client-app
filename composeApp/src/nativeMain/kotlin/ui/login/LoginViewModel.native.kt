package ui.login

import cocoapods.GoogleSignIn.GIDSignIn
import data.io.identity_platform.IdentityMessageType
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import dev.gitlive.firebase.auth.auth
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.kotlincrypto.hash.sha2.SHA256
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** module providing platform-specific sign in options */
actual fun signInServiceModule() = module {
    factoryOf(::UserOperationService)
    single<UserOperationService> { UserOperationService() }
}

actual class UserOperationService {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun requestGoogleSignIn(filterAuthorizedAccounts: Boolean): LoginResultType {
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        return if (rootViewController == null) {
            LoginResultType.NO_WINDOW
        } else {
            val signInResult = requestCredentials(rootViewController)

            signInResult.first?.user?.let { user ->
                val idToken = user.idToken?.tokenString
                val accessToken = user.accessToken.tokenString

                if(Firebase.auth.signInWithCredential(
                        GoogleAuthProvider.credential(idToken, accessToken)
                    ).user != null
                ) {
                    LoginResultType.SUCCESS
                } else {
                    LoginResultType.FAILURE
                }
            } ?: LoginResultType.FAILURE
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun requestCredentials(rootViewController: UIViewController) = suspendCoroutine { continuation ->
        GIDSignIn.sharedInstance.signInWithPresentingViewController(rootViewController) { result, error ->
            continuation.resume(result to error)
        }
    }

    private var currentNonce: String? = null

    private fun sha256(input: String): String {
        val inputData = input.toByteArray(Charsets.UTF_8)
        val hashedData = SHA256().digest(inputData)
        return hashedData.joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    actual suspend fun requestAppleSignIn(): LoginResultType {
        val deferredJob = CompletableDeferred<AppleIdAuthShell?>(Job())
        currentNonce = Uuid.random().toString()

        val appleIDProvider = ASAuthorizationAppleIDProvider()
        val request = appleIDProvider.createRequest().apply {
            requestedScopes = listOf(
                ASAuthorizationScopeFullName,
                ASAuthorizationScopeEmail
            )
            currentNonce?.let {
                nonce = sha256(it)
            }
        }

        return withContext(Dispatchers.Main) {
            ASAuthorizationController(authorizationRequests = listOf(request)).run {
                val authorizationDelegate = object: NSObject(),
                    ASAuthorizationControllerDelegateProtocol,
                    ASAuthorizationControllerPresentationContextProvidingProtocol {
                    override fun authorizationController(
                        controller: ASAuthorizationController,
                        didCompleteWithAuthorization: ASAuthorization
                    ) {
                        deferredJob.complete(
                            AppleIdAuthShell(
                                didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
                            )
                        )
                    }

                    override fun authorizationController(
                        controller: ASAuthorizationController,
                        didCompleteWithError: NSError
                    ) {
                        deferredJob.complete(AppleIdAuthShell(error = didCompleteWithError))
                    }

                    override fun presentationAnchorForAuthorizationController(
                        controller: ASAuthorizationController
                    ): UIWindow? = UIApplication.sharedApplication.keyWindow
                }
                delegate = authorizationDelegate
                setPresentationContextProvider(authorizationDelegate)
                performRequests()
            }
            deferredJob.await().let { response ->
                return@withContext if(response?.credentials == null) {
                    println("Apple Sign In Error: ${response?.error}")
                    when(response?.error?.code) {
                        1001L -> LoginResultType.CANCELLED
                        else -> LoginResultType.FAILURE
                    }
                }else {
                    try {
                        Firebase.auth.signInWithCredential(
                            OAuthProvider.credential(
                                providerId = "apple.com",
                                rawNonce = currentNonce,
                                idToken = response.credentials.identityToken?.string()
                            )
                        ).user.let {
                            currentNonce = null
                            return@withContext if(it != null) LoginResultType.SUCCESS else LoginResultType.FAILURE
                        }
                    }catch (e: FirebaseAuthException) {
                        println(e.printStackTrace())
                        currentNonce = null
                        return@withContext LoginResultType.AUTH_SECURITY
                    }
                }
            }
        }
    }

    actual suspend fun signUpWithPassword(
        email: String,
        password: String,
        deleteRightAfter: Boolean
    ): IdentityMessageType? = null

    @OptIn(BetaInteropApi::class)
    private fun NSData.string(): String? = NSString.create(
        data = this,
        encoding = NSUTF8StringEncoding
    )?.toString()
}

/** result of the Sign in with Apple Id */
private data class AppleIdAuthShell(
    /** successfully gained credentials */
    val credentials: ASAuthorizationAppleIDCredential? = null,
    /** returned error during the process */
    val error: NSError? = null
)