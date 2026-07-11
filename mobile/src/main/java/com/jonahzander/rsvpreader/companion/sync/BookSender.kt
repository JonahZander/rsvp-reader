package com.jonahzander.rsvpreader.companion.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.jonahzander.rsvpreader.core.model.Book
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import com.jonahzander.rsvpreader.core.sync.ReaderSettings
import kotlinx.coroutines.tasks.await

/**
 * Pushes books and settings into the Wearable Data Layer. Delivery to the
 * watch is handled by Play Services, including when the watch is offline.
 */
class BookSender(context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    suspend fun sendBook(book: Book) {
        val request = PutDataMapRequest.create(DataLayerContract.bookPath(book.id)).apply {
            // The timestamp is never read; it forces the DataMap to differ so
            // re-sends of an unchanged book still trigger a change event.
            dataMap.putLong(DataLayerContract.KEY_UPDATED_AT, System.currentTimeMillis())
            dataMap.putAsset(DataLayerContract.ASSET_BOOK, Asset.createFromBytes(DataLayerContract.encodeBook(book)))
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    suspend fun deleteBook(id: String) {
        for (path in listOf(DataLayerContract.bookPath(id), DataLayerContract.ackPath(id))) {
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .authority("*")
                .path(path)
                .build()
            dataClient.deleteDataItems(uri).await()
        }
    }

    suspend fun sendSettings(settings: ReaderSettings) {
        val request = PutDataRequest.create(DataLayerContract.SETTINGS_PATH).apply {
            data = DataLayerContract.encodeSettings(settings)
        }.setUrgent()
        dataClient.putDataItem(request).await()
    }

    /** True if a watch is currently reachable (sync works regardless). */
    suspend fun isWatchConnected(): Boolean =
        runCatching { nodeClient.connectedNodes.await().isNotEmpty() }.getOrDefault(false)
}
