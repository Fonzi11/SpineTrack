package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.RealtimePostura
import com.example.spinetrack.data.model.SesionPostural
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object SesionesRepository {

    private val db = FirebaseDatabase.getInstance()

    private fun getAuthenticatedUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Obtiene todas las sesiones de un usuario ordenadas por fecha descendente.
     */
    suspend fun obtenerSesiones(uid: String): Result<List<SesionPostural>> {
        val currentUid = getAuthenticatedUid()
            ?: return Result.failure(SecurityException("Usuario no autenticado"))
        if (uid != currentUid) {
            return Result.failure(SecurityException("UID solicitado no coincide con usuario autenticado"))
        }
        val targetUid = currentUid
        return try {
            val snapshot = db.getReference("sesiones/$targetUid")
                .orderByChild("ts_inicio")
                .get()
                .await()

            val sesiones = snapshot.children
                .mapNotNull { child ->
                    @Suppress("UNCHECKED_CAST")
                    (child.value as? Map<String, Any>)?.let {
                        SesionPostural.fromMap(it)
                    }
                }
                .sortedByDescending { it.tsInicio }

            Result.success(sesiones)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene las últimas N sesiones de un usuario.
     */
    suspend fun obtenerUltimasSesiones(uid: String, limite: Int = 10): Result<List<SesionPostural>> {
        val currentUid = getAuthenticatedUid()
            ?: return Result.failure(SecurityException("Usuario no autenticado"))
        if (uid != currentUid) {
            return Result.failure(SecurityException("UID solicitado no coincide con usuario autenticado"))
        }
        val targetUid = currentUid
        return try {
            val snapshot = db.getReference("sesiones/$targetUid")
                .orderByChild("ts_inicio")
                .limitToLast(limite)
                .get()
                .await()

            val sesiones = snapshot.children
                .mapNotNull { child ->
                    @Suppress("UNCHECKED_CAST")
                    (child.value as? Map<String, Any>)?.let {
                        SesionPostural.fromMap(it)
                    }
                }
                .sortedByDescending { it.tsInicio }

            Result.success(sesiones)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene una sesión específica por su session_id para un usuario.
     */
    suspend fun obtenerSesion(uid: String, sessionId: String): Result<SesionPostural?> {
        val currentUid = getAuthenticatedUid()
            ?: return Result.failure(SecurityException("Usuario no autenticado"))
        if (uid != currentUid) {
            return Result.failure(SecurityException("UID solicitado no coincide con usuario autenticado"))
        }
        val targetUid = currentUid
        return try {
            val ref = db.getReference("sesiones/$targetUid/$sessionId")
            val snapshot = ref.get().await()
            if (!snapshot.exists() || snapshot.value == null) {
                Result.success(null)
            } else {
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any>
                val sesion = map?.let { SesionPostural.fromMap(it) }
                Result.success(sesion)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Escucha el nodo realtime en tiempo real (Flow continuo).
     * Se cancela automáticamente cuando el scope del colector se cancela.
     */
    fun escucharRealtime(uid: String?): Flow<RealtimePostura> = callbackFlow {
        // Si no hay uid (usuario no logueado), no intentamos leer una ruta 'sensores/null'
        // y en su lugar devolvemos un flow que emite un objeto vacío para que la UI muestre
        // estado 'SinDispositivo' sin lanzar excepciones.
        if (uid.isNullOrBlank()) {
            trySend(RealtimePostura())
            awaitClose { /* no-op */ }
            return@callbackFlow
        }

        val ref = db.getReference("sensores/$uid/realtime")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // ← Si no hay datos aún, emitir objeto vacío en vez de crashear
                if (!snapshot.exists() || snapshot.value == null) {
                    trySend(RealtimePostura())  // uid vacío → ViewModel muestra SinDispositivo
                    return
                }
                @Suppress("UNCHECKED_CAST")
                val map = snapshot.value as? Map<String, Any> ?: return
                trySend(RealtimePostura.fromMap(map))
            }
            override fun onCancelled(error: DatabaseError) {
                // No cerrar el flow con excepción — emitimos un estado vacío para que la UI
                // no crashee cuando las reglas de Firebase nieguen el acceso.
                trySend(RealtimePostura())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Calcula el ICP promedio de las últimas N sesiones.
     */
    fun calcularIcpPromedio(sesiones: List<SesionPostural>): Double {
        if (sesiones.isEmpty()) return 0.0
        return sesiones.map { it.icp }.average()
    }

    /**
     * Calcula el tiempo total monitoreado en minutos.
     */
    fun calcularTiempoTotalMin(sesiones: List<SesionPostural>): Double {
        return sesiones.sumOf { it.duracionMin }
    }

    /**
     * Calcula la racha actual de días consecutivos con sesión registrada.
     */
    fun calcularRachaActual(sesiones: List<SesionPostural>): Int {
        if (sesiones.isEmpty()) return 0
        val fechas = sesiones
            .mapNotNull { it.tsInicio.take(10).takeIf { f -> f.length == 10 } }
            .distinct()
            .sortedDescending()

        var racha = 1
        for (i in 0 until fechas.size - 1) {
            val hoy = java.time.LocalDate.parse(fechas[i])
            val ayer = java.time.LocalDate.parse(fechas[i + 1])
            if (hoy.minusDays(1) == ayer) racha++ else break
        }
        return racha
    }
}