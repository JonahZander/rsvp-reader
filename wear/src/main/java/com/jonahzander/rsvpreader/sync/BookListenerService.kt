package com.jonahzander.rsvpreader.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.jonahzander.rsvpreader.ReaderApplication
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import kotlinx.coroutines.runBlocking

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
            val dataClient = Wearable.getDataClient(this@BookListenerService)
            settingsBytes?.let { applySettingsBytes(it, app.progressStore) }
            for ((id, asset) in toImport) {
                importBookAsset(dataClient, id, asset, app.bookRepository)
            }
            for (id in toDelete) {
                runCatching {
                    app.bookRepository.deleteSynced(id)
                    deleteAck(dataClient, id)
                }
            }
        }
    }
}
