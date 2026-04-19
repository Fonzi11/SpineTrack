package com.example.spinetrack.data.model

data class SesionPostural(
    val sessionId: String = "",
    val uid: String = "",
    val nombre: String = "",
    val tsInicio: String = "",
    val tsFin: String = "",
    val duracionMin: Double = 0.0,
    val tBuenaMin: Double = 0.0,
    val tMalaMin: Double = 0.0,
    val pctBuena: Double = 0.0,
    val pctMala: Double = 0.0,
    val thetaPromedio: Double = 0.0,
    val numAlertas: Int = 0,
    val numCorrecciones: Int = 0,
    val icp: Double = 0.0,
    val claseIcp: String = "",
    val distAnguloPct: Map<String, Double> = emptyMap(),
    val tempPromC: Double? = null,
    val nMuestras: Int = 0,
    val eventos: List<EventoPostural> = emptyList()
) {
    // Convierte el mapa de Firebase (snake_case) a este objeto
    companion object {
        fun fromMap(map: Map<String, Any>): SesionPostural {
            @Suppress("UNCHECKED_CAST")
            val eventosRaw = map["eventos"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val distRaw = map["dist_angulo_pct"] as? Map<String, Any> ?: emptyMap()

            return SesionPostural(
                sessionId     = map["session_id"] as? String ?: "",
                uid           = map["uid"] as? String ?: "",
                nombre        = map["nombre"] as? String ?: "",
                tsInicio      = map["ts_inicio"] as? String ?: "",
                tsFin         = map["ts_fin"] as? String ?: "",
                duracionMin   = (map["duracion_min"] as? Number)?.toDouble() ?: 0.0,
                tBuenaMin     = (map["t_buena_min"] as? Number)?.toDouble() ?: 0.0,
                tMalaMin      = (map["t_mala_min"] as? Number)?.toDouble() ?: 0.0,
                pctBuena      = (map["pct_buena"] as? Number)?.toDouble() ?: 0.0,
                pctMala       = (map["pct_mala"] as? Number)?.toDouble() ?: 0.0,
                thetaPromedio = (map["theta_promedio"] as? Number)?.toDouble() ?: 0.0,
                numAlertas    = (map["num_alertas"] as? Number)?.toInt() ?: 0,
                numCorrecciones = (map["num_correcciones"] as? Number)?.toInt() ?: 0,
                icp           = (map["icp"] as? Number)?.toDouble() ?: 0.0,
                claseIcp      = map["clase_icp"] as? String ?: "",
                distAnguloPct = distRaw.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 },
                tempPromC     = (map["temp_prom_c"] as? Number)?.toDouble(),
                nMuestras     = (map["n_muestras"] as? Number)?.toInt() ?: 0,
                eventos       = eventosRaw.map { EventoPostural.fromMap(it) }
            )
        }
    }
}