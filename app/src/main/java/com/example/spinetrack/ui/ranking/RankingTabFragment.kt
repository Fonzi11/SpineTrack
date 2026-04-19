package com.example.spinetrack.ui.ranking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.RankingUser
import com.example.spinetrack.data.repository.RankingRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RankingTabFragment : Fragment() {

    companion object {
        private const val ARG_TAB = "arg_tab"
        fun newInstance(tab: Int) = RankingTabFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, tab) }
        }
    }

    // Datos por defecto si falla la carga remota
    private val mockUsers = listOf(
        RankingUser(2, "Karen Ballen",   320, 14, 18),
        RankingUser(3, "Mafe Tafur",     290, 12, 15),
        RankingUser(1, "Ismael Fonseca", 0,   0,  0),
        RankingUser(4, "Ana García",     180, 7,  10),
        RankingUser(5, "Luis Pérez",     150, 5,  8),
        RankingUser(6, "Sofía Ruiz",     120, 4,  6),
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ranking_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tab = arguments?.getInt(ARG_TAB) ?: 0
        val rv = view.findViewById<RecyclerView>(R.id.rv_ranking)

        // Cargar ranking remoto
        lifecycleScope.launch {
            val res = RankingRepository.fetchRanking()
            val users = res.getOrElse { mockUsers }

            val (sorted, scoreGetter, subtitleGetter) = when (tab) {
                0 -> Triple(
                    users.sortedByDescending { it.puntos },
                    { u: RankingUser -> u.puntos },
                    { u: RankingUser -> "${u.puntos} puntos" }
                )
                1 -> Triple(
                    users.sortedByDescending { it.rachas },
                    { u: RankingUser -> u.rachas },
                    { u: RankingUser -> "${u.rachas} días de racha" }
                )
                else -> Triple(
                    users.sortedByDescending { it.lecciones },
                    { u: RankingUser -> u.lecciones },
                    { u: RankingUser -> "${u.lecciones} lecciones" }
                )
            }

            val adapter = RankingAdapter(scoreGetter, subtitleGetter)
            rv.adapter = adapter
            adapter.submitList(sorted)
        }
    }
}