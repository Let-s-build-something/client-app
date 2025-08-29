package koin

import augmy.interactive.com.BuildKonfig
import base.utils.NetworkSpeed
import base.utils.speedInMbps
import data.shared.SharedModel
import data.shared.auth.AuthService
import data.shared.sync.DataSyncService.Companion.SYNC_INTERVAL
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.MatrixServerException
import org.koin.mp.KoinPlatform
import ui.dev.DeveloperConsoleModel
import utils.DeveloperUtils
import utils.SharedLogger
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal expect fun httpClient(): HttpClient

/** Creates a new instance of http client with interceptor and authentication */
@OptIn(ExperimentalUuidApi::class)
internal fun httpClientFactory(
    sharedModel: SharedModel,
    developerViewModel: () -> DeveloperConsoleModel?,
    authService: AuthService,
    json: Json
): HttpClient {
    var forceRefreshCountdown = 3

    return httpClient().config {
        defaultRequest {
            contentType(ContentType.Application.Json)

            host = developerViewModel()?.hostOverride ?: BuildKonfig.HttpsHostName
            url {
                protocol = URLProtocol.HTTPS
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = SYNC_INTERVAL + 10_000
            socketTimeoutMillis = SYNC_INTERVAL + 10_000
            connectTimeoutMillis = 5_000
        }
        install(HttpSend)
        install(ContentNegotiation) {
            json(json)
        }
        httpClientConfig(sharedModel = sharedModel)
        install(HttpSend)
    }.apply {
        plugin(HttpSend).intercept { request ->
            val isMatrix = request.url.toString().contains(
                sharedModel.currentUser.value?.matrixHomeserver?.replace(
                    """:\d+""".toRegex(),
                    ""
                ) ?: ".;'][."
            )
            if(isMatrix) {
                sharedModel.currentUser.value?.accessToken?.let { accessToken ->
                    request.headers[HttpHeaders.Authorization] = "Bearer $accessToken"
                }
            }else if(request.url.host == (developerViewModel()?.hostOverride ?: BuildKonfig.HttpsHostName)) {
                request.headers.append(HttpHeaders.Authorization, "Bearer ${BuildKonfig.BearerToken}")
                sharedModel.currentUser.value?.accessToken?.let { accessToken ->
                    request.headers[AccessToken] = accessToken
                }
            }

            request.headers.append(HttpHeaders.XRequestId, Uuid.random().toString())

            developerViewModel()?.appendHttpLog(
                DeveloperUtils.processRequest(request)
            )
            val call = execute(request)

            // retry for 401 response
            if (call.response.status == EXPIRED_TOKEN_CODE
                && isMatrix
                && request.url.toString().contains("/_matrix/")
                && !request.url.encodedPath.contains("/refresh")
                && forceRefreshCountdown-- > 0
            ) {
                authService.setupAutoLogin(forceRefresh = false)
                if(sharedModel.currentUser.value?.accessToken != null) {
                    request.headers[HttpHeaders.Authorization] = "Bearer ${sharedModel.currentUser.value?.accessToken}"
                    return@intercept execute(request)
                }
            }else forceRefreshCountdown = 3

            call
        }
    }
}

fun HttpClientConfig<*>.httpClientConfig(sharedModel: SharedModel) {
    val developerViewModel = KoinPlatform.getKoin().getOrNull<DeveloperConsoleModel>()

    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.BODY

        sanitizeHeader { header ->
            header == HttpHeaders.Authorization
                    || header == IdToken
                    || header == AccessToken
        }
    }
    install(HttpRequestRetry) {
        retryIf { httpRequest, httpResponse ->
            (httpResponse.status == HttpStatusCode.TooManyRequests)
                .also { if (it) SharedLogger.logger.warn { "rate limit exceeded for ${httpRequest.method} ${httpRequest.url}" } }
        }
        retryOnExceptionIf { _, throwable ->
            (throwable is MatrixServerException && throwable.statusCode == HttpStatusCode.TooManyRequests)
                .also {
                    if (it) {
                        SharedLogger.logger.warn { (if(SharedLogger.logger.isDebugEnabled) "${throwable.message} - ${throwable.cause}" else "") + ": rate limit exceeded" }
                    }
                }
        }
        exponentialDelay(maxDelayMs = 30_000, respectRetryAfterHeader = true)
    }
    ResponseObserver { response ->
        developerViewModel?.appendHttpLog(
            DeveloperUtils.processResponse(response)
        )

        // sync has a very long timeout which would throw off this calculation
        val speedMbps = response.speedInMbps().roundToInt()
        sharedModel.updateNetworkConnectivity(
            networkSpeed = if (!response.request.url.toString().contains("/sync")) {
                when {
                    speedMbps <= 1.0 -> NetworkSpeed.VerySlow
                    speedMbps <= 2.0 -> NetworkSpeed.Slow
                    speedMbps <= 5.0 -> NetworkSpeed.Moderate
                    speedMbps <= 10.0 -> NetworkSpeed.Good
                    else -> NetworkSpeed.Fast
                }.takeIf { speedMbps != 0 }
            } else null,
            isNetworkAvailable = response.status.value < 500
        )
        if (!response.status.isSuccess()) {
            SharedLogger.logger.warn {
                "Http call failed: $response"
            }
        }
    }
    HttpResponseValidator {
        handleResponseException { cause, _ ->
            cause.printStackTrace()
            when {
                cause is ConnectTimeoutException || cause is SocketTimeoutException || isConnectionException(cause) -> {
                    sharedModel.updateNetworkConnectivity(isNetworkAvailable = false)
                }
            }
        }
    }
    defaultRequest {
        headers[HttpHeaders.UserAgent] = "Augmy"
        if(headers[HttpHeaders.Authorization] == null) {
            when {
                url.toString().contains(sharedModel.currentUser.value?.matrixHomeserver ?: ".;'][.") -> {
                    sharedModel.currentUser.value?.accessToken?.let { accessToken ->
                        headers[HttpHeaders.Authorization] = "Bearer $accessToken"
                    }
                }
            }
        }
    }
}

expect fun isConnectionException(cause: Throwable): Boolean

/** Authorization type header with Firebase identification token */
val IdToken: String
    get() = "Id-Token"

/** Authorization type header with Firebase identification token */
val AccessToken: String
    get() = "Access-Token"

/** http response code indicating expired token */
internal val EXPIRED_TOKEN_CODE = HttpStatusCode.Unauthorized

internal const val HttpDomain = "https://augmy.org"