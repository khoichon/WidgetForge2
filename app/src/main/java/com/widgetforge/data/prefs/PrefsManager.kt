package com.widgetforge.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "widgetforge_prefs")

@Singleton
class PrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.dataStore

    companion object {
        val KEY_FIRST_RUN = booleanPreferencesKey("first_run")
        val KEY_SAMPLE_BUNDLES_INSTALLED = booleanPreferencesKey("samples_installed")
        val KEY_LAST_EXPORT_DIR = stringPreferencesKey("last_export_dir")
        val KEY_INSTALL_TIME = longPreferencesKey("install_time")
        val KEY_RENDERING_QUALITY = stringPreferencesKey("rendering_quality") // LOW / MEDIUM / HIGH
    }

    val isFirstRun: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_FIRST_RUN] ?: true
    }

    val areSamplesInstalled: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_SAMPLE_BUNDLES_INSTALLED] ?: false
    }

    val lastExportDir: Flow<String> = store.data.map { prefs ->
        prefs[KEY_LAST_EXPORT_DIR] ?: ""
    }

    val renderingQuality: Flow<String> = store.data.map { prefs ->
        prefs[KEY_RENDERING_QUALITY] ?: "HIGH"
    }

    suspend fun setFirstRunComplete() {
        store.edit { prefs ->
            prefs[KEY_FIRST_RUN] = false
            prefs[KEY_INSTALL_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun setSamplesInstalled() {
        store.edit { it[KEY_SAMPLE_BUNDLES_INSTALLED] = true }
    }

    suspend fun setLastExportDir(dir: String) {
        store.edit { it[KEY_LAST_EXPORT_DIR] = dir }
    }

    suspend fun setRenderingQuality(quality: String) {
        store.edit { it[KEY_RENDERING_QUALITY] = quality }
    }
}
