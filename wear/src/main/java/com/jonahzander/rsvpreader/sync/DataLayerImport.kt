package com.jonahzander.rsvpreader.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import com.jonahzander.rsvpreader.data.BookRepository
import com.jonahzander.rsvpreader.data.ProgressStore
import kotlinx.coroutines.tasks.await

/**
 * One-shot import of everything already in the Data Layer — covers books that
 * were sent before the watch app was installed or while it wasn't running.
 */
object DataLayerImport {

    suspend fun importAll(context: Context, books: BookRepository, progress: ProgressStore) {
        val dataClient = Wearable.getDataClient(context)
        runCatching {
            val items = dataClient.dataItems.await()
            try {
                for (item in items) {
                    val path = item.uri.path ?: continue
                    when {
                        path == DataLayerContract.SETTINGS_PATH ->
                            item.data?.let { applySettingsBytes(it, progress) }

                        path.startsWith(DataLayerContract.BOOKS_PATH_PREFIX) -> {
                            val id = DataLayerContract.bookIdFromPath(path) ?: continue
                            val asset = DataMapItem.fromDataItem(item.freeze())
                                .dataMap.getAsset(DataLayerContract.ASSET_BOOK) ?: continue
                            importBookAsset(dataClient, id, asset, books)
                        }
                    }
                }
            } finally {
                items.release()
            }
        }
    }
}

/** Fetches, decodes, stores, and acks one /books asset; failures skip just that book. */
internal suspend fun importBookAsset(
    dataClient: DataClient,
    id: String,
    asset: Asset,
    books: BookRepository,
) {
    runCatching {
        val fd = dataClient.getFdForAsset(asset).await()
        val book = fd.inputStream.use { DataLayerContract.decodeBook(it.readBytes()) }
        books.saveSynced(book)
        sendAck(dataClient, id)
    }
}

/** Tells the phone the book landed: writes a DataItem at /acks/<id>. Best-effort. */
internal suspend fun sendAck(dataClient: DataClient, bookId: String) {
    runCatching {
        val request = PutDataRequest.create(DataLayerContract.ackPath(bookId))
            .apply { data = System.currentTimeMillis().toString().encodeToByteArray() }
            .setUrgent()
        dataClient.putDataItem(request).await()
    }
}

/**
 * Removes this watch's ack for a deleted book: an ack written after the phone
 * already swept /acks would otherwise linger and fake delivery of a future
 * re-import of the same content.
 */
internal suspend fun deleteAck(dataClient: DataClient, bookId: String) {
    runCatching {
        val uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority("*")
            .path(DataLayerContract.ackPath(bookId))
            .build()
        dataClient.deleteDataItems(uri).await()
    }
}

/** Decodes settings bytes and applies them; undecodable payloads are ignored. */
internal suspend fun applySettingsBytes(bytes: ByteArray, progress: ProgressStore) {
    runCatching { DataLayerContract.decodeSettings(bytes) }
        .onSuccess { progress.applySettings(it) }
}
