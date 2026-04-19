package com.example.spinetrack.ui.perfil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.spinetrack.MainActivity
import com.example.spinetrack.R
import com.example.spinetrack.data.model.AuthState
import com.example.spinetrack.databinding.FragmentPerfilBinding
import com.example.spinetrack.databinding.ItemBadgeBinding
import com.example.spinetrack.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()
    private val perfilViewModel: PerfilViewModel by viewModels()

    data class Badge(val icon: String, val nombre: String, val desbloqueada: Boolean)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeaderPlaceholders()
        setupStatsPlaceholders()
        setupBadges(buildBadges(lecciones = 0, rachaActual = 0, puntos = 0))
        setupSignOut()
        observeAuthState()
        observePerfilState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Unauthenticated -> {
                        (activity as? MainActivity)?.onLogout()
                    }
                    else -> { }
                }
            }
        }
    }

    private fun observePerfilState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                perfilViewModel.uiState.collect { state ->
                    if (state.isError) {
                        binding.tvProfileName.text = state.errorMessage ?: "Error cargando perfil"
                        return@collect
                    }

                    renderHeader(
                        userName = state.nombre,
                        userEmail = state.email,
                        nivel = state.nivel,
                        ptsNivel = state.puntosNivelActual,
                        ptsNext = state.puntosSiguienteNivel
                    )

                    renderStats(
                        rachaActual = state.rachaActual,
                        puntosTotales = state.puntosTotales,
                        leccionesCompletadas = state.leccionesCompletadas,
                        mejorRacha = state.mejorRacha
                    )

                    setupBadges(
                        buildBadges(
                            lecciones = state.leccionesCompletadas,
                            rachaActual = state.rachaActual,
                            puntos = state.puntosTotales
                        )
                    )
                }
            }
        }
    }

    private fun setupHeaderPlaceholders() {
        renderHeader("Usuario", "", 1, 0, 100)
    }

    private fun renderHeader(
        userName: String,
        userEmail: String,
        nivel: Int,
        ptsNivel: Int,
        ptsNext: Int
    ) {
        val initial = userName.trim().firstOrNull()?.uppercase() ?: "U"
        binding.tvProfileAvatar.text = initial
        binding.tvProfileName.text = userName
        binding.tvProfileEmail.text = userEmail
        binding.tvNivel.text = getString(R.string.level_label, nivel)
        binding.tvNivelPts.text = getString(R.string.profile_level_points_format, ptsNivel, ptsNext)
        val progress = if (ptsNext > 0) (ptsNivel * 100) / ptsNext else 0
        binding.progressNivel.progress = progress
    }

    private fun setupStatsPlaceholders() {
        renderStats(0, 0, 0, 0)
    }

    private fun renderStats(
        rachaActual: Int,
        puntosTotales: Int,
        leccionesCompletadas: Int,
        mejorRacha: Int
    ) {
        binding.tvStatRacha.text = rachaActual.toString()
        binding.tvStatPuntos.text = puntosTotales.toString()
        binding.tvStatLecciones.text = leccionesCompletadas.toString()
        binding.tvStatMejorRacha.text = mejorRacha.toString()
    }

    private fun buildBadges(lecciones: Int, rachaActual: Int, puntos: Int): List<Badge> {
        return listOf(
            Badge("📖", "Primera lección", lecciones >= 1),
            Badge("🔥", "3 días seguidos", rachaActual >= 3),
            Badge("⚡", "7 días seguidos", rachaActual >= 7),
            Badge("💯", "100 puntos", puntos >= 100),
            Badge("🏅", "30 días seguidos", rachaActual >= 30),
            Badge("💎", "500 puntos", puntos >= 500),
        )
    }

    private fun setupBadges(badges: List<Badge>) {
        if (badges.size < 6) return
        val badgeViews = listOf(
            binding.badgePrimeraLeccion,
            binding.badge3Dias,
            binding.badge7Dias,
            binding.badge100Pts,
            binding.badge30Dias,
            binding.badge500Pts,
        )

        badges.forEachIndexed { index, badge ->
            val badgeBinding: ItemBadgeBinding = badgeViews[index]
            badgeBinding.tvBadgeIcon.apply {
                text = badge.icon
                setBackgroundResource(
                    if (badge.desbloqueada) R.drawable.bg_badge_unlocked
                    else R.drawable.bg_badge_locked
                )
                alpha = if (badge.desbloqueada) 1.0f else 0.4f
            }
            badgeBinding.tvBadgeName.text = badge.nombre
        }
    }

    private fun setupSignOut() {
        binding.btnSignOut.setOnClickListener {
            authViewModel.logout()  // ← ahora limpia Firebase + DataStore primero
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}