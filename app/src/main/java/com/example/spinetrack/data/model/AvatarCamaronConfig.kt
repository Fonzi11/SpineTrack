package com.example.spinetrack.data.model

/**
 * Configuracion de avatar tipo camaron para personalizacion visual.
 */
data class AvatarCamaronConfig(
    val colorKey: String = AvatarCamaronDefaults.DEFAULT_COLOR_KEY,
    val accesorioKey: String = AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY,
    val sizeSp: Int = AvatarCamaronDefaults.DEFAULT_SIZE_SP
)

/**
 * Valores por defecto y claves base del avatar.
 */
object AvatarCamaronDefaults {
    const val DEFAULT_COLOR_KEY = "coral"
    const val DEFAULT_ACCESSORY_KEY = "none"
    const val DEFAULT_SIZE_SP = 26
}
