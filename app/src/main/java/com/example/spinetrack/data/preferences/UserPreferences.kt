package com.example.spinetrack.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Maneja la persistencia de datos del usuario usando DataStore.
 * Guarda información de sesión y preferencias básicas.
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val USER_ID = stringPreferencesKey("user_id")
                private val LAST_SESSION_ID = stringPreferencesKey("last_session_id")
        private val LAST_STATS_UPDATE = stringPreferencesKey("last_stats_update")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
    }

    /**
     * Flow que emite el ID del usuario logueado.
     * Null si no hay sesión activa.
     */
    val userIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID]
    }

    /**
     * Flow que emite el último session_id observado por la app (si existe).
     */
    val lastSessionIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SESSION_ID]
    }

    /** Flow que emite la última actualización de estadísticas (string ISO timestamp) */
    val lastStatsUpdateFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_STATS_UPDATE]
    }

    /**
     * Flow que emite el nombre del usuario.
     */
    val userNameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME]
    }

    /**
     * Flow que emite el email del usuario.
     */
    val userEmailFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL]
    }

    /**
     * Guarda los datos del usuario en DataStore.
     */
    suspend fun saveUserSession(
        id: String,
        name: String,
        email: String,
        photoUrl: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = id
            preferences[USER_NAME] = name
            preferences[USER_EMAIL] = email
            photoUrl?.let { preferences[USER_PHOTO_URL] = it }
        }
    }

    /**
     * Sincroniza solo el UID del usuario autenticado.
     * No toca nombre/email para evitar sobreescrituras involuntarias.
     */
    suspend fun syncUserId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = id
        }
    }

    /**
     * Actualiza el nombre del usuario.
     */
    suspend fun updateUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

    /**
     * Elimina la sesión del usuario (logout).
     */
    suspend fun clearUserSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(USER_NAME)
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_PHOTO_URL)
        }
    }

    /**
     * Guarda el último session_id observado (usado para sincronizar estadísticas).
     */
    suspend fun saveLastSessionId(sessionId: String?) {
        context.dataStore.edit { preferences ->
            if (sessionId == null) preferences.remove(LAST_SESSION_ID)
            else preferences[LAST_SESSION_ID] = sessionId
        }
    }

    /** Guarda un timestamp para notificar a otras partes que deben recargar stats */
    suspend fun saveLastStatsUpdate(timestampIso: String?) {
        context.dataStore.edit { preferences ->
            if (timestampIso == null) preferences.remove(LAST_STATS_UPDATE)
            else preferences[LAST_STATS_UPDATE] = timestampIso
        }
    }

    /**
     * Verifica si hay una sesión activa.
     */
    fun isLoggedIn(): Flow<Boolean> = userIdFlow.map { it != null }
}