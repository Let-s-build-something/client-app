package koin

import android.os.NetworkOnMainThreadException
import io.ktor.client.HttpClient
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

internal actual fun httpClient(): HttpClient = HttpClient()
actual fun isConnectionException(cause: Throwable): Boolean {
    return when (cause) {
        is SSLHandshakeException -> true
        is SocketTimeoutException -> true
        is ConnectException -> true
        is NoRouteToHostException -> true
        is NetworkOnMainThreadException -> true  // Android-specific
        is android.accounts.NetworkErrorException -> true  // Android account sync errors
        else -> false
    }
}