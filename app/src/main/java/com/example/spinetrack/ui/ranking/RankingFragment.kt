package com.example.spinetrack.ui.ranking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.spinetrack.databinding.FragmentRankingBinding
import com.google.android.material.tabs.TabLayoutMediator

class RankingFragment : Fragment() {

    private var _binding: FragmentRankingBinding? = null
    private val binding get() = _binding!!

    private val tabTitles = listOf("Puntos", "Rachas", "Lecciones")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = RankingPagerAdapter(childFragmentManager, lifecycle)
        binding.viewPagerRanking.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayoutRanking, binding.viewPagerRanking) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class RankingPagerAdapter(fm: FragmentManager, lc: Lifecycle) :
        FragmentStateAdapter(fm, lc) {
        override fun getItemCount() = tabTitles.size
        override fun createFragment(position: Int) = RankingTabFragment.newInstance(position)
    }
}