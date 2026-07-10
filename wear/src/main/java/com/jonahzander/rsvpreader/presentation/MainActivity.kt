package com.jonahzander.rsvpreader.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jonahzander.rsvpreader.ReaderApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchBookId = intent.getStringExtra(EXTRA_BOOK_ID)
        setContent {
            WearApp(application as ReaderApplication, launchBookId)
        }
    }

    companion object {
        /** Set by the continue-reading tile to jump straight into the reader. */
        const val EXTRA_BOOK_ID = "com.jonahzander.rsvpreader.extra.BOOK_ID"
    }
}
