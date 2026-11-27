package com.muz.omnikit.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muz.omnikit.core.model.AppSettings
import com.muz.omnikit.core.model.Language
import com.muz.omnikit.core.model.ThemeMode
import com.muz.omnikit.core.model.ToolId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.omniKitDataStore: DataStore<Preferences> by preferencesDataStore(name = "omnikit_settings")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val HIDDEN_TOOLS = stringSetPreferencesKey("hidden_tools")
    }

    val settingsFlow: Flow<AppSettings> = context.omniKitDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            language = Language.fromCode(prefs[Keys.LANGUAGE]),
            hiddenToolIds = prefs[Keys.HIDDEN_TOOLS] ?: emptySet()
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.omniKitDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setLanguage(language: Language) {
        context.omniKitDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setToolVisible(toolId: ToolId, visible: Boolean) {
        context.omniKitDataStore.edit { prefs ->
            val current = (prefs[Keys.HIDDEN_TOOLS] ?: emptySet()).toMutableSet()
            if (visible) {
                current.remove(toolId.routeKey)
            } else {
                current.add(toolId.routeKey)
            }
            prefs[Keys.HIDDEN_TOOLS] = current
        }
    }

    suspend fun showAllTools() {
        context.omniKitDataStore.edit { prefs ->
            prefs[Keys.HIDDEN_TOOLS] = emptySet()
        }
    }

    suspend fun hideAllTools() {
        context.omniKitDataStore.edit { prefs ->
            prefs[Keys.HIDDEN_TOOLS] = ToolId.entries.map { it.routeKey }.toSet()
        }
    }
}
