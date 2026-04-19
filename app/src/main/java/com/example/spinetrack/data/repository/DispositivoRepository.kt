package com.example.spinetrack.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio para operaciones sobre el nodo /dispositivos en Firebase Realtime Database.
 *
 * Nota: Las reglas de RTDB deben permitir lectura/escritura en /dispositivos/{uid}
 * solo si auth.uid == uid. Ver comentario más abajo.
 */
object DispositivoRepository {

    private const val TAG = "DispositivoRepo"

    private fun db() = FirebaseDatabase.getInstance()

    private fun getAuthenticatedUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    private fun <T> requireAuth(result: T): Result<T> {
        val uid = getAuthenticatedUid()
        return if (uid != null) Result.success(result)
        else Result.failure(SecurityException("Usuario no autenticado"))
    }

    suspend fun registrarDispositivo(uid: String, nombre: String): Result<Unit> {
        // Verificar que el uid coincide con el usuario autenticado
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            return Result.failure(SecurityException("UID no coincide con usuario autenticado"))
        }
        return try {
            val ref = db().getReference("dispositivos").child(uid)

            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                // Mantener config existente, pero sincronizar identidad visible en app/Pi.
                ref.child("config").updateChildren(
                    mapOf(
                        "uid" to uid,
                        "nombre" to nombre
                    )
                ).await()

                if (!snapshot.child("control").child("comando").exists()) {
                    ref.child("control").child("comando").setValue("idle").await()
                }

                if (!snapshot.child("estado").exists()) {
                    val estado = mapOf(
                        "activo" to false,
                        "calibrado" to false,
                        "sesion_activa" to false,
                        "ts_ultimo" to 0
                    )
                    ref.child("estado").setValue(estado).await()
                }

                Log.d(TAG, "Dispositivo ya registrado para $uid, identidad actualizada")
                return Result.success(Unit)
            }

            val config = mapOf(
                "uid" to uid,
                "nombre" to nombre,
                "sensibilidad" to "normal",
                "pitch_on" to 3.0,
                "roll_on" to 3.0,
                "pitch_off" to 5.0,
                "roll_off" to 5.0
            )

            // Escribir configuración, comando inicial y estado inicial
            ref.child("config").setValue(config).await()
            Log.d(TAG, "config escrito para $uid")
            ref.child("control").child("comando").setValue("idle").await()
            Log.d(TAG, "comando inicial idle escrito para $uid")
            val estado = mapOf(
                "activo" to false,
                "calibrado" to false,
                "sesion_activa" to false,
                "ts_ultimo" to 0
            )
            ref.child("estado").setValue(estado).await()
            Log.d(TAG, "estado inicial escrito para $uid")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando dispositivo $uid", e)
            Result.failure(e)
        }
    }

    suspend fun enviarComando(uid: String, comando: String): Result<Unit> {
        // Verificar que el uid coincide con el usuario autenticado
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            return Result.failure(SecurityException("UID no coincide con usuario autenticado"))
        }
        return try {
            val allowed = setOf("iniciar", "detener", "calibrar", "idle")
            if (!allowed.contains(comando)) return Result.failure(IllegalArgumentException("Comando no permitido"))
            val commandRef = db().getReference("dispositivos").child(uid).child("control").child("comando")

            // Fuerza flanco cuando se repite el mismo comando (ej. calibrar -> calibrar).
            val currentValue = commandRef.get().await().getValue(String::class.java)
            if (currentValue == comando && comando != "idle") {
                commandRef.setValue("idle").await()
            }

            commandRef.setValue(comando).await()

            // Confirmar persistencia para detectar reglas/reescrituras inesperadas.
            val persisted = commandRef.get().await().getValue(String::class.java)
            if (persisted != comando) {
                return Result.failure(
                    IllegalStateException("El comando no se persistio correctamente. Valor actual: ${persisted ?: "null"}")
                )
            }
            Log.d(TAG, "Comando '$comando' enviado a $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando comando '$comando' a $uid", e)
            // Mejorar mensaje cuando la causa sea permiso denegado
            val msg = when {
                e.message?.contains("Permission denied", ignoreCase = true) == true -> "Permiso denegado al escribir en /dispositivos/$uid (ver reglas de Firebase)"
                else -> e.message ?: "Error desconocido al enviar comando"
            }
            Result.failure(Exception(msg, e))
        }
    }

    /**
     * Escucha el nodo /dispositivos/{uid}/estado y emite cambios como Flow<Map>.
     * La conversión a EstadoDispositivo se realiza en la capa de ViewModel.
     */
    fun escucharEstado(uid: String): Flow<Map<String, Any?>?> = callbackFlow {
        // Verificar autenticación
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            Log.w(TAG, "UID no coincide con usuario autenticado para escuchar estado")
            close(SecurityException("UID no coincide con usuario autenticado"))
            return@callbackFlow
        }
        val ref = db().getReference("dispositivos").child(uid).child("estado")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any?>
                Log.d(TAG, "estado cambiado para $uid: $map")
                trySend(map).isSuccess
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Escucha estado cancelada para $uid: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    suspend fun actualizarSensibilidad(uid: String, sensibilidad: String, pitchOn: Double, rollOn: Double): Result<Unit> {
        // Verificar que el uid coincide con el usuario autenticado
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            return Result.failure(SecurityException("UID no coincide con usuario autenticado"))
        }
        return try {
            val updates = mapOf<String, Any>(
                "sensibilidad" to sensibilidad,
                "pitch_on" to pitchOn,
                "roll_on" to rollOn
            )
            db().getReference("dispositivos").child(uid).child("config").updateChildren(updates).await()
            Log.d(TAG, "Sensibilidad actualizada para $uid -> $updates")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando sensibilidad para $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Envia una petición de pairing para que la Raspberry acepte a este usuario.
     * Escribe en: /pairing/requests/{raspberryId}/{clientUid}
     */
    suspend fun requestPairing(raspberryId: String, clientDisplayName: String): Result<Unit> {
        val currentUid = getAuthenticatedUid()
        if (currentUid == null) return Result.failure(SecurityException("Usuario no autenticado"))
        return try {
            val payload = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "clientUid" to currentUid,
                "clientDisplayName" to clientDisplayName,
                "clientDeviceInfo" to mapOf("platform" to "android", "appVersion" to "unknown"),
                "requestedActions" to listOf("pair", "calibrate")
            )
            db().getReference("pairing").child("requests").child(raspberryId).child(currentUid)
                .setValue(payload).await()
            Log.d(TAG, "Pairing request written for $raspberryId by $currentUid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing pairing request for $raspberryId by $currentUid", e)
            Result.failure(e)
        }
    }

    /**
     * Observa el ack que la Raspberry escribirá en /pairing/acks/{raspberryId}/{clientUid}
     * Emite el mapa completo del ack cuando cambie.
     */
    fun observePairingAck(raspberryId: String, clientUid: String): Flow<Map<String, Any?>?> = callbackFlow {
        val ref = db().getReference("pairing").child("acks").child(raspberryId).child(clientUid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any?>
                trySend(map).isSuccess
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

/*
 * REGLAS SUGERIDAS (comentario):
 *
 * {
 *   "rules": {
 *     "dispositivos": {
 *       "$uid": {
 *         ".read": "auth != null && auth.uid == $uid",
 *         ".write": "auth != null && auth.uid == $uid"
 *       }
 *     }
 *   }
 * }
 *
 * Estas reglas aseguran que cada usuario solo lea/escriba su propio nodo de dispositivo.
 */

