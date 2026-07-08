package com.jonahzander.rsvpreader.core.sync

import com.jonahzander.rsvpreader.core.model.Book
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared contract between the phone and watch apps for the Wearable Data Layer.
 *
 * Books live at /books/<id> as a DataItem whose DataMap carries the metadata
 * plus a single Asset ([ASSET_BOOK]) containing the whole book as JSON.
 * Settings live at /settings as JSON bytes in the DataItem payload.
 */
object DataLayerContract {
    const val BOOKS_PATH_PREFIX = "/books/"
    const val SETTINGS_PATH = "/settings"

    const val ASSET_BOOK = "book"
    const val KEY_TITLE = "title"
    const val KEY_AUTHOR = "author"
    const val KEY_UPDATED_AT = "updatedAt"

    val json = Json { ignoreUnknownKeys = true }

    fun bookPath(id: String): String = BOOKS_PATH_PREFIX + id

    fun bookIdFromPath(path: String): String? =
        path.takeIf { it.startsWith(BOOKS_PATH_PREFIX) }
            ?.removePrefix(BOOKS_PATH_PREFIX)
            ?.takeIf { it.isNotBlank() }

    fun encodeBook(book: Book): ByteArray =
        json.encodeToString(Book.serializer(), book).encodeToByteArray()

    fun decodeBook(bytes: ByteArray): Book =
        json.decodeFromString(Book.serializer(), bytes.decodeToString())

    fun encodeSettings(settings: ReaderSettings): ByteArray =
        json.encodeToString(ReaderSettings.serializer(), settings).encodeToByteArray()
    fun decodeSettings(bytes: ByteArray): ReaderSettings =
        json.decodeFromString(ReaderSettings.serializer(), bytes.decodeToString())
}

@Serializable
data class ReaderSettings(
    val wpm: Int = 300,
    val font: String = "atkinson",
)
