package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.User
import com.example.spinetrack.util.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

object AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            if (email.isBlank())
                return Result.failure(Exception("El email es requerido"))
            if (password.length < 6)
                return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))

            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Usuario no encontrado"))

            Result.success(User(
                id       = firebaseUser.uid,
                nombre   = firebaseUser.displayName
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
                email    = firebaseUser.email ?: email,
                photoUrl = firebaseUser.photoUrl?.toString()
            ))
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No existe una cuenta con este email"))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Contraseña incorrecta"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al iniciar sesión"))
        }
    }

    suspend fun register(email: String, password: String, nombre: String): Result<User> {
        return try {
            if (email.isBlank())
                return Result.failure(Exception("El email es requerido"))
            if (nombre.isBlank())
                return Result.failure(Exception("El nombre es requerido"))
            if (password.length < 6)
                return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))

            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(Exception("Error al crear usuario"))

            val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(nombre)
                .build()
            firebaseUser.updateProfile(profileUpdate).await()

            Result.success(User(
                id       = firebaseUser.uid,
                nombre   = nombre,
                email    = email,
                photoUrl = null
            ))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(Exception("La contraseña es muy débil"))
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Result.failure(Exception("Este email ya está registrado"))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Error al registrar"))
        }
    }

    suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
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