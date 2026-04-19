package com.example.spinetrack.data.model

/**
 * Estados posibles de autenticación.
 * Usado para observar el estado de auth en la UI.
 */
sealed class AuthState {
    /** Estado inicial - verificando si hay sesión guardada */
    object Loading : AuthState()

    /** Usuario no autenticado - mostrar pantalla de login */
    object Unauthenticated : AuthState()

    /** Usuario autenticado - mostrar pantalla principal */
    data class Authenticated(val user: User) : AuthState()

    /** Error de autenticación - mostrar mensaje de error */
    data class Error(val message: String) : AuthState()
}