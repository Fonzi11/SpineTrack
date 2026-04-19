package com.example.spinetrack.data.model

data class RealtimePostura(
    val uid: String = "",
    val sessionId: String = "",
    val ts: String = "",
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val pitchDev: Double = 0.0,
    val rollDev: Double = 0.0,
    val thetaAbs: Double = 0.0,
    val buenaPostura: Boolean = true,
    val claseAngulo: String = "",
    val icpParcial: Double = 100.0,
    val claseIcp: String = "",
    val tBuenaMin: Double = 0.0,
    val tMalaMin: Double = 0.0,
    val numAlertas: Int = 0,
    val tempC: Double? = null
) {
    companion object {
        private fun Map<String, Any>.str(vararg keys: String): String {
            for (k in keys) {
                val v = this[k]
                if (v is String && v.isNotBlank()) return v
            }
            return ""
        }

        private fun Map<String, Any>.num(vararg keys: String): Double? {
            for (k in keys) {
                val v = this[k]
                when (v) {
                    is Number -> return v.toDouble()
                    is String -> v.toDoubleOrNull()?.let { return it }
                }
            }
            return null
        }

        fun fromMap(map: Map<String, Any>): RealtimePostura {
            val icpRaw = map.num("icp_parcial", "icp", "icpScore") ?: 100.0
            // Soporta payloads en escala 0..1 o 0..100.
            val icpNormalizado = if (icpRaw in 0.0..1.0) icpRaw * 100.0 else icpRaw

            val pitch = map.num("pitch") ?: 0.0
            val roll = map.num("roll") ?: 0.0
            val pitchDev = map.num("pitch_dev", "pitchDev") ?: pitch
            val rollDev = map.num("roll_dev", "rollDev") ?: roll
            val theta = map.num("theta_abs", "thetaAbs") ?: kotlin.math.sqrt(pitchDev * pitchDev + rollDev * rollDev)

            val posturaIncorrecta = when (val v = map["postura_incorrecta"]) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.equals("true", ignoreCase = true) || v == "1"
                else -> false
            }
            val buenaPostura = map["buena_postura"] as? Boolean ?: !posturaIncorrecta

            return RealtimePostura(
                uid          = map.str("uid", "device_id"),
                sessionId    = map.str("session_id", "sessionId"),
                ts           = map.str("ts", "timestamp"),
                pitch        = pitch,
                roll         = roll,
                pitchDev     = pitchDev,
                rollDev      = rollDev,
                thetaAbs     = theta,
                buenaPostura = buenaPostura,
                claseAngulo  = map.str("clase_angulo", "posture", "postureLabel"),
                icpParcial   = icpNormalizado.coerceIn(0.0, 100.0),
                claseIcp     = map.str("clase_icp"),
                tBuenaMin    = map.num("t_buena_min", "tBuenaMin") ?: 0.0,
                tMalaMin     = map.num("t_mala_min", "tMalaMin") ?: 0.0,
                numAlertas   = (map.num("num_alertas", "numAlertas") ?: 0.0).toInt(),
                tempC        = map.num("temp_c", "tempC")
            )
        }
    }
}