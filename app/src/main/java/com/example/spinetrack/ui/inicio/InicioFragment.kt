package com.example.spinetrack.ui.inicio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.SesionesRepository
import com.example.spinetrack.data.repository.UserStatsRepository
import com.example.spinetrack.data.repository.UsuariosRepository
import com.example.spinetrack.databinding.FragmentInicioBinding
import com.example.spinetrack.ui.comunidad.UserProfileFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * InicioFragment — Pantalla principal (tab "Inicio")
 *
 * Muestra:
 *  - Saludo personalizado + mascota cuy
 *  - Racha actual y mejor racha
 *  - Accesos rápidos: Lecciones, Ejercicio, Comunidad, Stats
 *  - Progreso del día (minutos, ejercicios, lecciones, puntos)
 *  - Calendario semanal
 *  - Tip del día
 */
class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private lateinit var userPreferences: UserPreferences
    private var allAmigos: List<UserProfile> = emptyList()
    private val amigosAdapter = AmigosAdapter { amigo ->
        val bundle = Bundle().apply { putString("uid", amigo.uid) }
        findNavController().navigate(R.id.nav_user_profile, bundle)
    }

    // Datos sincronizados desde Firebase
    private var streakCurrent = 0
    private var streakBest = 0
    private var dailyMinutes = 0
    private val dailyGoalMinutes = 30
    private var dailyExercises = 0
    private var dailyLessons = 0
    private var dailyPoints = 0

    private val tips = listOf(
        "Ajusta tu monitor a la altura de los ojos para mantener el cuello en posición neutral.",
        "Cada 30 minutos, levántate y estira la espalda durante 1-2 minutos.",
        "Mantén los hombros relajados y alejados de las orejas mientras escribes.",
        "Usa una silla con soporte lumbar para mantener la curva natural de tu columna.",
        "Coloca el teclado y el ratón a la misma altura para evitar tensión en los hombros."
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userPreferences = UserPreferences(requireContext())
        loadUserName()
        setupQuickNav()
        setupAmigos()
        setupWeekCalendar()
        setupTip()
        syncProgressFromFirebase()

        parentFragmentManager.setFragmentResultListener(
            UserProfileFragment.RESULT_FRIENDSHIP_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            refreshAmigos()
        }
    }

    override fun onResume() {
        super.onResume()
        syncProgressFromFirebase()
        refreshAmigos()
    }

    // ── Cargar nombre de usuario desde DataStore ────────────
    private fun loadUserName() {
        viewLifecycleOwner.lifecycleScope.launch {
            val name = userPreferences.userNameFlow.first()
            if (!name.isNullOrBlank()) {
                binding.tvUsername.text = getString(R.string.username_with_star, name)
            } else {
                val email = userPreferences.userEmailFlow.first()
                val fallbackName = email?.substringBefore("@") ?: getString(R.string.app_name)
                binding.tvUsername.text = getString(R.string.username_with_star, fallbackName)
            }
        }
    }

    // ── Racha ────────────────────────────────────────────────
    private fun setupStreak() {
        binding.tvStreakCount.text = streakCurrent.toString()
        binding.tvBestStreak.text = getString(R.string.best_streak, streakBest)
    }

    // ── Amigos ────────────────────────────────────────────
    private fun setupAmigos() {
        binding.recyclerAmigos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAmigos.adapter = amigosAdapter
        binding.etSearchAmigos.doAfterTextChanged { editable ->
            applyAmigosFilter(editable?.toString().orEmpty())
        }

        refreshAmigos()
    }

    private fun refreshAmigos() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = userPreferences.userIdFlow.first()
            if (uid.isNullOrBlank()) return@launch
            val result = withContext(Dispatchers.IO) { UsuariosRepository.obtenerAmigos(uid) }
            if (result.isSuccess) {
                allAmigos = result.getOrDefault(emptyList()).filter { it.uid != uid }
                applyAmigosFilter(binding.etSearchAmigos.text?.toString().orEmpty())
            }
        }
    }

    private fun applyAmigosFilter(query: String) {
        val normalized = query.trim().lowercase()
        val filtered = if (normalized.isBlank()) {
            allAmigos
        } else {
            allAmigos.filter { user ->
                user.nombre.lowercase().contains(normalized) ||
                    user.email.lowercase().contains(normalized) ||
                    user.uid.lowercase().contains(normalized)
            }
        }
        amigosAdapter.submitList(filtered)
    }

    // ── Quick Nav ────────────────────────────────────────────
    private fun setupQuickNav() {
        binding.quickNavLecciones.setOnClickListener {
            findNavController().navigate(R.id.nav_lecciones)
        }
        binding.quickNavRanking.setOnClickListener {
            findNavController().navigate(R.id.nav_ranking)
        }
        binding.quickNavComunidad.setOnClickListener {
            findNavController().navigate(R.id.nav_comunidad)
        }
        binding.quickNavStats.setOnClickListener {
            findNavController().navigate(R.id.nav_stats)
        }
        // Acceso rápido al dispositivo
        binding.quickNavDispositivo.setOnClickListener {
            findNavController().navigate(R.id.nav_dispositivo)
        }
        // El card de comunidad también lleva a la sección Comunidad completa
        binding.cardComunidad.setOnClickListener {
            findNavController().navigate(R.id.nav_comunidad)
        }
        // Acceso rápido al control del dispositivo (abrir pantalla Dispositivo)
        binding.cardMascot.setOnClickListener {
            findNavController().navigate(R.id.nav_dispositivo)
        }
    }

    // ── Progreso del día ─────────────────────────────────────
    private fun setupProgress() {
        val progressPct = if (dailyGoalMinutes > 0) {
            (dailyMinutes * 100) / dailyGoalMinutes
        } else 0

        binding.progressDaily.progress = progressPct
        binding.tvProgressPct.text = String.format(Locale.getDefault(), "%d%%", progressPct)
        binding.tvGoalTime.text = String.format(
            Locale.getDefault(),
            "%d/%d %s",
            dailyMinutes,
            dailyGoalMinutes,
            getString(R.string.mins_label)
        )

        val minutesLeft = dailyGoalMinutes - dailyMinutes
        binding.tvTimeLeft.text = if (minutesLeft > 0) "Faltan $minutesLeft min" else "¡Meta alcanzada! 🎉"

        binding.tvMinutos.text = dailyMinutes.toString()
        binding.tvEjercicios.text = dailyExercises.toString()
        binding.tvLeccionesCount.text = dailyLessons.toString()
        binding.tvPuntos.text = dailyPoints.toString()
    }

    private fun syncProgressFromFirebase() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = userPreferences.userIdFlow.first()
            if (uid.isNullOrBlank()) {
                setupStreak()
                setupProgress()
                return@launch
            }

            val profileResult = UserStatsRepository.observeUserProfileStats(uid).first()
            profileResult.getOrNull()?.let { profile ->
                dailyPoints = profile.puntos
                dailyLessons = profile.lecciones
                streakCurrent = profile.rachaActual
                streakBest = profile.mejorRacha
            }

            val sesionesResult = withContext(Dispatchers.IO) { SesionesRepository.obtenerSesiones(uid) }
            if (sesionesResult.isSuccess) {
                val today = LocalDate.now().toString()
                val sesionesHoy = sesionesResult.getOrDefault(emptyList())
                    .filter { it.tsInicio.take(10) == today }

                dailyMinutes = sesionesHoy.sumOf { it.duracionMin }.toInt()
                dailyExercises = sesionesHoy.size
            }

            setupStreak()
            setupProgress()
        }
    }

    // ── Calendario semanal ───────────────────────────────────
    private fun setupWeekCalendar() {
        val calendar = Calendar.getInstance()
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)
        val todayDow = calendar.get(Calendar.DAY_OF_WEEK) // 1=Dom … 7=Sáb

        // Retroceder al lunes de la semana actual
        val daysFromMonday = when (todayDow) {
            Calendar.SUNDAY -> 6
            else -> todayDow - Calendar.MONDAY
        }
        calendar.add(Calendar.DAY_OF_MONTH, -daysFromMonday)

        val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
        val inflater = LayoutInflater.from(requireContext())

        binding.weekDaysRow.removeAllViews()

        for (i in 0..6) {
            val dayNum = calendar.get(Calendar.DAY_OF_MONTH)
            val isToday = dayNum == todayDay

            val dayView = inflater.inflate(R.layout.item_day_week, binding.weekDaysRow, false)
            val tvLetter = dayView.findViewById<TextView>(R.id.tv_day_letter)
            val tvNumber = dayView.findViewById<TextView>(R.id.tv_day_number)

            tvLetter.text = dayLetters[i]
            tvNumber.text = dayNum.toString()

            if (isToday) {
                tvNumber.background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.bg_day_circle_today
                )
                tvNumber.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_on_peach)
                )
                tvLetter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.peach_primary)
                )
            }

            binding.weekDaysRow.addView(dayView)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    // ── Tip del día ──────────────────────────────────────────
    private fun setupTip() {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        binding.tvTip.text = tips[dayOfYear % tips.size]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Adaptador de amigos para la lista en Inicio ──────
    private class AmigosAdapter(val onClick: (UserProfile) -> Unit) :
        androidx.recyclerview.widget.ListAdapter<UserProfile, AmigosAdapter.Holder>(DIFF) {

        companion object {
            private val DIFF = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<UserProfile>() {
                override fun areItemsTheSame(old: UserProfile, new: UserProfile) = old.uid == new.uid
                override fun areContentsTheSame(old: UserProfile, new: UserProfile) = old == new
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_amigo, parent, false)
            return Holder(v, onClick)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        class Holder(itemView: View, val onClick: (UserProfile) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val tvAvatar = itemView.findViewById<TextView>(R.id.tv_amigo_avatar)
            private val tvNombre = itemView.findViewById<TextView>(R.id.tv_amigo_nombre)
            private val tvPuntos = itemView.findViewById<TextView>(R.id.tv_amigo_puntos)

            fun bind(user: UserProfile) {
                tvAvatar.text = user.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                tvNombre.text = user.nombre.ifBlank { user.uid.take(8) }
                tvPuntos.text = itemView.context.getString(
                    R.string.friend_card_points_format,
                    user.puntosTotales,
                    user.rachaActual,
                    user.nivel
                )
                itemView.setOnClickListener { onClick(user) }
            }
        }
    }
}