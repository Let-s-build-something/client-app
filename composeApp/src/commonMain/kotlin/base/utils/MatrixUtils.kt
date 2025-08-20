package base.utils

import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

object MatrixUtils {
    private const val LOGIN = "m.login"

    const val LOGIN_DUMMY = "$LOGIN.dummy"
    const val LOGIN_SSO = "$LOGIN.sso"
    const val LOGIN_AUGMY_SSO = "$LOGIN.augmy.sso"
    const val LOGIN_EMAIL_IDENTITY = "$LOGIN.email.identity"
    const val LOGIN_RECAPTCHA = "$LOGIN.recaptcha"
    const val LOGIN_TERMS = "$LOGIN.terms"
    const val LOGIN_TOKEN = "$LOGIN.token"
    const val LOGIN_PASSWORD = "$LOGIN.password"

    const val MATRIX_HOME_SERVER = "matrix.org"
    const val AUGMY_HOMESERVER_IDENTIFIER = "augmy.org"
    const val AUGMY_HOME_SERVER_ADDRESS = "homeserver.augmy.org"
    const val AUGMY_INTERNAL_ROOM_ID = "!gAnKoCsLRBAAppnYpK:augmy.org"

    object ErrorCode {
        const val FORBIDDEN = "M_FORBIDDEN"
        const val USER_IN_USE = "M_USER_IN_USE"
        const val UNKNOWN = "M_UNKNOWN"
        const val UNKNOWN_TOKEN = "M_UNKNOWN_TOKEN"
        const val CREDENTIALS_IN_USE = "M_THREEPID_IN_USE"
        const val CREDENTIALS_DENIED = "M_THREEPID_DENIED"
    }

    object Id {
        const val THIRD_PARTY = "m.id.thirdparty"
        const val USER = "m.id.user"
    }

    object Brand {
        const val GOOGLE = "google"
        const val GOOGLE_OIDC = "oidc-google"
        const val APPLE = "apple"
        const val APPLE_OIDC = "oidc-apple"
    }

    object Medium {
        const val EMAIL = "email"
    }

    object Media {
        const val MATRIX_REPOSITORY_PREFIX = "mxc://"
    }

    val defaultRoomPowerLevels: InitialStateEvent<PowerLevelsEventContent>
        get() = InitialStateEvent(
            content = PowerLevelsEventContent(
                usersDefault = 0L,
                invite = 10L,
                eventsDefault = 0L,
                stateDefault = 50L,
                ban = 50L,
                kick = 50L,
                redact = 100L
            ),
            stateKey = ""
        )
}