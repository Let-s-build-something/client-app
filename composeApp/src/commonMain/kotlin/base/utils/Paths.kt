package base.utils

import io.github.vinceglb.filekit.PlatformFile
import net.folivo.trixnity.core.model.UserId
import okio.ByteString.Companion.toByteString
import okio.Path
import okio.Sink
import org.koin.core.module.Module
import kotlin.jvm.JvmInline

@JvmInline
value class RootPath(val path: Path) {
    fun forAccount(userId: UserId) = path.resolve(userId.asFilesystemSafeString())
    fun forAccountDatabase(userId: UserId) = forAccount(userId).resolve("database")
    //fun forAccountMedia(userId: UserId) = forAccount(userId).resolve("media")
    private fun UserId.asFilesystemSafeString() = full.encodeToByteArray().toByteString().sha256().base64Url()
}

expect fun platformPathsModule(): Module

expect fun getDownloadsPath(): String

expect fun PlatformFile.openSinkFromUri(): Sink
