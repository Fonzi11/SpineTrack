package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.UserProfile
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object UsuariosRepository {
    private val db = FirebaseDatabase.getInstance()

    /**
     * Escucha todos los usuarios públicos en el nodo `users`.
     */
    fun escucharUsuariosPublicos(): Flow<List<UserProfile>> = callbackFlow {
        val ref = db.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = snapshot.children.mapNotNull { child ->
                    @Suppress("UNCHECKED_CAST")
                    val map = child.value as? Map<String, Any>
                    if (map != null) UserProfile.fromMap(child.key ?: "", map) else null
                }
                trySend(lista)
            }

            override fun onCancelled(error: DatabaseError) {
                // Evitar que la excepción de Firebase derribe el hilo UI.
                Log.w("UsuariosRepository", "Firebase escuchador cancelado: ${error.message}")
                // Si no hay permisos (Permission denied) no lanzamos la excepción hacia arriba,
                // en su lugar notificamos por el Flow con una lista vacía y cerramos silenciosamente.
                try {
                    // intentar enviar una lista vacía para que el UI pueda manejar el estado
                    trySend(emptyList())
                } catch (_: Exception) { }
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Obtiene objetos UserProfile para los uids listados en `amigos/{uid}`
     */
    suspend fun obtenerAmigos(uid: String): Result<List<UserProfile>> {
        return try {
            val amigosSnap = db.getReference("amigos/$uid").get().await()
            val ids = amigosSnap.children.mapNotNull { it.key }
            val usersRef = db.getReference("users")
            val snapshot = usersRef.get().await()
            val users = snapshot.children.mapNotNull { child ->
                val key = child.key ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val map = child.value as? Map<String, Any> ?: return@mapNotNull null
                if (ids.contains(key)) UserProfile.fromMap(key, map) else null
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Agrega un amigo (bidireccional) para el usuario actual.
     */
    suspend fun agregarAmigo(uid: String, friendUid: String): Result<Boolean> {
        return try {
            val updates = mapOf(
                "amigos/$uid/$friendUid" to true,
                "amigos/$friendUid/$uid" to true
            )
            db.reference.updateChildren(updates).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Elimina un amigo (bidireccional).
     */
    suspend fun eliminarAmigo(uid: String, friendUid: String): Result<Boolean> {
        return try {
            val updates = mapOf<String, Any?>(
                "amigos/$uid/$friendUid" to null,
                "amigos/$friendUid/$uid" to null
            )
            db.reference.updateChildren(updates).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifica si friendUid es amigo de uid.
     */
    suspend fun esAmigo(uid: String, friendUid: String): Boolean {
        return try {
            val snap = db.getReference("amigos/$uid/$friendUid").get().await()
            snap.exists()
        } catch (_: Exception) {
            false
        }
    }
}

