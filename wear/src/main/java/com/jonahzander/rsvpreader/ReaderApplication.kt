package com.jonahzander.rsvpreader

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import com.jonahzander.rsvpreader.data.BookRepository
import com.jonahzander.rsvpreader.data.ProgressStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val android.content.Context.progressDataStore by preferencesDataStore(name = "progress")

class ReaderApplication : Application() {
    val bookRepository: BookRepository by lazy { BookRepository(this) }
    val progressStore: ProgressStore by lazy { ProgressStore(progressDataStore) }

    /**
     * Outlives any screen; used for writes that must survive ViewModel
     * teardown. Single-threaded so back-to-back saves (a seek pauses then
     * re-saves; ON_STOP and onCleared both save) commit in launch order —
     * on a parallel dispatcher the older write could land last.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
}
