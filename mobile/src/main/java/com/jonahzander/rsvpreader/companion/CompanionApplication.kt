package com.jonahzander.rsvpreader.companion

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore

private val android.content.Context.settingsDataStore by preferencesDataStore(name = "settings")

class CompanionApplication : Application() {
    val repository: CompanionRepository by lazy { CompanionRepository(this, settingsDataStore) }
}
