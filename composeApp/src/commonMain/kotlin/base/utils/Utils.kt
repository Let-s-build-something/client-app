package base.utils

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.Clipboard
import app.cash.paging.compose.LazyPagingItems
import augmy.interactive.shared.ui.base.LocalDeviceType
import coil3.toUri
import io.github.vinceglb.filekit.PlatformFile

/** Returns item at a specific index and handles indexOutOfBounds exception */
fun <T: Any> LazyPagingItems<T>.getOrNull(index: Int): T? {
    return if(index >= this.itemCount || index < 0) null else this[index]
}

/** Color derived from a user tag */
fun tagToColor(tag: String?) = if(tag != null) Color(("ff$tag").toLong(16)) else null

/** Converts color to 6 hexadecimal numbers representing it without transparency */
fun Color.asSimpleString() = this.value.toString(16).substring(2, 8)

inline fun <reified T : Number> T?.orZero(): T {
    return this ?: when (T::class) {
        Int::class -> 0 as T
        Long::class -> 0L as T
        Double::class -> 0.0 as T
        Float::class -> 0f as T
        Short::class -> 0.toShort() as T
        Byte::class -> 0.toByte() as T
        else -> throw IllegalArgumentException("Unsupported numeric type: ${T::class}")
    }
}

inline fun <reified T : Number> T?.orDefault(value: T): T {
    return this ?: value
}

/** Returns the extension of an url */
fun getUrlExtension(url: String): String = (url.toUri().path ?: url).substringAfterLast(".").lowercase()

/** Type of a media file */
enum class MediaType {
    IMAGE,
    VIDEO,
    GIF,
    AUDIO,
    TEXT,
    PDF,
    PRESENTATION,
    UNKNOWN;

    companion object {
        /** Returns a media type of a file */
        fun fromMimeType(mimeType: String): MediaType {
            return when {
                mimeType.lowercase().contains("gif") -> GIF
                mimeType.lowercase().contains("image") -> IMAGE
                mimeType.lowercase().contains("video") -> VIDEO
                mimeType.lowercase().contains("audio") -> AUDIO
                mimeType.lowercase().contains("text") -> TEXT
                mimeType.lowercase().contains("pdf") -> PDF
                mimeType.lowercase().contains("powerpoint")
                        || mimeType.lowercase().contains("presentation")
                        || mimeType.lowercase().contains("slideshow") -> PRESENTATION
                else -> UNKNOWN
            }
        }
    }

    val isVisual: Boolean
        get() = this == IMAGE || this == VIDEO || this == GIF
}

val maxMultiLineHeight: Int
    @Composable
    get() = when(LocalDeviceType.current) {
        WindowWidthSizeClass.Compact -> 5
        WindowWidthSizeClass.Medium -> 8
        else -> 15
    }

expect suspend fun Clipboard.withPlainText(content: String)

/** Returns a bitmap from a given file */
expect suspend fun getBitmapFromFile(file: PlatformFile): ImageBitmap?

/**
 * Converts any value to SHA-256 hash
 */
expect fun Any.toSha256(): String

/** Retrieves the current device name */
expect fun deviceName(): String?
