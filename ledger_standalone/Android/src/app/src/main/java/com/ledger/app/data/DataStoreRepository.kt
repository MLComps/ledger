package com.ledger.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ledger_settings")

interface DataStoreRepository {
  fun saveTextInputHistory(history: List<String>)
  fun readTextInputHistory(): List<String>
  fun saveCurrencyCode(code: String)
  fun readCurrencyCode(): String
  fun currencyCodeFlow(): Flow<String>
  fun saveHasSeenOnboarding(seen: Boolean)
  fun readHasSeenOnboarding(): Boolean
  fun saveHfAccessToken(token: String, expiresAt: Long)
  fun readHfAccessToken(): Pair<String, Long>?
  fun clearHfAccessToken()
  fun saveValidationMode(mode: String)
  fun readValidationMode(): String
  fun saveSmsEnabled(enabled: Boolean)
  fun readSmsEnabled(): Boolean
  fun smsEnabledFlow(): Flow<Boolean>
  fun saveThemeMode(mode: String)
  fun readThemeMode(): String
  fun themeModeFlow(): Flow<String>
}

class DefaultDataStoreRepository(private val context: Context) : DataStoreRepository {
  private val KEY_TEXT_HISTORY = stringPreferencesKey("text_input_history")
  private val KEY_CURRENCY_CODE = stringPreferencesKey("currency_code")
  private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
  private val KEY_HF_ACCESS_TOKEN = stringPreferencesKey("hf_access_token")
  private val KEY_HF_TOKEN_EXPIRES_AT = longPreferencesKey("hf_token_expires_at")
  private val KEY_VALIDATION_MODE = stringPreferencesKey("validation_mode")
  private val KEY_SMS_ENABLED = booleanPreferencesKey("sms_enabled")
  private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

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

  override fun currencyCodeFlow(): Flow<String> =
    context.dataStore.data.map { it[KEY_CURRENCY_CODE] ?: "KES" }

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

  override fun saveHfAccessToken(token: String, expiresAt: Long) {
    runBlocking {
      context.dataStore.edit { prefs ->
        prefs[KEY_HF_ACCESS_TOKEN] = token
        prefs[KEY_HF_TOKEN_EXPIRES_AT] = expiresAt
      }
    }
  }

  override fun readHfAccessToken(): Pair<String, Long>? {
    return runBlocking {
      val prefs = context.dataStore.data.first()
      val token = prefs[KEY_HF_ACCESS_TOKEN]
      val expiresAt = prefs[KEY_HF_TOKEN_EXPIRES_AT]
      if (token != null && expiresAt != null) token to expiresAt else null
    }
  }

  override fun clearHfAccessToken() {
    runBlocking {
      context.dataStore.edit { prefs ->
        prefs.remove(KEY_HF_ACCESS_TOKEN)
        prefs.remove(KEY_HF_TOKEN_EXPIRES_AT)
      }
    }
  }

  override fun saveValidationMode(mode: String) {
    runBlocking {
      context.dataStore.edit { prefs -> prefs[KEY_VALIDATION_MODE] = mode }
    }
  }

  override fun readValidationMode(): String {
    return runBlocking {
      context.dataStore.data.first()[KEY_VALIDATION_MODE] ?: "all"
    }
  }

  override fun saveSmsEnabled(enabled: Boolean) {
    runBlocking {
      context.dataStore.edit { prefs -> prefs[KEY_SMS_ENABLED] = enabled }
    }
  }

  override fun readSmsEnabled(): Boolean {
    return runBlocking {
      context.dataStore.data.first()[KEY_SMS_ENABLED] ?: true
    }
  }

  override fun smsEnabledFlow(): Flow<Boolean> =
    context.dataStore.data.map { it[KEY_SMS_ENABLED] ?: true }

  override fun saveThemeMode(mode: String) {
    runBlocking {
      context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }
  }

  override fun readThemeMode(): String {
    return runBlocking {
      context.dataStore.data.first()[KEY_THEME_MODE] ?: "system"
    }
  }

  override fun themeModeFlow(): Flow<String> =
    context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
}
