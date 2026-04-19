package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import com.example.spinetrack.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.example.spinetrack.data.repository.LeccionesRepository
import com.example.spinetrack.databinding.FragmentTutorialBinding

class TutorialFragment : Fragment() {
    private var _binding: FragmentTutorialBinding? = null
    private val binding get() = _binding!!

    private var leccionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leccionId = arguments?.getInt("leccion_id") ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTutorialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leccion = LeccionesRepository.getLeccionById(leccionId)
        binding.tvTutorialTitle.text = leccion?.titulo ?: ""

        val web = binding.root.findViewById<WebView>(R.id.web_tutorial_content)
        web.settings.javaScriptEnabled = false
        web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        val html = LeccionesRepository.getHtmlContent(leccionId)
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

