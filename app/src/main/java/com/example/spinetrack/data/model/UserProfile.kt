package com.example.spinetrack.data.model

import com.example.spinetrack.data.model.AvatarCamaronConfig
import com.example.spinetrack.data.model.AvatarCamaronDefaults

data class UserProfile(
    val uid: String = "",
    val nombre: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val puntosTotales: Int = 0,
    val leccionesCompletadas: Int = 0,
    val rachaActual: Int = 0,
    val nivel: Int = 1,
    val avatarConfig: AvatarCamaronConfig? = null
)

{
    companion object {
        fun fromMap(uid: String, map: Map<String, Any>): UserProfile {
            fun toInt(value: Any?): Int = when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Double -> value.toInt()
                else -> 0
            }

            val puntos = toInt(map["puntos"] ?: map["puntos_totales"] ?: map["puntosTotales"])
            val lecciones = toInt(map["lecciones_completadas"] ?: map["lecciones"])
            val racha = toInt(map["racha_actual"] ?: map["racha"])
            val nivel = toInt(map["nivel"]).coerceAtLeast(1)

            @Suppress("UNCHECKED_CAST")
            val avatarMap = map["avatar_config"] as? Map<String, Any?>
            val avatarConfig = avatarMap?.let { config ->
                AvatarCamaronConfig(
                    colorKey = config["color"] as? String ?: AvatarCamaronDefaults.DEFAULT_COLOR_KEY,
                    accesorioKey = config["accesorio"] as? String
                        ?: AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY,
                    sizeSp = when (val value = config["size_sp"]) {
                        is Int -> value
                        is Long -> value.toInt()
                        is Double -> value.toInt()
                        is String -> value.toIntOrNull() ?: AvatarCamaronDefaults.DEFAULT_SIZE_SP
                        else -> AvatarCamaronDefaults.DEFAULT_SIZE_SP
                    }
                )
            }

            return UserProfile(
                uid = uid,
                nombre = map["nombre"] as? String ?: map["name"] as? String ?: "",
                email = map["email"] as? String ?: "",
                photoUrl = map["photo_url"] as? String ?: map["photoUrl"] as? String,
                puntosTotales = puntos,
                leccionesCompletadas = lecciones,
                rachaActual = racha,
                nivel = nivel,
                avatarConfig = avatarConfig
            )
        }
    }
}
