@file:Suppress("unused")
package com.example.spinetrack.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.model.AuthState
import com.example.spinetrack.data.model.User
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.AuthRepository
import com.example.spinetrack.data.repository.DispositivoRepository
import com.example.spinetrack.data.repository.UsuariosRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkExistingSession()
    }

    /**
     * Verifica sesión usando solo Firebase Auth — sin Firestore.
     */
    private fun checkExistingSession() {
        viewModelScope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser

            if (firebaseUser != null) {
                val user = User(
                    id       = firebaseUser.uid,
                    nombre   = firebaseUser.displayName
                        ?: firebaseUser.email?.substringBefore("@") ?: "Usuario",
                    email    = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
                userPreferences.saveUserSession(
                    id       = user.id,
                    name     = user.nombre,
                    email    = user.email,
                    photoUrl = user.photoUrl
                )
                try {
                    UsuariosRepository.upsertUsuarioPublico(
                        uid = user.id,
                        nombre = user.nombre,
                        email = user.email,
                        photoUrl = user.photoUrl
                    )
                } catch (_: Exception) {
                }
                _authState.value = AuthState.Authenticated(user)
            } else {
                // Fallback a DataStore
                val userId = userPreferences.userIdFlow.first()
                if (userId != null) {
                    val user = User(
                        id     = userId,
                        nombre = userPreferences.userNameFlow.first() ?: "",
                        email  = userPreferences.userEmailFlow.first() ?: ""
                    )
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.login(email.trim(), password)
            _isLoading.value = false

            result.fold(
                onSuccess = { user ->
                    saveUserSession(user)
                    try {
                        UsuariosRepository.upsertUsuarioPublico(
                            uid = user.id,
                            nombre = user.nombre,
                            email = user.email,
                            photoUrl = user.photoUrl
                        )
                    } catch (_: Exception) {
                    }
                    _authState.value = AuthState.Authenticated(user)
                    try {
                        DispositivoRepository.registrarDispositivo(user.id, user.nombre)
                    } catch (_: Exception) {
                    }
                },
                onFailure = { error ->
                    val msg = error.message ?: "Error al iniciar sesión"
                    _errorMessage.value = msg
                    _authState.value = AuthState.Error(msg)
                }
            )
        }
    }

    fun register(email: String, password: String, nombre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.register(email.trim(), password, nombre.trim())
            _isLoading.value = false

            val user = result.getOrNull()
            if (user == null) {
                val msg = result.exceptionOrNull()?.message ?: "Error desconocido"
                _errorMessage.value = msg
                _authState.value = AuthState.Error(msg)
                return@launch
            }

            saveUserSession(user)

            val upsertResult = UsuariosRepository.upsertUsuarioPublico(
                uid = user.id,
                nombre = user.nombre,
                email = user.email,
                photoUrl = user.photoUrl
            )

            if (upsertResult.isFailure) {
                val cause = upsertResult.exceptionOrNull()?.message ?: "Sin detalle"
                AuthRepository.logout()
                userPreferences.clearUserSession()
                val msg = "Cuenta creada, pero no se pudo guardar el perfil en Firebase: $cause"
                _errorMessage.value = msg
                _authState.value = AuthState.Error(msg)
                return@launch
            }

            _authState.value = AuthState.Authenticated(user)

            // Registrar dispositivo en la RTDB (no bloquea el flujo principal)
            try {
                DispositivoRepository.registrarDispositivo(user.id, user.nombre)
            } catch (_: Exception) {
            }
        }
    }

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.loginWithGoogle(idToken)
            _isLoading.value = false

            result.fold(
                onSuccess = { user ->
                    saveUserSession(user)
                    try {
                        UsuariosRepository.upsertUsuarioPublico(
                            uid = user.id,
                            nombre = user.nombre,
                            email = user.email,
                            photoUrl = user.photoUrl
                        )
                    } catch (_: Exception) {
                    }
                    _authState.value = AuthState.Authenticated(user)
                    // Registrar dispositivo en la RTDB
                    try {
                        DispositivoRepository.registrarDispositivo(user.id, user.nombre)
                    } catch (_: Exception) {
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Error desconocido"
                    _authState.value = AuthState.Error(error.message ?: "Error desconocido")
                }
            )
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.resetPassword(email)
            _isLoading.value = false

            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Error desconocido"
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            AuthRepository.logout()
            userPreferences.clearUserSession()
            _authState.value = AuthState.Unauthenticated
        }
    }

    private suspend fun saveUserSession(user: User) {
        userPreferences.saveUserSession(
            id       = user.id,
            name     = user.nombre,
            email    = user.email,
            photoUrl = user.photoUrl
        )
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getCurrentUser(): User? {
        return when (val state = _authState.value) {
            is AuthState.Authenticated -> state.user
            else -> null
        }
    }
}