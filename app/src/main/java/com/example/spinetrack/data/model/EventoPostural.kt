package com.example.spinetrack.data.model

data class EventoPostural(
    val tsRelS: Double = 0.0,
    val timestamp: String = "",
    val tipo: String = "",        // "ALERTA" o "CORRECCION"
    val thetaAbs: Double = 0.0,
    val pitchDev: Double = 0.0,
    val rollDev: Double = 0.0,
    val clase: String = ""
) {
    val esAlerta: Boolean get() = tipo == "ALERTA"
    val esCorreccion: Boolean get() = tipo == "CORRECCION"

    companion object {
        fun fromMap(map: Map<String, Any>): EventoPostural {
            return EventoPostural(
                tsRelS    = (map["ts_rel_s"] as? Number)?.toDouble() ?: 0.0,
                timestamp = map["timestamp"] as? String ?: "",
                tipo      = map["tipo"] as? String ?: "",
                thetaAbs  = (map["theta_abs"] as? Number)?.toDouble() ?: 0.0,
                pitchDev  = (map["pitch_dev"] as? Number)?.toDouble() ?: 0.0,
                rollDev   = (map["roll_dev"] as? Number)?.toDouble() ?: 0.0,
                clase     = map["clase"] as? String ?: ""
            )
        }
    }
}