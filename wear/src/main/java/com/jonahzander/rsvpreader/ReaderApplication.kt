package com.jonahzander.rsvpreader

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import com.jonahzander.rsvpreader.data.BookRepository
import com.jonahzander.rsvpreader.data.ProgressStore

private val android.content.Context.progressDataStore by preferencesDataStore(name = "progress")

class ReaderApplication : Application() {
    val bookRepository: BookRepository by lazy { BookRepository(this) }
    val progressStore: ProgressStore by lazy { ProgressStore(progressDataStore) }
}
