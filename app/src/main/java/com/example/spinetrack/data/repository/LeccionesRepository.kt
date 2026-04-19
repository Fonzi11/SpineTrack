package com.example.spinetrack.data.repository

import com.example.spinetrack.data.model.CategoriaLeccion
import com.example.spinetrack.data.model.Leccion
import com.example.spinetrack.data.model.NivelLeccion

object LeccionesRepository {

    // Devuelve HTML para mostrar en el tutorial/ejercicio. Si no existe HTML personalizado,
    // genera una plantilla simple a partir de los datos de la lección.
    fun getHtmlContent(leccionId: Int): String {
        val leccion = getLeccionById(leccionId) ?: return "<html><body><p>Contenido no disponible</p></body></html>"
        val tipo = when (leccion.categoria) {
            CategoriaLeccion.EJERCICIOS -> "Ejercicio"
            CategoriaLeccion.HABITOS -> "Hábito"
            CategoriaLeccion.ERGONOMIA -> "Ergonomía"
            else -> "Lección"
        }

        // Plantilla HTML básica
        return """
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                body { font-family: sans-serif; color: #222; padding:16px }
                h1 { color: #FF8A65 }
                .meta { color:#666; font-size:14px; margin-bottom:12px }
                .section { margin-top:12px }
                .steps { margin-left:18px }
              </style>
            </head>
            <body>
              <h1>${escapeHtml(leccion.titulo)}</h1>
              <div class="meta">Tipo: $tipo • Nivel: ${escapeHtml(leccion.nivel.label)} • Duración: ${leccion.duracionMin} min</div>
              <div class="section"><strong>Descripción</strong><p>${escapeHtml(leccion.descripcion)}</p></div>
              ${if (leccion.categoria == CategoriaLeccion.EJERCICIOS)
                "<div class=\"section\"><strong>Instrucciones</strong><ol class=\"steps\"><li>Realiza una breve entrada en calor (30s)</li><li>Sigue los movimientos descritos en la lección</li><li>Repite según tu nivel</li></ol></div>"
                else ""}
              <div class="section"><em>¡Sigue las indicaciones con cuidado y adapta según tu condición física!</em></div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    /**
     * Devuelve la lista de lecciones para la categoría, marcando como completadas
     * aquellas cuyo id está presente en el store local.
     */
    fun getLeccionesWithCompletion(context: android.content.Context, categoria: CategoriaLeccion): List<Leccion> {
        val completed = com.example.spinetrack.data.local.CompletedLessonsStore.getCompletedIds(context)
        return when (categoria) {
            CategoriaLeccion.LECCIONES  -> lecciones.map { it.copy(completada = completed.contains(it.id)) }
            CategoriaLeccion.EJERCICIOS -> ejercicios.map { it.copy(completada = completed.contains(it.id)) }
            CategoriaLeccion.ERGONOMIA  -> ergonomia.map { it.copy(completada = completed.contains(it.id)) }
            CategoriaLeccion.HABITOS    -> habitos.map { it.copy(completada = completed.contains(it.id)) }
        }
    }

    fun getLecciones(categoria: CategoriaLeccion): List<Leccion> = when (categoria) {
        CategoriaLeccion.LECCIONES  -> lecciones
        CategoriaLeccion.EJERCICIOS -> ejercicios
        CategoriaLeccion.ERGONOMIA  -> ergonomia
        CategoriaLeccion.HABITOS    -> habitos
    }

    fun getLeccionById(id: Int): Leccion? {
        return (lecciones + ejercicios + ergonomia + habitos).firstOrNull { it.id == id }
    }

    private val lecciones = listOf(
        Leccion(1, "¿Qué es la buena postura?",
            "Descubre por qué la postura importa y cómo afecta tu salud",
            NivelLeccion.PRINCIPIANTE, 5, 15, CategoriaLeccion.LECCIONES),
        Leccion(2, "Anatomía de la columna vertebral",
            "Conoce las partes de tu columna y cómo mantenerla sana",
            NivelLeccion.PRINCIPIANTE, 5, 15, CategoriaLeccion.LECCIONES),
        Leccion(3, "Postura al estar sentado",
            "Aprende la posición correcta para trabajar sentado",
            NivelLeccion.PRINCIPIANTE, 8, 20, CategoriaLeccion.LECCIONES),
        Leccion(4, "El cuello y la posición de la cabeza",
            "Evita la tensión cervical con técnicas simples",
            NivelLeccion.INTERMEDIO, 6, 25, CategoriaLeccion.LECCIONES),
        Leccion(5, "Hombros relajados: cómo lograrlo",
            "Técnicas para liberar tensión en hombros y trapecios",
            NivelLeccion.INTERMEDIO, 7, 25, CategoriaLeccion.LECCIONES),
        Leccion(6, "Postura y respiración",
            "Cómo una buena postura mejora tu capacidad respiratoria",
            NivelLeccion.AVANZADO, 10, 35, CategoriaLeccion.LECCIONES),
        Leccion(7, "ICP: tu Índice de Calidad Postural",
            "Entiende tu puntaje y cómo mejorarlo cada día",
            NivelLeccion.AVANZADO, 8, 30, CategoriaLeccion.LECCIONES),
    )

    private val ejercicios = listOf(
        Leccion(101, "Estiramiento de cuello (1 min)",
            "Alivia la tensión cervical con 4 movimientos básicos",
            NivelLeccion.PRINCIPIANTE, 1, 10, CategoriaLeccion.EJERCICIOS),
        Leccion(102, "Apertura de pecho",
            "Contrarresta el encorvamiento con este ejercicio",
            NivelLeccion.PRINCIPIANTE, 3, 15, CategoriaLeccion.EJERCICIOS),
        Leccion(103, "Rotaciones de hombros",
            "Activa y relaja la zona escapular en 2 minutos",
            NivelLeccion.PRINCIPIANTE, 2, 10, CategoriaLeccion.EJERCICIOS),
        Leccion(104, "Plancha de espalda",
            "Fortalece el core para sostener mejor tu columna",
            NivelLeccion.INTERMEDIO, 5, 20, CategoriaLeccion.EJERCICIOS),
        Leccion(105, "Estiramiento lumbar en silla",
            "Ejercicio de movilidad sin levantarte del escritorio",
            NivelLeccion.INTERMEDIO, 4, 20, CategoriaLeccion.EJERCICIOS),
        Leccion(106, "Yoga de escritorio (rutina completa)",
            "Secuencia de 10 minutos para trabajadores remotos",
            NivelLeccion.AVANZADO, 10, 40, CategoriaLeccion.EJERCICIOS),
    )

    private val ergonomia = listOf(
        Leccion(201, "Altura ideal del monitor",
            "Ajusta tu pantalla para eliminar tensión en cuello y ojos",
            NivelLeccion.PRINCIPIANTE, 4, 15, CategoriaLeccion.ERGONOMIA),
        Leccion(202, "Configuración de tu silla",
            "Altura, apoyabrazos y soporte lumbar: guía completa",
            NivelLeccion.PRINCIPIANTE, 5, 15, CategoriaLeccion.ERGONOMIA),
        Leccion(203, "Posición del teclado y mouse",
            "Evita el síndrome del túnel carpiano con esta config",
            NivelLeccion.INTERMEDIO, 5, 20, CategoriaLeccion.ERGONOMIA),
        Leccion(204, "Iluminación y fatiga visual",
            "Cómo la luz de tu espacio afecta tu postura",
            NivelLeccion.INTERMEDIO, 6, 20, CategoriaLeccion.ERGONOMIA),
        Leccion(205, "Escritorio de pie: pros y contras",
            "¿Vale la pena? Cuándo y cómo usarlo correctamente",
            NivelLeccion.AVANZADO, 8, 30, CategoriaLeccion.ERGONOMIA),
    )

    private val habitos = listOf(
        Leccion(301, "La regla 20-20-20",
            "Descansos visuales y posturales cada 20 minutos",
            NivelLeccion.PRINCIPIANTE, 3, 10, CategoriaLeccion.HABITOS),
        Leccion(302, "Construye tu racha postural",
            "Estrategias para mantener buenos hábitos semana tras semana",
            NivelLeccion.PRINCIPIANTE, 5, 15, CategoriaLeccion.HABITOS),
        Leccion(303, "Alarmas y recordatorios inteligentes",
            "Configura SpineTrack para que te ayude sin interrumpirte",
            NivelLeccion.INTERMEDIO, 4, 15, CategoriaLeccion.HABITOS),
        Leccion(304, "Hidratación y postura",
            "La conexión entre tomar agua y moverte regularmente",
            NivelLeccion.INTERMEDIO, 5, 20, CategoriaLeccion.HABITOS),
        Leccion(305, "Postura fuera del escritorio",
            "Cómo caminar, cargar el bolso y usar el celular correctamente",
            NivelLeccion.AVANZADO, 7, 30, CategoriaLeccion.HABITOS),
    )
}