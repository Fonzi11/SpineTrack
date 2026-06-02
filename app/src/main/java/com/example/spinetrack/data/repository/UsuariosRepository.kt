package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.data.model.AvatarCamaronConfig
import com.example.spinetrack.data.model.AvatarCamaronDefaults
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

    suspend fun upsertUsuarioPublico(
        uid: String,
        nombre: String,
        email: String,
        photoUrl: String?
    ): Result<Unit> {
        return try {
            val ref = db.getReference("users/$uid")
            val snapshot = ref.get().await()
            val previous = snapshot.value as? Map<*, *> ?: emptyMap<String, Any>()

            val payload = hashMapOf<String, Any?>(
                "nombre" to nombre,
                "email" to email,
                "photoUrl" to photoUrl,
                "uid" to uid,
                "updated_at" to System.currentTimeMillis(),
                // Inicializa métricas sin sobreescribir progreso existente
                "puntos" to (previous["puntos"] ?: 0),
                "lecciones_completadas" to (previous["lecciones_completadas"] ?: 0),
                "racha_actual" to (previous["racha_actual"] ?: 0),
                "nivel" to (previous["nivel"] ?: 1)
            )

            ref.updateChildren(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun guardarAvatarCamaron(uid: String, config: AvatarCamaronConfig): Result<Unit> {
        return try {
            val payload = mapOf(
                "color" to config.colorKey,
                "accesorio" to config.accesorioKey,
                "size_sp" to config.sizeSp,
                "updated_at" to System.currentTimeMillis()
            )
            db.getReference("users/$uid/avatar_config").setValue(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun obtenerAvatarCamaron(uid: String): Result<AvatarCamaronConfig?> {
        return try {
            val snap = db.getReference("users/$uid/avatar_config").get().await()
            if (!snap.exists()) return Result.success(null)
            @Suppress("UNCHECKED_CAST")
            val map = snap.value as? Map<String, Any?> ?: return Result.success(null)
            val config = AvatarCamaronConfig(
                colorKey = map["color"] as? String ?: AvatarCamaronDefaults.DEFAULT_COLOR_KEY,
                accesorioKey = map["accesorio"] as? String ?: AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY,
                sizeSp = when (val v = map["size_sp"]) {
                    is Long -> v.toInt()
                    is Int -> v
                    is Double -> v.toInt()
                    is String -> v.toIntOrNull() ?: AvatarCamaronDefaults.DEFAULT_SIZE_SP
                    else -> AvatarCamaronDefaults.DEFAULT_SIZE_SP
                }
            )
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                    val key = child.key ?: return@mapNotNull null
                    if (map == null) return@mapNotNull null
                    val user = UserProfile.fromMap(key, map)
                    if (user.uid.isBlank() || user.nombre.isBlank()) return@mapNotNull null
                    user
                }.sortedBy { it.nombre.lowercase() }
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
                if (!ids.contains(key)) return@mapNotNull null
                val user = UserProfile.fromMap(key, map)
                if (user.nombre.isBlank()) return@mapNotNull null
                user
            }.sortedBy { it.nombre.lowercase() }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Agrega un amigo (bidireccional) para el usuario current.
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
