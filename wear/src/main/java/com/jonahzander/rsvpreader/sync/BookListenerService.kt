package com.jonahzander.rsvpreader.sync

import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jonahzander.rsvpreader.ReaderApplication
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Receives books and settings pushed from the phone companion app.
 */
class BookListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val app = application as ReaderApplication
        val toImport = mutableListOf<Pair<String, com.google.android.gms.wearable.Asset>>()
        val toDelete = mutableListOf<String>()
        var settingsBytes: ByteArray? = null

        for (event in events) {
            val path = event.dataItem.uri.path ?: continue
            when {
                path == DataLayerContract.SETTINGS_PATH && event.type == DataEvent.TYPE_CHANGED ->
                    settingsBytes = event.dataItem.data

                path.startsWith(DataLayerContract.BOOKS_PATH_PREFIX) -> {
                    val id = DataLayerContract.bookIdFromPath(path) ?: continue
                    if (event.type == DataEvent.TYPE_CHANGED) {
                        val asset = DataMapItem.fromDataItem(event.dataItem)
                            .dataMap.getAsset(DataLayerContract.ASSET_BOOK) ?: continue
                        toImport += id to asset
                    } else {
                        toDelete += id
                    }
                }
            }
        }

        // WearableListenerService callbacks run on a background thread; blocking is fine here.
        runBlocking {
            settingsBytes?.let { bytes ->
                runCatching { DataLayerContract.decodeSettings(bytes) }
                    .onSuccess { app.progressStore.applySettings(it) }
            }
            val dataClient = Wearable.getDataClient(this@BookListenerService)
            for ((id, asset) in toImport) {
                runCatching {
                    val fd = dataClient.getFdForAsset(asset).await()
                    val book = fd.inputStream.use { DataLayerContract.decodeBook(it.readBytes()) }
                    app.bookRepository.saveSynced(book)
                    sendAck(dataClient, id)
                }
            }
            for (id in toDelete) {
                app.bookRepository.deleteSynced(id)
            }
        }
    }

    private suspend fun sendAck(dataClient: DataClient, bookId: String) {
        runCatching {
            val request = PutDataRequest.create(DataLayerContract.ackPath(bookId))
                .apply { data = System.currentTimeMillis().toString().encodeToByteArray() }
                .setUrgent()
            dataClient.putDataItem(request).await()
        }
    }
}
