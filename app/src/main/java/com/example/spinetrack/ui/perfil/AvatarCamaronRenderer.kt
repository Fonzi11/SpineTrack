package com.example.spinetrack.ui.perfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.appcompat.content.res.AppCompatResources
import com.example.spinetrack.R
import com.example.spinetrack.data.model.AvatarCamaronConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Renderiza el avatar de camaron usando LayerDrawable y capas intercambiables.
 */
object AvatarCamaronRenderer {

    /**
     * Construye un LayerDrawable con cuerpo, ojos y accesorio para el avatar.
     */
    fun buildLayerDrawable(context: Context, config: AvatarCamaronConfig): LayerDrawable {
        val base = AppCompatResources.getDrawable(context, R.drawable.avatar_camaron_layers) as LayerDrawable
        val body = base.findDrawableByLayerId(R.id.layer_body)
        val bodyColor = ContextCompat.getColor(context, AvatarCamaronVisuals.colorRes(config.colorKey))
        DrawableCompat.setTint(DrawableCompat.wrap(body), bodyColor)

        val accessoryRes = resolveAccessoryDrawable(context, config.accesorioKey)
        val accessory = AppCompatResources.getDrawable(context, accessoryRes)
        if (accessory != null) {
            base.setDrawableByLayerId(R.id.layer_accessory, accessory)
        }
        return base
    }

    /**
     * Aplica el avatar en un ImageView para previsualizacion.
     */
    fun applyToImageView(context: Context, imageView: ImageView, config: AvatarCamaronConfig) {
        imageView.setImageDrawable(buildLayerDrawable(context, config))
    }

    /**
     * Genera un Bitmap con el avatar renderizado en el tamano indicado.
     */
    fun renderToBitmap(context: Context, config: AvatarCamaronConfig, sizePx: Int): Bitmap {
        val layer = buildLayerDrawable(context, config)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layer.setBounds(0, 0, sizePx, sizePx)
        layer.draw(canvas)
        return bitmap
    }

    /**
     * Exporta el avatar renderizado a un archivo PNG.
     */
    fun exportToPng(context: Context, config: AvatarCamaronConfig, sizePx: Int, outputFile: File): Result<File> {
        return try {
            val bitmap = renderToBitmap(context, config, sizePx)
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveAccessoryDrawable(context: Context, accesorioKey: String): Int {
        val normalized = AvatarCamaronVisuals.normalizeAccessoryKey(accesorioKey)
        val candidateName = "avatar_acc_${normalized}_os"
        val candidateId = context.resources.getIdentifier(candidateName, "drawable", context.packageName)
        return if (candidateId != 0) {
            candidateId
        } else {
            AvatarCamaronVisuals.accessoryDrawableRes(normalized)
        }
    }

    @DrawableRes
    private fun accessoryDrawableRes(accesorioKey: String): Int {
        return AvatarCamaronVisuals.accessoryDrawableRes(accesorioKey)
    }
}
