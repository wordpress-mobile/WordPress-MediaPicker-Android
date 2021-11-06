package org.wordpress.android.mediapicker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

typealias Key = Preferences.Key<Boolean>

@Singleton
class Permissions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        val PERMISSION_STORAGE_READ = booleanPreferencesKey("PERMISSION_STORAGE_READ")
        val PERMISSION_CAMERA = booleanPreferencesKey("PERMISSION_CAMERA")
    }

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("permissions")

    fun markAsAsked(key: Key) {
        scope.launch {
            savePermissionAsked(key)
        }
    }

    suspend fun wasPermissionAsked(key: Key) = context.dataStore.data.first().contains(key)

    private suspend fun savePermissionAsked(key: Key) {
        context.dataStore.edit { settings ->
            settings[key] = true
        }
    }
}
