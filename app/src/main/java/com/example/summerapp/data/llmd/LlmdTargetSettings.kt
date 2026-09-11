package com.example.summerapp.data.llmd

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.llmdTargetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "llmd_target",
)

interface LlmdTargetSettings {
    val defaultTarget: LlmdTarget
    val selectedTarget: Flow<LlmdTarget>

    suspend fun selectTarget(target: LlmdTarget)
}

class DataStoreLlmdTargetSettings(context: Context) : LlmdTargetSettings {
    private val appContext = context.applicationContext
    private val dataStore = appContext.llmdTargetDataStore

    override val defaultTarget: LlmdTarget =
        if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            LlmdTarget.Debug
        } else {
            LlmdTarget.Release
        }

    override val selectedTarget: Flow<LlmdTarget> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            preferences[SELECTED_TARGET]?.let(LlmdTarget::fromPreference) ?: defaultTarget
        }

    override suspend fun selectTarget(target: LlmdTarget) {
        dataStore.edit { preferences ->
            preferences[SELECTED_TARGET] = target.preferenceValue
        }
    }

    private companion object {
        val SELECTED_TARGET = stringPreferencesKey("selected_target")
    }
}
