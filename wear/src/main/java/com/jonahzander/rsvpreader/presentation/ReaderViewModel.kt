package com.jonahzander.rsvpreader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.wear.tiles.TileService
import com.jonahzander.rsvpreader.ReaderApplication
import com.jonahzander.rsvpreader.tile.ContinueReadingTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(
    app: ReaderApplication,
    bookId: String,
    startChapter: Int?,
) : ViewModel() {

    private val _session = MutableStateFlow<ReaderSession?>(null)
    val session: StateFlow<ReaderSession?> = _session

    init {
        viewModelScope.launch {
            app.bookRepository.refresh()
            val book = app.bookRepository.get(bookId) ?: return@launch
            val session = ReaderSession(book, app.progressStore, viewModelScope, saveScope = app.appScope) {
                TileService.getUpdater(app).requestUpdate(ContinueReadingTileService::class.java)
            }
            if (startChapter != null) session.jumpToChapter(startChapter)
            _session.value = session
        }
    }

    override fun onCleared() {
        _session.value?.pause()
    }

    companion object {
        fun factory(app: ReaderApplication, bookId: String, startChapter: Int?) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    ReaderViewModel(app, bookId, startChapter) as T
            }
    }
}
