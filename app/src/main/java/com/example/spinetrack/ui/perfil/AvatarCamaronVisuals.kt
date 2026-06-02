package com.example.spinetrack.ui.perfil

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.example.spinetrack.R
import com.example.spinetrack.data.model.AvatarCamaronDefaults

/**
 * Utilidades de render para el avatar de camaron.
 * Centraliza colores y composicion visual para mantener consistencia entre pantallas.
 */
object AvatarCamaronVisuals {

    /**
     * Opcion de color disponible para el avatar.
     */
    data class ColorOption(
        val key: String,
        @StringRes val labelRes: Int,
        @ColorRes val colorRes: Int
    )

    /**
     * Opcion de accesorio disponible para el avatar.
     */
    data class AccessoryOption(
        val key: String,
        @StringRes val labelRes: Int,
        val spec: AccessorySpec
    )

    /**
     * Especificacion visual del accesorio al componer el avatar.
     */
    data class AccessorySpec(
        val prefix: String = "",
        val suffix: String = "",
        val top: Boolean = false,
        val bottom: Boolean = false,
        val scale: Float = 0.86f
    )

    val colorOptions: List<ColorOption> = listOf(
        ColorOption(AvatarCamaronDefaults.DEFAULT_COLOR_KEY, R.string.avatar_color_coral, R.color.peach_primary),
        ColorOption("menta", R.string.avatar_color_menta, R.color.good_green),
        ColorOption("mar", R.string.avatar_color_mar, R.color.peach_dark),
        ColorOption("lavanda", R.string.avatar_color_lavanda, R.color.pink_primary),
        ColorOption("sol", R.string.avatar_color_sol, R.color.warning_yellow),
        ColorOption("noche", R.string.avatar_color_noche, R.color.excellent_blue)
    )

    val accessoryOptions: List<AccessoryOption> = listOf(
        AccessoryOption(AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY, R.string.avatar_acc_none, AccessorySpec()),
        AccessoryOption("gafas", R.string.avatar_acc_gafas, AccessorySpec(suffix = "🥽", scale = 0.82f)),
        AccessoryOption("corona", R.string.avatar_acc_corona, AccessorySpec(prefix = "👑", top = true, scale = 0.9f)),
        AccessoryOption("estrella", R.string.avatar_acc_estrella, AccessorySpec(prefix = "✨", top = true, scale = 0.82f)),
        AccessoryOption("lazo", R.string.avatar_acc_lazo, AccessorySpec(prefix = "🎀", top = true, scale = 0.86f)),
        AccessoryOption("audifonos", R.string.avatar_acc_audifonos, AccessorySpec(prefix = "🎧", scale = 0.88f)),
        AccessoryOption("bigote", R.string.avatar_acc_bigote, AccessorySpec(suffix = "〰", bottom = true, scale = 0.82f)),
        AccessoryOption("sombrero", R.string.avatar_acc_sombrero, AccessorySpec(prefix = "🎩", top = true, scale = 0.86f)),
        AccessoryOption("flor", R.string.avatar_acc_flor, AccessorySpec(prefix = "🌺", top = true, scale = 0.86f)),
        AccessoryOption("medalla", R.string.avatar_acc_medalla, AccessorySpec(suffix = "🏅", bottom = true, scale = 0.82f))
    )

    fun normalizeColorKey(colorKey: String): String {
        return colorOptions.firstOrNull { it.key == colorKey }?.key
            ?: AvatarCamaronDefaults.DEFAULT_COLOR_KEY
    }

    /**
     * Normaliza una clave de accesorio al catalogo actual.
     */
    fun normalizeAccessoryKey(accesorioKey: String): String {
        return accessoryOptions.firstOrNull { it.key == accesorioKey }?.key
            ?: AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY
    }

    /**
     * Resuelve el drawable asociado a un accesorio.
     */
    @DrawableRes
    fun accessoryDrawableRes(accesorioKey: String): Int {
        return when (normalizeAccessoryKey(accesorioKey)) {
            "gafas" -> R.drawable.avatar_acc_gafas
            "corona" -> R.drawable.avatar_acc_corona
            "estrella" -> R.drawable.avatar_acc_estrella
            "lazo" -> R.drawable.avatar_acc_lazo
            "audifonos" -> R.drawable.avatar_acc_audifonos
            "bigote" -> R.drawable.avatar_acc_bigote
            "sombrero" -> R.drawable.avatar_acc_sombrero
            "flor" -> R.drawable.avatar_acc_flor
            "medalla" -> R.drawable.avatar_acc_medalla
            else -> R.drawable.avatar_acc_none
        }
    }

    /**
     * Devuelve el color de fondo asociado a la clave seleccionada.
     */
    @ColorRes
    fun colorRes(colorKey: String): Int {
        val resolved = normalizeColorKey(colorKey)
        return colorOptions.firstOrNull { it.key == resolved }?.colorRes
            ?: R.color.peach_primary
    }

    /**
     * Compone el avatar usando el logo de camaron y agrega accesorios alrededor.
     */
    fun composeAvatar(context: Context, accesorioKey: String, sizeSp: Int): CharSequence {
        val resolvedAccessory = normalizeAccessoryKey(accesorioKey)
        val spec = accessoryOptions.firstOrNull { it.key == resolvedAccessory }?.spec ?: AccessorySpec()

        val marker = "*"
        val raw = "${spec.prefix}$marker${spec.suffix}"
        val builder = SpannableStringBuilder(raw)
        val markerIndex = spec.prefix.length

        val drawable = AppCompatResources.getDrawable(context, R.mipmap.ic_avatar_camaron_foreground)
            ?: AppCompatResources.getDrawable(context, R.mipmap.ic_avatar_camaron)
        if (drawable == null) {
            builder.replace(markerIndex, markerIndex + 1, "🦐")
            return builder
        }

        val baseIconPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp.toFloat(),
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(32)
        val iconPx = (baseIconPx * 1.28f).toInt().coerceAtLeast(44)

        drawable.setBounds(0, 0, iconPx, iconPx)
        builder.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            markerIndex,
            markerIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        applyAccessorySpans(builder, spec, markerIndex)
        return builder
    }

    private fun applyAccessorySpans(
        builder: SpannableStringBuilder,
        spec: AccessorySpec,
        markerIndex: Int
    ) {
        if (spec.prefix.isNotEmpty()) {
            builder.setSpan(
                RelativeSizeSpan(spec.scale),
                0,
                markerIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (spec.top) {
                builder.setSpan(
                    SuperscriptSpan(),
                    0,
                    markerIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (spec.suffix.isNotEmpty()) {
            val start = markerIndex + 1
            val end = builder.length
            builder.setSpan(
                RelativeSizeSpan(spec.scale),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (spec.bottom) {
                builder.setSpan(
                    SubscriptSpan(),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
