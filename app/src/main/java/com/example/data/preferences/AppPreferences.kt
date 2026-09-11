package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_chat_settings")

enum class ThemeMode(val title: String) {
    SYSTEM("System Default"),
    DARK("Dark Mode"),
    LIGHT("Light Mode")
}

data class UserSettings(
    val apiKey: String,
    val themeMode: ThemeMode,
    val defaultModel: String,
    val systemPrompt: String,
    val temperature: Float,
    val customEndpoint: String
)

class AppPreferences(private val context: Context) {

    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("openai_api_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val LAST_CONVERSATION_ID = stringPreferencesKey("last_conversation_id")
    }

    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val storedApiKey = preferences[PreferencesKeys.API_KEY] ?: ""
        // Fallback to BuildConfig if provided or empty
        val effectiveApiKey = storedApiKey.ifEmpty {
            try {
                // Check if BuildConfig has GEMINI_API_KEY or OPENAI_API_KEY
                val buildConfigKey = BuildConfig::class.java.getField("OPENAI_API_KEY").get(null) as? String
                buildConfigKey ?: ""
            } catch (e: Throwable) {
                ""
            }
        }

        val themeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val defaultModel = preferences[PreferencesKeys.DEFAULT_MODEL] ?: "llama3.2"
        val systemPrompt = preferences[PreferencesKeys.SYSTEM_PROMPT] ?: "You are a helpful, knowledgeable AI assistant. Give clear, well-formatted responses with markdown where appropriate."
        val temperature = preferences[PreferencesKeys.TEMPERATURE] ?: 0.7f
        val customEndpoint = preferences[PreferencesKeys.CUSTOM_ENDPOINT] ?: "https://rkchat-ai.duckdns.org/chat_ai"

        UserSettings(
            apiKey = effectiveApiKey,
            themeMode = themeMode,
            defaultModel = defaultModel,
            systemPrompt = systemPrompt,
            temperature = temperature,
            customEndpoint = customEndpoint
        )
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val themeString = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val lastConversationId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_CONVERSATION_ID]
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun saveDefaultModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_MODEL] = model
        }
    }

    suspend fun saveSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun saveTemperature(temp: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE] = temp
        }
    }

    suspend fun saveLastConversationId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id != null) {
                preferences[PreferencesKeys.LAST_CONVERSATION_ID] = id
            } else {
                preferences.remove(PreferencesKeys.LAST_CONVERSATION_ID)
            }
        }
    }

    suspend fun updateAllSettings(
        apiKey: String,
        themeMode: ThemeMode,
        defaultModel: String,
        systemPrompt: String,
        temperature: Float
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = apiKey.trim()
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
            preferences[PreferencesKeys.DEFAULT_MODEL] = defaultModel
            preferences[PreferencesKeys.SYSTEM_PROMPT] = systemPrompt
            preferences[PreferencesKeys.TEMPERATURE] = temperature
        }
    }
}
