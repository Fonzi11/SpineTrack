package com.example.spinetrack.data.model

/**
 * Modelo de datos del usuario.
 * Contiene toda la información relevante del usuario de la aplicación.
 */
data class User(
    val id: String,
    val nombre: String,
    val email: String,
    val photoUrl: String? = null,
    val nivel: Int = 1,
    val puntosTotales: Int = 0,
    val rachaActual: Int = 0,
    val mejorRacha: Int = 0,
    val leccionesCompletadas: Int = 0
) {
    /**
     * Calcula el progreso hacia el siguiente nivel.
     * Cada nivel requiere 1000 puntos.
     */
    fun getProgresoNivel(): Int = puntosTotales % 1000

    /**
     * Calcula el porcentaje de progreso del nivel actual.
     */
    fun getPorcentajeNivel(): Int = (getProgresoNivel() * 100) / 1000

    /**
     * Calcula los puntos necesarios para subir de nivel.
     */
    fun getPuntosParaSiguienteNivel(): Int = 1000 - getProgresoNivel()
}