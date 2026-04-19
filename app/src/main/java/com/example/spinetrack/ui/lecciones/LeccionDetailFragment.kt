package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.R
import com.example.spinetrack.data.repository.LeccionesRepository
import com.example.spinetrack.databinding.FragmentLeccionDetailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.example.spinetrack.data.repository.UserStatsRepository
import com.example.spinetrack.data.preferences.UserPreferences
import java.time.LocalDateTime
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class LeccionDetailFragment : Fragment() {

    private var _binding: FragmentLeccionDetailBinding? = null
    private val binding get() = _binding!!

    private var leccionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leccionId = arguments?.getInt("leccion_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeccionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leccion = LeccionesRepository.getLeccionById(leccionId)
        if (leccion == null) {
            Toast.makeText(requireContext(), "Lección no encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvDetailTitle.text = leccion.titulo
        binding.tvDetailDesc.text = leccion.descripcion
        binding.tvDetailLevel.text = leccion.nivel.label
        binding.tvDetailDuration.text = "⏱ ${leccion.duracionMin} min"
        binding.tvDetailPoints.text = "+${leccion.puntos} pts"

        binding.btnStartAction.text = when (leccion.categoria) {
            com.example.spinetrack.data.model.CategoriaLeccion.EJERCICIOS -> "Iniciar ejercicio"
            com.example.spinetrack.data.model.CategoriaLeccion.HABITOS -> "Ver hábito"
            else -> "Ver tutorial"
        }

        binding.btnStartAction.setOnClickListener {
            // Navegar al fragment apropiado según la categoría
            val bundle = android.os.Bundle().apply { putInt("leccion_id", leccion.id) }
            when (leccion.categoria) {
                com.example.spinetrack.data.model.CategoriaLeccion.EJERCICIOS ->
                    findNavController().navigate(R.id.action_leccion_detail_to_ejercicio, bundle)
                com.example.spinetrack.data.model.CategoriaLeccion.HABITOS ->
                    findNavController().navigate(R.id.action_leccion_detail_to_habito, bundle)
                else ->
                    findNavController().navigate(R.id.action_leccion_detail_to_tutorial, bundle)
            }
        }

        // Inicializar estado del botón según si ya está completada
        val completed = com.example.spinetrack.data.local.CompletedLessonsStore.isCompleted(requireContext(), leccion.id)
        binding.btnMarkDone.isEnabled = !completed
        if (completed) binding.btnMarkDone.text = getString(R.string.leccion_marcada)

        binding.btnMarkDone.setOnClickListener {
            // Marcar como completada — persistir localmente y actualizar UI
            CoroutineScope(Dispatchers.Main).launch {
                com.example.spinetrack.data.local.CompletedLessonsStore.markCompleted(requireContext(), leccion.id)
                binding.btnMarkDone.isEnabled = false
                binding.btnMarkDone.text = getString(R.string.leccion_marcada)
                // mejorar aspecto del botón: icono y color
                try {
                    val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle)
                    binding.btnMarkDone.icon = icon
                    binding.btnMarkDone.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                } catch (_: Exception) { }
                Toast.makeText(requireContext(), getString(R.string.leccion_marcada), Toast.LENGTH_SHORT).show()
                // También sincronizar con Firebase: puntos, lecciones completadas y racha
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    launch(Dispatchers.IO) {
                        val res = UserStatsRepository.markLessonCompletedRemote(firebaseUser.uid, leccion.puntos)
                        // Notificar a la UI en hilo principal y guardar marca en DataStore para recargar stats
                        withContext(Dispatchers.Main) {
                            res.fold(
                                onSuccess = {
                                    Toast.makeText(requireContext(), "Puntos sincronizados (+${leccion.puntos})", Toast.LENGTH_SHORT).show()
                                    // animar el botón
                                    try {
                                        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.06f, 1f)
                                        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.06f, 1f)
                                        val anim = ObjectAnimator.ofPropertyValuesHolder(binding.btnMarkDone, scaleX, scaleY)
                                        anim.duration = 420
                                        anim.start()
                                    } catch (_: Exception) { }
                                },
                                onFailure = { e ->
                                    Toast.makeText(requireContext(), "Error sincronizando: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Guardar marca de actualización para que Estadísticas y Perfil recarguen
                        try {
                            val prefs = UserPreferences(requireContext())
                            prefs.saveLastStatsUpdate(LocalDateTime.now().toString())
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

