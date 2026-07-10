package com.jonahzander.rsvpreader.companion.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.jonahzander.rsvpreader.companion.CompanionApplication
import com.jonahzander.rsvpreader.core.sync.DataLayerContract
import kotlinx.coroutines.runBlocking

/** Receives delivery acknowledgments written by the watch at /acks/<bookId>. */
class AckListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val repository = (application as CompanionApplication).repository
        val acked = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            event.dataItem.uri.path?.let { DataLayerContract.ackIdFromPath(it) }
        }
        if (acked.isEmpty()) return
        runBlocking { acked.forEach { repository.markAcked(it) } }
    }
}
