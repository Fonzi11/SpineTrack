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
import com.example.spinetrack.util.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            if (email.isBlank())
                return Result.failure(Exception("El email es requerido"))
            if (password.length < 6)
                return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))

            val authResult = FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .await()

            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Usuario no encontrado"))

            val user = User(
                id       = firebaseUser.uid,
                nombre   = firebaseUser.displayName
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
                email    = firebaseUser.email ?: email,
                photoUrl = firebaseUser.photoUrl?.toString()
            )
            saveUserSession(user)
            _authState.value = AuthState.Authenticated(user)
            // Registrar dispositivo en Realtime Database (si no existe)
            try {
                DispositivoRepository.registrarDispositivo(user.id, user.nombre)
            } catch (_: Exception) {
                // Ignorar fallos para no bloquear el login
            }
            Result.success(user)

        } catch (_: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No existe una cuenta con este email"))
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Contraseña incorrecta"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al iniciar sesión"))
        }
    }

    fun register(email: String, password: String, nombre: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.register(email, password, nombre)
            _isLoading.value = false

            result.fold(
                onSuccess = { user ->
                    saveUserSession(user)
                    _authState.value = AuthState.Authenticated(user)
                    // Registrar dispositivo en la RTDB (no bloquear el flujo de registro)
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

    fun loginWithGoogle(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = AuthRepository.loginWithGoogle(idToken)
            _isLoading.value = false

            result.fold(
                onSuccess = { user ->
                    saveUserSession(user)
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