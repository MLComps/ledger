package com.ledger.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

interface DataStoreRepository {
  fun saveTextInputHistory(history: List<String>)
  fun readTextInputHistory(): List<String>
}

class DefaultDataStoreRepository(private val context: Context) : DataStoreRepository {
  private val KEY_TEXT_HISTORY = stringPreferencesKey("text_input_history")

  override fun saveTextInputHistory(history: List<String>) {
    runBlocking {
      context.dataStore.edit { prefs ->
        prefs[KEY_TEXT_HISTORY] = history.joinToString("")
      }
    }
  }

  override fun readTextInputHistory(): List<String> {
    return runBlocking {
      val prefs = context.dataStore.data.first()
      prefs[KEY_TEXT_HISTORY]?.split("")?.filter { it.isNotEmpty() } ?: emptyList()
    }
  }
}
