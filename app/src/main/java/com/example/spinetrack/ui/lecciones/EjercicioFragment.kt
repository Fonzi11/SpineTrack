package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.spinetrack.data.repository.LeccionesRepository
import com.example.spinetrack.databinding.FragmentEjercicioBinding
import com.example.spinetrack.R

class EjercicioFragment : Fragment() {
    private var _binding: FragmentEjercicioBinding? = null
    private val binding get() = _binding!!

    private var leccionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leccionId = arguments?.getInt("leccion_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEjercicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leccion = LeccionesRepository.getLeccionById(leccionId)
        binding.tvEjercicioTitle.text = leccion?.titulo ?: ""
        val web = binding.root.findViewById<WebView>(R.id.web_ejercicio_content)
        web.settings.javaScriptEnabled = false
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        val html = LeccionesRepository.getHtmlContent(leccionId)
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)

        // placeholder: botón para iniciar cronómetro o rutina
        binding.btnStartExercise.setOnClickListener {
            binding.btnStartExercise.isEnabled = false
            binding.btnStartExercise.text = "Iniciando..."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

