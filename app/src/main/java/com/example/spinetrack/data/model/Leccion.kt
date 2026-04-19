package com.example.spinetrack.data.model

data class Leccion(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val nivel: NivelLeccion,
    val duracionMin: Int,
    val puntos: Int,
    val categoria: CategoriaLeccion,
    val completada: Boolean = false
)

enum class NivelLeccion(val label: String) {
    PRINCIPIANTE("Principiante"),
    INTERMEDIO("Intermedio"),
    AVANZADO("Avanzado")
}

enum class CategoriaLeccion {
    LECCIONES,
    EJERCICIOS,
    ERGONOMIA,
    HABITOS
}