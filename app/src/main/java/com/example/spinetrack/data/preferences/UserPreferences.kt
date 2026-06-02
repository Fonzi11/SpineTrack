package com.example.spinetrack.data.preferences

import android.content.Context
import com.example.spinetrack.data.model.AvatarCamaronConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val AVATAR_CAMARON_COLOR = stringPreferencesKey("avatar_camaron_color")
        private val AVATAR_CAMARON_ACCESORIO = stringPreferencesKey("avatar_camaron_accesorio")
        private val AVATAR_CAMARON_SIZE = stringPreferencesKey("avatar_camaron_size")
        private val AVATAR_CAMARON_ENABLED = booleanPreferencesKey("avatar_camaron_enabled")
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

    val avatarCamaronConfigFlow: Flow<AvatarCamaronConfig> = context.dataStore.data.map { preferences ->
        AvatarCamaronConfig(
            colorKey = preferences[AVATAR_CAMARON_COLOR] ?: "coral",
            accesorioKey = preferences[AVATAR_CAMARON_ACCESORIO] ?: "none",
            sizeSp = preferences[AVATAR_CAMARON_SIZE]?.toIntOrNull() ?: 26
        )
    }

    val avatarCamaronEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AVATAR_CAMARON_ENABLED] ?: false
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

    suspend fun saveAvatarCamaronConfig(config: AvatarCamaronConfig) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_CAMARON_COLOR] = config.colorKey
            preferences[AVATAR_CAMARON_ACCESORIO] = config.accesorioKey
            preferences[AVATAR_CAMARON_SIZE] = config.sizeSp.toString()
            preferences[AVATAR_CAMARON_ENABLED] = true
        }
    }

    suspend fun resetAvatarCamaron() {
        context.dataStore.edit { preferences ->
            preferences.remove(AVATAR_CAMARON_COLOR)
            preferences.remove(AVATAR_CAMARON_ACCESORIO)
            preferences.remove(AVATAR_CAMARON_SIZE)
            preferences[AVATAR_CAMARON_ENABLED] = false
        }
    }

    /**
     * Verifica si hay una sesión activa.
     */
    fun isLoggedIn(): Flow<Boolean> = userIdFlow.map { it != null }
}