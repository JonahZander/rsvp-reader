package com.jonahzander.rsvpreader.sync

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.jonahzander.rsvpreader.data.BookRepository
import com.jonahzander.rsvpreader.data.ProgressStore
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
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
                            item.data?.let { bytes ->
                                runCatching { DataLayerContract.decodeSettings(bytes) }
                                    .onSuccess { progress.applySettings(it) }
                            }

                        path.startsWith(DataLayerContract.BOOKS_PATH_PREFIX) -> {
                            val asset = DataMapItem.fromDataItem(item.freeze())
                                .dataMap.getAsset(DataLayerContract.ASSET_BOOK) ?: continue
                            runCatching {
                                val fd = dataClient.getFdForAsset(asset).await()
                                val book = fd.inputStream.use { DataLayerContract.decodeBook(it.readBytes()) }
                                books.saveSynced(book)
                            }
                            runCatching {
                                val id = DataLayerContract.bookIdFromPath(path)
                                if (id != null) {
                                    val ack = PutDataRequest.create(DataLayerContract.ackPath(id))
                                        .apply { data = System.currentTimeMillis().toString().encodeToByteArray() }
                                        .setUrgent()
                                    dataClient.putDataItem(ack).await()
                                }
                            }
                        }
                    }
                }
            } finally {
                items.release()
            }
        }
    }
}
