package koin

import augmy.interactive.com.BuildKonfig
import augmy.interactive.shared.ui.base.PlatformType
import augmy.interactive.shared.ui.base.currentPlatform
import coil3.annotation.ExperimentalCoilApi
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import data.io.matrix.room.event.serialization.DefaultLocalSerializerMappings
import data.io.matrix.room.event.serialization.createEventSerializersModule
import data.shared.SharedDataManager
import data.shared.SharedModel
import data.shared.SharedRepository
import data.shared.appServiceModule
import data.shared.auth.AuthService
import data.shared.auth.authModule
import data.shared.auth.matrixRepositoryModule
import data.shared.sync.dataSyncModule
import database.databaseModule
import database.file.FileAccess
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.overwriteWith
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.conversation.components.audio.mediaProcessorModule
import ui.dev.DeveloperConsoleModel
import ui.dev.developerConsoleModule
import ui.home.homeModule
import ui.login.LoginDataManager

/** Common module for the whole application */
@OptIn(ExperimentalCoilApi::class, ExperimentalSerializationApi::class)
internal val commonModule = module {
    if(currentPlatform != PlatformType.Jvm) includes(settingsModule)
    single { FileAccess() }
    single { SharedDataManager() }
    single { LoginDataManager() }
    single<EventContentSerializerMappings> { DefaultEventContentSerializerMappings }
    single<Json> {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            useArrayPolymorphism = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
            allowSpecialFloatingPointValues = true
            allowStructuredMapKeys = true
            prettyPrint = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            serializersModule = createEventSerializersModule(
                DefaultEventContentSerializerMappings
            ).overwriteWith(DefaultLocalSerializerMappings)
        }
    }

    includes(databaseModule)
    includes(dataSyncModule)
    includes(matrixRepositoryModule)
    includes(authModule)
    viewModelOf(::SharedModel)
    includes(homeModule)
    includes(appServiceModule)
    includes(mediaProcessorModule)

    single {
        NetworkFetcher.Factory(
            networkClient = { get<HttpClient>().asNetworkClient() }
        )
    }

    if(BuildKonfig.isDevelopment) this@module.includes(developerConsoleModule)

    single<HttpClient> {
        httpClientFactory(
            sharedModel = get<SharedModel>(),
            developerViewModel = if(BuildKonfig.isDevelopment) get<DeveloperConsoleModel>() else null,
            json = get<Json>(),
            authService = get<AuthService>()
        )
    }
    single<HttpClientEngine> { get<HttpClient>().engine }

    factory { SharedRepository() }
}

expect val settings: AppSettings

expect val secureSettings: SecureAppSettings
