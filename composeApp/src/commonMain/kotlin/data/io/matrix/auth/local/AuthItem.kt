package data.io.matrix.auth.local

import augmy.interactive.shared.utils.DateUtils
import base.utils.orZero
import data.io.social.UserConfiguration
import database.factory.SecretByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AuthItem(
    val homeserver: String? = null,

    val loginType: String? = null,

    /** the medium of login */
    val medium: String? = null,

    val expiresAtMsEpoch: Long? = null,

    val accessToken: String? = null,
    val refreshToken: String? = null,
    val address: String? = null,
    val password: String? = null,
    val token: String?,

    @Transient
    val userId: String? = null,
    @Transient
    val databasePassword: SecretByteArray? = null,
    @Transient
    val pickleKey: String? = null,

    val avatarUrl: String? = null,

    // init-app info
    val displayName: String? = null,
    val publicId: String? = null,
    val configuration: UserConfiguration? = null
) {

    val isExpired: Boolean
        get() = accessToken == null || expiresAtMsEpoch?.minus(1_000L).orZero() < DateUtils.now.toEpochMilliseconds()

    val isFullyValid: Boolean
        get() = accessToken != null
                && homeserver != null
                && userId != null

    val canLogin: Boolean
        get() = token != null
                || ((userId != null || address != null) && password != null)
}
