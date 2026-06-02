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

        val objetivo = objetivoPorLeccion[leccionId] ?: "Mejorar la higiene postural de forma progresiva y segura."
        val contenido = when (leccion.categoria) {
            CategoriaLeccion.EJERCICIOS -> renderEjercicio(leccionId)
            CategoriaLeccion.HABITOS -> renderHabito(leccionId)
            CategoriaLeccion.ERGONOMIA, CategoriaLeccion.LECCIONES -> renderLeccionTeorica(leccionId)
        }

        return """
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                body { font-family: sans-serif; color: #222; padding:16px; line-height:1.45; background:#fffdf9; }
                h1 { color: #FF8A65; margin-bottom:6px; }
                h2 { color: #7a5c4a; font-size:18px; margin:16px 0 8px; }
                .meta { color:#666; font-size:14px; margin-bottom:12px }
                .section { margin-top:12px }
                .box { background:#fff3ea; border-radius:12px; padding:10px 12px; margin-top:8px; }
                .steps { margin-left:18px; padding-left:6px; }
                .steps li { margin-bottom:6px; }
                .tip { margin-top:10px; color:#5f6368; }
              </style>
            </head>
            <body>
              <h1>${escapeHtml(leccion.titulo)}</h1>
              <div class="meta">Tipo: $tipo • Nivel: ${escapeHtml(leccion.nivel.label)} • Duración: ${leccion.duracionMin} min</div>
              <div class="section"><strong>Objetivo</strong><div class="box">${escapeHtml(objetivo)}</div></div>
              <div class="section"><strong>Descripción</strong><p>${escapeHtml(leccion.descripcion)}</p></div>
              $contenido
              <div class="tip"><em>Recomendación: mantén respiración controlada y detente si presentas dolor.</em></div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderLeccionTeorica(leccionId: Int): String {
        val puntos = when (leccionId) {
            1 -> listOf("Mantén orejas alineadas con hombros", "Evita encorvar la zona torácica", "Apoya ambos pies en el suelo")
            2 -> listOf("Curva cervical y lumbar deben conservarse", "Evita posturas sostenidas por más de 45 minutos", "Activa core para descargar columna")
            3 -> listOf("Pantalla a la altura de los ojos", "Codos cerca de 90°", "Apoyo lumbar firme")
            4 -> listOf("Evita adelantamiento de cabeza", "Realiza retracción cervical suave", "Descansa cuello cada 30 minutos")
            5 -> listOf("Hombros lejos de las orejas", "Evita tensión al escribir", "Incluye pausas de movilidad escapular")
            6 -> listOf("Respira diafragmáticamente", "Expansión costal sin elevar hombros", "Coordina postura con respiración")
            7 -> listOf("ICP alto indica mejor higiene postural", "Observa tendencia semanal, no solo un valor", "Corrige hábitos antes de aumentar exigencia")
            201 -> listOf("Centro del monitor al nivel ocular", "Distancia entre 50 y 70 cm", "Evita reflejos directos")
            202 -> listOf("Altura de silla para pies apoyados", "Rodillas cercanas a 90°", "Soporte lumbar en zona baja")
            203 -> listOf("Teclado cerca del borde", "Muñecas neutras", "Mouse alineado al hombro")
            204 -> listOf("Luz ambiental homogénea", "Evita contraste extremo", "Descansos visuales frecuentes")
            205 -> listOf("Alterna sentado/de pie", "No bloquees rodillas", "Usa superficie estable")
            else -> listOf("Define una meta semanal", "Registra progreso", "Ajusta entorno de trabajo")
        }
        return """
            <h2>Puntos clave</h2>
            <ol class="steps">
                ${puntos.joinToString("\n") { "<li>${escapeHtml(it)}</li>" }}
            </ol>
            <h2>Aplicación práctica</h2>
            <div class="box">Implementa al menos 2 puntos clave hoy y revisa tu postura cada 30 minutos.</div>
        """.trimIndent()
    }

    private fun renderEjercicio(leccionId: Int): String {
        val pasos = when (leccionId) {
            101 -> listOf("Inclina cuello lateralmente 15 segundos por lado", "Flexiona y extiende cuello suavemente", "Realiza 2 repeticiones por movimiento")
            102 -> listOf("Entrecruza manos detrás de la espalda", "Abre pecho sin arquear lumbar", "Mantén 20 segundos y repite 3 veces")
            103 -> listOf("Rota hombros hacia atrás en círculos", "Haz 10 repeticiones lentas", "Cambia sentido y repite")
            104 -> listOf("Activa abdomen y glúteos", "Mantén columna neutra", "Sostén 20-40 segundos según nivel")
            105 -> listOf("Sentado al borde de la silla", "Inclina tronco adelante con espalda recta", "Mantén 20 segundos y respira profundo")
            106 -> listOf("Movilidad cervical y torácica 2 minutos", "Secuencia de apertura de cadera", "Cierre con respiración guiada")
            else -> listOf("Calentamiento breve", "Ejecución controlada", "Vuelta a la calma")
        }
        return """
            <h2>Instrucciones paso a paso</h2>
            <ol class="steps">
                ${pasos.joinToString("\n") { "<li>${escapeHtml(it)}</li>" }}
            </ol>
            <h2>Dosificación</h2>
            <div class="box">Realiza la rutina durante ${getLeccionById(leccionId)?.duracionMin ?: 3} minutos. Si aparece dolor agudo, detén el ejercicio.</div>
        """.trimIndent()
    }

    private fun renderHabito(leccionId: Int): String {
        val acciones = when (leccionId) {
            301 -> listOf("Cada 20 minutos mira a 20 pies de distancia por 20 segundos", "Aprovecha la pausa para corregir postura", "Configura recordatorio automático")
            302 -> listOf("Define meta de días consecutivos", "Asocia hábito a una rutina diaria", "Registra cumplimiento antes de dormir")
            303 -> listOf("Usa alarmas cada 30-45 minutos", "Evita notificaciones excesivas", "Adapta frecuencia según jornada")
            304 -> listOf("Toma agua en intervalos regulares", "Levántate al recargar botella", "Relaciona hidratación con micro-pausas")
            305 -> listOf("Sostén el celular a nivel visual", "Alterna hombro al cargar bolso", "Camina con mirada al frente")
            else -> listOf("Define señal de inicio", "Ejecuta acción corta", "Recompensa consistencia")
        }
        return """
            <h2>Acciones recomendadas</h2>
            <ol class="steps">
                ${acciones.joinToString("\n") { "<li>${escapeHtml(it)}</li>" }}
            </ol>
            <h2>Checklist diario</h2>
            <div class="box">Marca esta actividad como completada al finalizar para reforzar la racha y sincronizar puntos.</div>
        """.trimIndent()
    }

    private val objetivoPorLeccion = mapOf(
        1 to "Entender los principios base de una postura saludable.",
        2 to "Reconocer la anatomía funcional de la columna vertebral.",
        3 to "Configurar una postura sentada ergonómica para estudio/trabajo.",
        4 to "Reducir carga cervical y mejorar alineación de cabeza.",
        5 to "Disminuir tensión de hombros durante actividades prolongadas.",
        6 to "Integrar respiración y estabilidad postural.",
        7 to "Interpretar el ICP y usarlo para mejorar hábitos.",
        101 to "Liberar tensión cervical en una rutina corta.",
        102 to "Mejorar apertura torácica y reducir encorvamiento.",
        103 to "Activar cintura escapular y movilidad de hombros.",
        104 to "Fortalecer core para sostener la columna.",
        105 to "Mejorar movilidad lumbar en puesto de escritorio.",
        106 to "Realizar rutina completa de movilidad consciente.",
        201 to "Optimizar altura y distancia del monitor.",
        202 to "Ajustar correctamente silla y soporte lumbar.",
        203 to "Alinear teclado/mouse para prevenir sobrecarga.",
        204 to "Reducir fatiga visual por iluminación inadecuada.",
        205 to "Aplicar uso seguro y progresivo del escritorio de pie.",
        301 to "Aplicar pausas visuales y posturales periódicas.",
        302 to "Construir consistencia en hábitos posturales.",
        303 to "Automatizar recordatorios sin perder foco.",
        304 to "Vincular hidratación con movimiento saludable.",
        305 to "Extender buena postura fuera del escritorio."
    )

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