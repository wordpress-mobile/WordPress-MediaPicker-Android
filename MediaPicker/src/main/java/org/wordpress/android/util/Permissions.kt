package org.wordpress.android.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
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
    private val map = mutableMapOf<Key, Boolean?>()

    operator fun get(key: Key) = map[key] ?: false

    fun markAsAsked(key: Key) {
        scope.launch {
            savePermissionAsked(key, true)
        }
    }

    init {
        scope.launch {
            getPermissionAsked(PERMISSION_STORAGE_READ).map {
                map[PERMISSION_STORAGE_READ] = it
            }
        }

        scope.launch {
            getPermissionAsked(PERMISSION_CAMERA).map {
                map[PERMISSION_CAMERA] = it
            }
        }
    }

    private fun getPermissionAsked(key: Key) = context.dataStore.data
        .map { settings -> settings[key] }

    private suspend fun savePermissionAsked(key: Key, wasAsked: Boolean) {
        context.dataStore.edit { settings ->
            settings[key] = wasAsked
        }
    }

    fun contains(key: Key) = map.containsKey(key) && map[key] != null
}
