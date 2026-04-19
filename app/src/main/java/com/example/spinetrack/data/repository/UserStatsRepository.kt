package com.example.spinetrack.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

object UserStatsRepository {

    private const val TAG = "UserStatsRepository"

    data class UserProfileStats(
        val uid: String,
        val nombre: String,
        val email: String,
        val puntos: Int,
        val lecciones: Int,
        val rachaActual: Int,
        val mejorRacha: Int,
        val nivel: Int,
        val puntosNivelActual: Int,
        val puntosSiguienteNivel: Int
    )

    private fun getAuthenticatedUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Actualiza valores del usuario al completar una lección:
     * - suma puntos
     * - incrementa contador de lecciones completadas
     * - actualiza racha si corresponde (si no se hizo actividad hoy)
     * - actualiza el nodo /ranking/{uid} para facilitar consultas de ranking
     */
    suspend fun markLessonCompletedRemote(uid: String, puntos: Int): Result<Boolean> {
        // Verificar que el uid coincide con el usuario autenticado
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            return Result.failure(SecurityException("Usuario no autenticado o UID no coincide"))
        }
        return try {
            val db = FirebaseDatabase.getInstance()
            val userRef = db.getReference("users/$uid")

            // Ejecutar operación en transacción para evitar race conditions.
            suspend fun runUserTransaction(): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                userRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        val map = currentData.value as? Map<String, Any?> ?: emptyMap<String, Any?>()
                        val curPts = (map["puntos"] as? Long)?.toInt() ?: 0
                        val curLecciones = (map["lecciones_completadas"] as? Long)?.toInt() ?: 0
                        val curRacha = (map["racha_actual"] as? Long)?.toInt() ?: 0
                        val curMejorRacha = (map["mejor_racha"] as? Long)?.toInt() ?: 0
                        val lastDate = map["last_activity_date"] as? String
                        val today = LocalDate.now().toString()
                        val newRacha = if (lastDate != today) curRacha + 1 else curRacha
                        val newPuntos = curPts + puntos
                        val newMejorRacha = maxOf(curMejorRacha, newRacha)
                        val newNivel = (newPuntos / 100) + 1

                        // actualizar valores en currentData
                        currentData.child("puntos").value = newPuntos
                        currentData.child("lecciones_completadas").value = curLecciones + 1
                        currentData.child("racha_actual").value = newRacha
                        currentData.child("mejor_racha").value = newMejorRacha
                        currentData.child("nivel").value = newNivel
                        currentData.child("last_activity_date").value = today

                        return com.google.firebase.database.Transaction.success(currentData)
                    }

                    override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {
                        if (error != null) cont.resumeWith(Result.failure(error.toException()))
                        else cont.resumeWith(Result.success(committed))
                    }
                })
            }

            val committed = runUserTransaction()
            if (!committed) return Result.failure(Exception("Transaction not committed"))

            // luego actualizar /ranking con valores más recientes (lectura simple)
            val snap = userRef.get().await()
            val map = snap.value as? Map<String, Any?> ?: emptyMap()
            val updatedPts = (map["puntos"] as? Long)?.toInt() ?: 0
            val updatedLecciones = (map["lecciones_completadas"] as? Long)?.toInt() ?: 0
            val updatedRacha = (map["racha_actual"] as? Long)?.toInt() ?: 0
            val updatedMejorRacha = (map["mejor_racha"] as? Long)?.toInt() ?: updatedRacha
            val updatedNivel = (map["nivel"] as? Long)?.toInt() ?: ((updatedPts / 100) + 1)

            val rankingRef = db.getReference("ranking/$uid")
            val rankUpdates = mapOf<String, Any>(
                "puntos" to updatedPts,
                "lecciones" to updatedLecciones,
                "racha" to updatedRacha,
                "mejor_racha" to updatedMejorRacha,
                "nivel" to updatedNivel,
                "nombre" to (map["nombre"] as? String ?: "")
            )
            rankingRef.updateChildren(rankUpdates).await()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class UserStats(val puntos: Int = 0, val lecciones: Int = 0, val racha: Int = 0)

    private fun fallbackProfile(uid: String, auth: com.google.firebase.auth.FirebaseUser?): UserProfileStats {
        val nombre = when {
            !auth?.displayName.isNullOrBlank() -> auth?.displayName?.trim().orEmpty()
            !auth?.email.isNullOrBlank() -> auth?.email
                ?.substringBefore("@")
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty()
            else -> "Usuario"
        }
        return UserProfileStats(
            uid = uid,
            nombre = nombre,
            email = auth?.email.orEmpty(),
            puntos = 0,
            lecciones = 0,
            rachaActual = 0,
            mejorRacha = 0,
            nivel = 1,
            puntosNivelActual = 0,
            puntosSiguienteNivel = 100
        )
    }

    /**
     * Observa en tiempo real los datos de perfil del usuario autenticado.
     */
    fun observeUserProfileStats(uid: String): Flow<Result<UserProfileStats>> = callbackFlow {
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            trySend(Result.failure(SecurityException("Usuario no autenticado o UID no coincide")))
            close()
            return@callbackFlow
        }

        val auth = FirebaseAuth.getInstance().currentUser
        val userRef = FirebaseDatabase.getInstance().getReference("users/$uid")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        trySend(Result.success(fallbackProfile(uid, auth)))
                        return
                    }

                    val map = snapshot.value as? Map<String, Any?> ?: emptyMap()
                    val puntos = (map["puntos"] as? Long)?.toInt() ?: 0
                    val lecciones = (map["lecciones_completadas"] as? Long)?.toInt() ?: 0
                    val rachaActual = (map["racha_actual"] as? Long)?.toInt() ?: 0
                    val mejorRachaDb = (map["mejor_racha"] as? Long)?.toInt() ?: 0
                    val mejorRacha = maxOf(mejorRachaDb, rachaActual)

                    val nombreDb = (map["nombre"] as? String).orEmpty().trim()
                    val nombre = when {
                        nombreDb.isNotEmpty() -> nombreDb
                        !auth?.displayName.isNullOrBlank() -> auth?.displayName?.trim().orEmpty()
                        !auth?.email.isNullOrBlank() -> auth?.email
                            ?.substringBefore("@")
                            ?.replaceFirstChar { it.uppercase() }
                            .orEmpty()
                        else -> "Usuario"
                    }

                    val email = auth?.email.orEmpty()
                    val nivel = (puntos / 100) + 1
                    val puntosNivelActual = puntos % 100
                    val puntosSiguienteNivel = 100

                    trySend(
                        Result.success(
                            UserProfileStats(
                                uid = uid,
                                nombre = nombre,
                                email = email,
                                puntos = puntos,
                                lecciones = lecciones,
                                rachaActual = rachaActual,
                                mejorRacha = mejorRacha,
                                nivel = nivel,
                                puntosNivelActual = puntosNivelActual,
                                puntosSiguienteNivel = puntosSiguienteNivel
                            )
                        )
                    )
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    Log.w(TAG, "Permiso denegado leyendo users/$uid. Se usa fallback local.")
                    trySend(Result.success(fallbackProfile(uid, auth)))
                } else {
                    trySend(Result.failure(error.toException()))
                }
            }
        }

        userRef.addValueEventListener(listener)
        awaitClose { userRef.removeEventListener(listener) }
    }

    suspend fun fetchUserStats(uid: String): Result<UserStats> {
        // Verificar que el uid coincide con el usuario autenticado
        val currentUid = getAuthenticatedUid()
        if (currentUid != uid) {
            return Result.failure(SecurityException("Usuario no autenticado o UID no coincide"))
        }
        return try {
            val db = FirebaseDatabase.getInstance()
            val snap = db.getReference("users/$uid").get().await()
            val map = snap.value as? Map<String, Any?> ?: emptyMap()
            val puntos = (map["puntos"] as? Long)?.toInt() ?: 0
            val lecciones = (map["lecciones_completadas"] as? Long)?.toInt() ?: 0
            val racha = (map["racha_actual"] as? Long)?.toInt() ?: 0
            Result.success(UserStats(puntos, lecciones, racha))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


