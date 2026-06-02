package com.example.spinetrack.ui.perfil

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.R
import com.example.spinetrack.data.model.AvatarCamaronConfig
import com.example.spinetrack.data.model.AvatarCamaronDefaults
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.UsuariosRepository
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AvatarPersonalizacionFragment : Fragment() {

    private lateinit var ivPreview: ImageView
    private lateinit var rgColor: RadioGroup
    private lateinit var rgAccesorio: RadioGroup
    private lateinit var sliderSize: Slider
    private lateinit var btnGuardar: Button
    private lateinit var btnRestaurar: Button

    private val colorOptionIds = mutableMapOf<Int, String>()
    private val accessoryOptionIds = mutableMapOf<Int, String>()

    private lateinit var preferences: UserPreferences
    private var currentConfig = AvatarCamaronConfig()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return buildContentView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = UserPreferences(requireContext())

        setupListeners()
        loadInitialConfig()
    }

    private fun loadInitialConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            val local = preferences.avatarCamaronConfigFlow.first()
            currentConfig = local

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                val remote = UsuariosRepository.obtenerAvatarCamaron(uid).getOrNull()
                if (remote != null) {
                    currentConfig = remote
                }
            }

            bindConfigToControls(currentConfig)
            renderPreview(currentConfig)
        }
    }

    private fun setupListeners() {
        rgColor.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            val colorKey = colorOptionIds[checkedId] ?: AvatarCamaronDefaults.DEFAULT_COLOR_KEY
            currentConfig = currentConfig.copy(colorKey = colorKey)
            renderPreview(currentConfig)
        }

        rgAccesorio.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            val accessoryKey = accessoryOptionIds[checkedId] ?: AvatarCamaronDefaults.DEFAULT_ACCESSORY_KEY
            currentConfig = currentConfig.copy(accesorioKey = accessoryKey)
            renderPreview(currentConfig)
        }

        sliderSize.addOnChangeListener { _, value, _ ->
            currentConfig = currentConfig.copy(sizeSp = value.toInt())
            renderPreview(currentConfig)
        }

        btnGuardar.setOnClickListener {
            guardarAvatar()
        }

        btnRestaurar.setOnClickListener {
            currentConfig = AvatarCamaronConfig()
            bindConfigToControls(currentConfig)
            renderPreview(currentConfig)
        }
    }

    private fun bindConfigToControls(config: AvatarCamaronConfig) {
        val resolvedColor = AvatarCamaronVisuals.normalizeColorKey(config.colorKey)
        val resolvedAccessory = AvatarCamaronVisuals.normalizeAccessoryKey(config.accesorioKey)

        val colorId = colorOptionIds.entries.firstOrNull { it.value == resolvedColor }?.key
            ?: colorOptionIds.keys.firstOrNull()
        if (colorId != null) {
            rgColor.check(colorId)
        }

        val accessoryId = accessoryOptionIds.entries.firstOrNull { it.value == resolvedAccessory }?.key
            ?: accessoryOptionIds.keys.firstOrNull()
        if (accessoryId != null) {
            rgAccesorio.check(accessoryId)
        }

        sliderSize.value = config.sizeSp.toFloat()
    }

    private fun renderPreview(config: AvatarCamaronConfig) {
        val resolved = config.copy(
            colorKey = AvatarCamaronVisuals.normalizeColorKey(config.colorKey),
            accesorioKey = AvatarCamaronVisuals.normalizeAccessoryKey(config.accesorioKey)
        )
        val sizePx = dp(resolved.sizeSp * 4)
        val params = ivPreview.layoutParams as? LinearLayout.LayoutParams
        if (params != null) {
            params.width = sizePx
            params.height = sizePx
            ivPreview.layoutParams = params
        }
        AvatarCamaronRenderer.applyToImageView(requireContext(), ivPreview, resolved)
    }

    private fun guardarAvatar() {
        viewLifecycleOwner.lifecycleScope.launch {
            val normalized = currentConfig.copy(
                colorKey = AvatarCamaronVisuals.normalizeColorKey(currentConfig.colorKey),
                accesorioKey = AvatarCamaronVisuals.normalizeAccessoryKey(currentConfig.accesorioKey)
            )
            preferences.saveAvatarCamaronConfig(normalized)

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                UsuariosRepository.guardarAvatarCamaron(uid, normalized)
            }

            findNavController().popBackStack()
        }
    }

    private fun buildContentView(): View {
        val context = requireContext()
        val padding = dp(20)

        val root = ScrollView(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background_cream))
            isFillViewport = true
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(context).apply {
            text = getString(R.string.avatar_personalizacion_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(ContextCompat.getColor(context, R.color.text_dark))
        }
        content.addView(title)

        val subtitle = TextView(context).apply {
            text = getString(R.string.avatar_personalizacion_subtitle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.text_soft))
            setPadding(0, dp(4), 0, 0)
        }
        content.addView(subtitle)

        ivPreview = ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar_circle)
            val size = dp(AvatarCamaronDefaults.DEFAULT_SIZE_SP * 4)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(20)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        content.addView(ivPreview)

        content.addView(sectionLabel(getString(R.string.avatar_color)))
        rgColor = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        AvatarCamaronVisuals.colorOptions.forEach { option ->
            val id = addRadio(rgColor, getString(option.labelRes))
            colorOptionIds[id] = option.key
        }
        content.addView(rgColor)

        content.addView(sectionLabel(getString(R.string.avatar_accesorio)))
        rgAccesorio = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        AvatarCamaronVisuals.accessoryOptions.forEach { option ->
            val id = addRadio(rgAccesorio, getString(option.labelRes))
            accessoryOptionIds[id] = option.key
        }
        content.addView(rgAccesorio)

        content.addView(sectionLabel(getString(R.string.avatar_tamano)))
        sliderSize = Slider(context).apply {
            valueFrom = 20f
            valueTo = 44f
            stepSize = 2f
            value = AvatarCamaronDefaults.DEFAULT_SIZE_SP.toFloat()
        }
        content.addView(sliderSize)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(20), 0, 0)
        }

        btnRestaurar = Button(context).apply {
            text = getString(R.string.avatar_restaurar)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnGuardar = Button(context).apply {
            text = getString(R.string.avatar_guardar)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        buttonsRow.addView(btnRestaurar)
        buttonsRow.addView(btnGuardar)
        content.addView(buttonsRow)

        root.addView(content)
        return root
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_dark))
            setPadding(0, dp(12), 0, dp(8))
        }
    }

    private fun addRadio(group: RadioGroup, text: String): Int {
        val id = View.generateViewId()
        val radio = android.widget.RadioButton(requireContext()).apply {
            this.id = id
            this.text = text
            setTextColor(Color.BLACK)
        }
        group.addView(radio)
        return id
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
