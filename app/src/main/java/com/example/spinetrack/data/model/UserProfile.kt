package com.example.spinetrack.data.model

data class UserProfile(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val puntosTotales: Int = 0
)

{
    companion object {
        fun fromMap(uid: String, map: Map<String, Any>): UserProfile {
            val puntos = when (val v = map["puntos_totales"] ?: map["puntosTotales"]) {
                is Int -> v
                is Long -> v.toInt()
                is Double -> v.toInt()
                else -> 0
            }
            return UserProfile(
                uid = uid,
                nombre = map["nombre"] as? String ?: map["name"] as? String ?: "",
                email = map["email"] as? String ?: "",
                photoUrl = map["photo_url"] as? String ?: map["photoUrl"] as? String,
                puntosTotales = puntos
            )
        }
    }
}

