package com.ledger.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

interface DataStoreRepository {
  fun saveTextInputHistory(history: List<String>)
  fun readTextInputHistory(): List<String>
  fun saveCurrencyCode(code: String)
  fun readCurrencyCode(): String
  fun saveHasSeenOnboarding(seen: Boolean)
  fun readHasSeenOnboarding(): Boolean
}

class DefaultDataStoreRepository(private val context: Context) : DataStoreRepository {
  private val KEY_TEXT_HISTORY = stringPreferencesKey("text_input_history")
  private val KEY_CURRENCY_CODE = stringPreferencesKey("currency_code")
  private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")

  override fun saveTextInputHistory(history: List<String>) {
    runBlocking {
      context.dataStore.edit { prefs ->
        prefs[KEY_TEXT_HISTORY] = history.joinToString("")
      }
    }
  }

  override fun readTextInputHistory(): List<String> {
    return runBlocking {
      val prefs = context.dataStore.data.first()
      prefs[KEY_TEXT_HISTORY]?.split("")?.filter { it.isNotEmpty() } ?: emptyList()
    }
  }

  override fun saveCurrencyCode(code: String) {
    runBlocking {
      context.dataStore.edit { prefs -> prefs[KEY_CURRENCY_CODE] = code }
    }
  }

  override fun readCurrencyCode(): String {
    return runBlocking {
      context.dataStore.data.first()[KEY_CURRENCY_CODE] ?: "KES"
    }
  }

  override fun saveHasSeenOnboarding(seen: Boolean) {
    runBlocking {
      context.dataStore.edit { prefs -> prefs[KEY_HAS_SEEN_ONBOARDING] = seen }
    }
  }

  override fun readHasSeenOnboarding(): Boolean {
    return runBlocking {
      context.dataStore.data.first()[KEY_HAS_SEEN_ONBOARDING] ?: false
    }
  }
}
