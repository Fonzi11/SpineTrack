package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.spinetrack.data.repository.LeccionesRepository
import com.example.spinetrack.databinding.FragmentHabitoBinding
import com.example.spinetrack.R

class HabitoFragment : Fragment() {
    private var _binding: FragmentHabitoBinding? = null
    private val binding get() = _binding!!

    private var leccionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leccionId = arguments?.getInt("leccion_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHabitoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leccion = LeccionesRepository.getLeccionById(leccionId)
        binding.tvHabitoTitle.text = leccion?.titulo ?: ""
        val web = binding.root.findViewById<WebView>(R.id.web_habito_content)
        web.settings.javaScriptEnabled = false
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        val html = LeccionesRepository.getHtmlContent(leccionId)
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)

        binding.btnCreateReminder.setOnClickListener {
            binding.btnCreateReminder.isEnabled = false
            binding.btnCreateReminder.text = "Recordatorio creado"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

