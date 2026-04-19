package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.RankingUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

object RankingRepository {

    suspend fun fetchRanking(): Result<List<RankingUser>> {
        return try {
            val db = FirebaseDatabase.getInstance()
            val snap = db.getReference("ranking").get().await()
            val list = snap.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val map = child.value as? Map<String, Any?> ?: return@mapNotNull null
                val nombre = map["nombre"] as? String ?: uid
                val puntos = (map["puntos"] as? Long)?.toInt() ?: 0
                val racha = (map["racha"] as? Long)?.toInt() ?: 0
                val lecciones = (map["lecciones"] as? Long)?.toInt() ?: 0
                RankingUser(uid.hashCode(), nombre, puntos, racha, lecciones)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

