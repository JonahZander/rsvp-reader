package com.jonahzander.rsvpreader.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jonahzander.rsvpreader.ReaderApplication

class MainActivity : ComponentActivity() {

    private var launchBookId by mutableStateOf<String?>(null)
    private var launchNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fresh starts only: on recreation (config change, memory pressure)
        // the nav back stack is restored, and replaying a stale tile intent
        // would force the reader back on top of it.
        if (savedInstanceState == null) readLaunchIntent(intent)
        setContent {
            WearApp(
                app = application as ReaderApplication,
                launchBookId = launchBookId,
                launchNonce = launchNonce,
            )
        }
    }

    // The activity is singleTask, so a tile tap while the app is alive lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLaunchIntent(intent)
    }

    private fun readLaunchIntent(intent: Intent) {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return
        launchBookId = bookId
        launchNonce++
    }

    companion object {
        /** Set by the continue-reading tile to jump straight into the reader. */
        const val EXTRA_BOOK_ID = "com.jonahzander.rsvpreader.extra.BOOK_ID"
    }
}
