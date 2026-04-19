package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.spinetrack.databinding.FragmentLeccionesBinding
import com.google.android.material.tabs.TabLayoutMediator

class LeccionesFragment : Fragment() {

    private var _binding: FragmentLeccionesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeccionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val pagerAdapter = LeccionesPagerAdapter(childFragmentManager, lifecycle)
        binding.viewPagerLecciones.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayoutLecciones, binding.viewPagerLecciones) { tab, position ->
            tab.text = pagerAdapter.tabTitles[position]
        }.attach()

        binding.viewPagerLecciones.offscreenPageLimit = 2
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}