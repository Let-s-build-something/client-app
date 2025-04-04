package ui.login

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.firebase_web_client_id
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import data.io.identity_platform.IdentityUserResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import java.util.UUID

/** module providing platform-specific sign in options */
actual fun signInServiceModule() = module {
    val context: Context = getKoin().get()

    single<UserOperationService> { UserOperationService(context) }
}

actual class UserOperationService(
    private val context: Context
) {

    companion object {
        
        private const val TAG = "UserOperationService"
    }

    private var lastNonce: String? = null

    actual suspend fun requestGoogleSignIn(filterAuthorizedAccounts: Boolean): LoginResultType {
        val pendingResult = checkForPendingResult()
        if(pendingResult != null) return pendingResult
        val nonce = UUID.randomUUID().toString()
        lastNonce = nonce

        var result: LoginResultType? = null

        val credentialManager = CredentialManager.create(context)

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption
            .Builder()
            .setFilterByAuthorizedAccounts(filterAuthorizedAccounts)
            .setServerClientId(getString(Res.string.firebase_web_client_id))
            .setNonce(nonce)
            .build()

        val passwordCredential = GetPasswordOption()

        val request: GetCredentialRequest = GetCredentialRequest
            .Builder()
            .addCredentialOption(googleIdOption)
            .addCredentialOption(passwordCredential)
            .build()

        try {
            val res = credentialManager.getCredential(
                request = request,
                context = context
            )
            result = handleGoogleSignIn(res, nonce = nonce)
        } catch (e: NoCredentialException) {
            Log.e(TAG, "$e")
            result = if(filterAuthorizedAccounts) {
                requestGoogleSignIn(filterAuthorizedAccounts = false)
            }else LoginResultType.NO_GOOGLE_CREDENTIALS
        } catch (e: GetCredentialCancellationException) {
            Log.e(TAG, "$e")
            /*if(filterAuthorizedAccounts) {
                result = requestGoogleSignIn(filterAuthorizedAccounts = false)
            }*/
            LoginResultType.CANCELLED
        } catch (e: GetCredentialException) {
            Log.e(TAG, "${e.errorMessage}")
        }

        return result ?: LoginResultType.FAILURE
    }

    actual suspend fun requestAppleSignIn(): LoginResultType {
        return LoginResultType.FAILURE
    }

    actual suspend fun signUpWithPassword(
        email: String,
        password: String,
        deleteRightAfter: Boolean
    ): IdentityUserResponse? = null


    /**
     * Checks for any currently ongoing process and only returns [LoginResultType] in case the pending exists,
     * thus, it should be uninterrupted
     */
    private suspend fun checkForPendingResult(): LoginResultType? {
        val pending = Firebase.auth.pendingAuthResult

        return if (pending != null) {
            if(pending.await().user != null) LoginResultType.SUCCESS else LoginResultType.FAILURE
        } else null
    }

    /** Handles successful user sign in with any type of credential */
    private suspend fun handleGoogleSignIn(
        result: GetCredentialResponse,
        nonce: String
    ): LoginResultType {
        if(lastNonce != nonce) return LoginResultType.FAILURE
        // Handle the successfully returned credential.
        val credential = result.credential

        Log.d(TAG, "Received credential: $credential")
        return withContext(Dispatchers.IO) {
            when (credential) {
                is PublicKeyCredential -> {
                    // Share responseJson such as a GetCredentialResponse on your server to
                    // validate and authenticate
                    //val responseJson = credential.authenticationResponseJson
                    LoginResultType.FAILURE
                }
                is PasswordCredential -> {
                    // Send ID and password to your server to validate and authenticate.
                    val id = credential.id
                    val password = credential.password

                    val request = when {
                        android.util.Patterns.EMAIL_ADDRESS.matcher(id).matches() -> {
                            Firebase.auth.signInWithEmailAndPassword(id, password).await()
                        }
                        android.util.Patterns.PHONE.matcher(id).matches() -> {
                            Firebase.auth.signInWithCredential(
                                PhoneAuthProvider.getCredential(id, password)
                            ).await()
                        }
                        else -> null
                    }
                    if(request?.user != null) LoginResultType.SUCCESS else LoginResultType.FAILURE
                }
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            // Use googleIdTokenCredential and extract id to validate and
                            // authenticate on your server.
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val authCredential = GoogleAuthProvider.getCredential(
                                googleIdTokenCredential.idToken,
                                null
                            )
                            val request = Firebase.auth.signInWithCredential(authCredential).await()
                            if(request.user != null) LoginResultType.SUCCESS else LoginResultType.FAILURE
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e(
                                TAG,
                                "Received an invalid google id token response: $e"
                            )
                            LoginResultType.FAILURE
                        }
                    } else {
                        // Catch any unrecognized custom credential type here.
                        Log.e(TAG, "Unexpected type of credential")
                        LoginResultType.FAILURE
                    }
                }
                else -> {
                    // Catch any unrecognized credential type here.
                    Log.e(TAG, "Unexpected type of credential")
                    LoginResultType.FAILURE
                }
            }
        }
    }
}