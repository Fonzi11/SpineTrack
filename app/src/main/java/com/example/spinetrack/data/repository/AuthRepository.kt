package com.example.spinetrack.data.repository

import android.util.Patterns
import com.example.spinetrack.data.model.User
import com.example.spinetrack.util.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

object AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val normalizedEmail = email.trim().lowercase()
            if (normalizedEmail.isBlank()) {
                return Result.failure(Exception("El email es requerido"))
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                return Result.failure(Exception("Correo invalido"))
            }
            if (password.length < 6) {
                return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))
            }

            val authResult = auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Usuario no encontrado"))

            Result.success(
                User(
                    id = firebaseUser.uid,
                    nombre = firebaseUser.displayName
                        ?: normalizedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    email = firebaseUser.email ?: normalizedEmail,
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
            )
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No existe una cuenta con este email"))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Email o contraseña incorrectos"))
        } catch (e: FirebaseAuthException) {
            val msg = when (e.errorCode) {
                "ERROR_USER_DISABLED" -> "Esta cuenta fue deshabilitada"
                "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Intenta mas tarde"
                else -> e.message ?: "Error al iniciar sesión"
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al iniciar sesión"))
        }
    }

    suspend fun register(email: String, password: String, nombre: String): Result<User> {
        return try {
            val normalizedEmail = email.trim().lowercase()
            val normalizedNombre = nombre.trim()

            if (normalizedEmail.isBlank()) {
                return Result.failure(Exception("El email es requerido"))
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
                return Result.failure(Exception("Correo invalido"))
            }
            if (normalizedNombre.isBlank()) {
                return Result.failure(Exception("El nombre es requerido"))
            }
            if (password.length < 6) {
                return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))
            }

            val authResult = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Error al crear usuario"))

            val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(normalizedNombre)
                .build()
            firebaseUser.updateProfile(profileUpdate).await()

            Result.success(
                User(
                    id = firebaseUser.uid,
                    nombre = normalizedNombre,
                    email = firebaseUser.email ?: normalizedEmail,
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
            )
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("La contraseña es muy débil"))
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("Este email ya está registrado"))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Correo invalido"))
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception(e.message ?: "Error al registrar"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al registrar"))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            if (idToken.isBlank()) {
                return Result.failure(Exception("Token de Google vacio"))
            }
            val credential = com.google.firebase.auth.GoogleAuthProvider
                .getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Error al autenticar con Google"))

            Result.success(User(
                id       = firebaseUser.uid,
                nombre   = firebaseUser.displayName ?: "Usuario",
                email    = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString()
            ))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            val raw = e.message.orEmpty().lowercase()
            if (raw.contains("audience") || raw.contains("client") || raw.contains("id token")) {
                Result.failure(
                    Exception(
                        "Google Sign-In mal configurado: revisa Web client ID en Firebase, activa el proveedor Google y agrega SHA-1/SHA-256"
                    )
                )
            } else {
                Result.failure(Exception("Credenciales de Google invalidas"))
            }
        } catch (e: FirebaseAuthException) {
            val mapped = when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" -> "Proveedor Google no habilitado en Firebase Auth"
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "Este correo ya existe con otro metodo de acceso"
                else -> e.message ?: "Error autenticando con Firebase"
            }
            Result.failure(Exception(mapped))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al iniciar sesión con Google"))
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            if (email.isBlank())
                return Result.failure(Exception("El email es requerido"))
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No existe una cuenta con este email"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al enviar email de recuperación"))
        }
    }

    suspend fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id       = firebaseUser.uid,
            nombre   = firebaseUser.displayName
                ?: firebaseUser.email?.substringBefore("@") ?: "Usuario",
            email    = firebaseUser.email ?: "",
            photoUrl = firebaseUser.photoUrl?.toString()
        )
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}