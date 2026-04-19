package com.example.spinetrack.data.model

/**
 * Modelo que representa el estado del dispositivo reportado en /dispositivos/{uid}/estado
 */
data class EstadoDispositivo(
    val activo: Boolean = false,
    val calibrado: Boolean = false,
    val calibrando: Boolean = false,
    val sesionActiva: Boolean = false,
    val tsUltimo: Long = 0L
) {
    companion object {
        fun fromMap(map: Map<String, Any?>?): EstadoDispositivo {
            if (map == null) return EstadoDispositivo()
            val activo = map["activo"] as? Boolean ?: (map["activo"] as? Long)?.let { it != 0L } ?: false
            val calibrado = map["calibrado"] as? Boolean ?: (map["calibrado"] as? Long)?.let { it != 0L } ?: false
            val calibrando = map["calibrando"] as? Boolean ?: (map["calibrando"] as? Long)?.let { it != 0L } ?: false
            val sesion = map["sesion_activa"] as? Boolean ?: (map["sesion_activa"] as? Long)?.let { it != 0L } ?: false
            val ts = when (val v = map["ts_ultimo"]) {
                is Long -> v
                is Int -> v.toLong()
                is Double -> v.toLong()
                is String -> v.toLongOrNull() ?: 0L
                else -> 0L
            }
            return EstadoDispositivo(
                activo = activo,
                calibrado = calibrado,
                calibrando = calibrando,
                sesionActiva = sesion,
                tsUltimo = ts
            )
        }
    }
}

