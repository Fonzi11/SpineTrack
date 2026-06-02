package com.example.spinetrack.ui.comunidad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.UsuariosRepository
import com.example.spinetrack.databinding.FragmentCommunityBinding
import com.example.spinetrack.ui.perfil.AvatarCamaronRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.withContext
import java.text.Normalizer

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private var currentUid: String? = null
    private var friendsUids: Set<String> = emptySet()
    private var allUsers: List<UserProfile> = emptyList()
    private var currentQuery: String = ""
    private var activeUsersCount: Int = 0
    private var firebaseHintShown: Boolean = false

    private val adapter = UsersAdapter(
        onClick = { user ->
            val bundle = Bundle().apply { putString("uid", user.uid) }
            findNavController().navigate(R.id.nav_user_profile, bundle)
        },
        onAddFriend = { user -> addFriend(user) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter
        binding.etSearchUsers.doAfterTextChanged { editable ->
            currentQuery = editable?.toString().orEmpty().trim()
            renderUsers()
        }

        parentFragmentManager.setFragmentResultListener(
            UserProfileFragment.RESULT_FRIENDSHIP_CHANGED,
            viewLifecycleOwner
        ) { _, _ ->
            refreshFriendsAsync()
        }

        val prefs = UserPreferences(requireContext())
        lifecycleScope.launch {
            currentUid = prefs.userIdFlow.first()
            refreshFriendsAsync()

            binding.progressLoading.visibility = View.VISIBLE
            UsuariosRepository.escucharUsuariosPublicos().collect { list ->
                binding.progressLoading.visibility = View.GONE
                adapter.setFriends(friendsUids, currentUid)
                activeUsersCount = list.size
                allUsers = list.filter { it.uid != currentUid }
                updateSummary()
                renderUsers()
                if (list.isEmpty() && currentQuery.isBlank() && !firebaseHintShown) {
                    firebaseHintShown = true
                    com.google.android.material.snackbar.Snackbar.make(
                        binding.root,
                        getString(R.string.community_permission_hint),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFriendsAsync()
    }

    private fun refreshFriendsAsync() {
        val uid = currentUid ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UsuariosRepository.obtenerAmigos(uid) }
            if (result.isSuccess) {
                friendsUids = result.getOrDefault(emptyList()).map { it.uid }.toSet()
                adapter.setFriends(friendsUids, currentUid)
                updateSummary()
                renderUsers()
            }
        }
    }

    private fun renderUsers() {
        val query = normalizeText(currentQuery)
        val filtered = if (query.isBlank()) {
            allUsers
        } else {
            allUsers.filter { user ->
                val haystack = buildString {
                    append(user.nombre)
                    append(' ')
                    append(user.email)
                    append(' ')
                    append(user.uid)
                }
                normalizeText(haystack).contains(query)
            }
        }

        binding.tvEmpty.text = if (currentQuery.isBlank()) {
            getString(R.string.community_empty)
        } else {
            getString(R.string.community_empty_search)
        }
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(filtered)
    }

    private fun normalizeText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase()
    }

    private fun updateSummary() {
        val friendsCount = friendsUids.size
        binding.tvActiveCount.text = getString(R.string.community_active_count_format, activeUsersCount)
        binding.tvFriendsCount.text = getString(R.string.community_friends_count_format, friendsCount)
    }

    private fun addFriend(user: UserProfile) {
        val uid = currentUid ?: return
        if (user.uid == uid || friendsUids.contains(user.uid)) return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                UsuariosRepository.agregarAmigo(uid, user.uid)
            }
            if (result.isSuccess) {
                friendsUids = friendsUids + user.uid
                adapter.setFriends(friendsUids, currentUid)
                Toast.makeText(requireContext(), getString(R.string.friend_added_toast), Toast.LENGTH_SHORT).show()
            } else {
                val message = result.exceptionOrNull()?.message ?: getString(R.string.error_register)
                Toast.makeText(requireContext(), getString(R.string.friend_error_format, message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class UsersAdapter(
        val onClick: (UserProfile) -> Unit,
        val onAddFriend: (UserProfile) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<UserProfile, UsersAdapter.Holder>(DIFF) {
        companion object {
            private val DIFF = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<UserProfile>() {
                override fun areItemsTheSame(oldItem: UserProfile, newItem: UserProfile) = oldItem.uid == newItem.uid
                override fun areContentsTheSame(oldItem: UserProfile, newItem: UserProfile) = oldItem == newItem
            }
        }

        private var friendsUids: Set<String> = emptySet()
        private var currentUid: String? = null

        fun setFriends(friends: Set<String>, uid: String?) {
            friendsUids = friends
            currentUid = uid
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return Holder(v, onClick, onAddFriend)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position), friendsUids, currentUid)
        }

        class Holder(
            itemView: View,
            val onClick: (UserProfile) -> Unit,
            val onAddFriend: (UserProfile) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val ivAvatar = itemView.findViewById<android.widget.ImageView>(R.id.iv_avatar)
            private val tvAvatar = itemView.findViewById<android.widget.TextView>(R.id.tv_avatar)
            private val tvNombre = itemView.findViewById<android.widget.TextView>(R.id.tv_nombre)
            private val tvEmail = itemView.findViewById<android.widget.TextView>(R.id.tv_email)
            private val tvStats = itemView.findViewById<android.widget.TextView>(R.id.tv_stats)
            private val tvBadge = itemView.findViewById<TextView?>(R.id.tv_friend_badge)
            private val btnAddFriend = itemView.findViewById<com.google.android.material.button.MaterialButton?>(R.id.btn_add_friend)

            fun bind(user: UserProfile, friendsUids: Set<String>, currentUid: String?) {
                val initial = user.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                tvAvatar.text = initial
                tvNombre.text = user.nombre.ifBlank { user.uid.take(8) }
                tvEmail.text = user.email
                tvStats.text = "${user.puntosTotales} pts · ${user.leccionesCompletadas} lecciones · racha ${user.rachaActual} · nivel ${user.nivel}"

                // Mostrar badge de amigo si aplica (no mostrar para uno mismo)
                if (tvBadge != null) {
                    val isFriend = friendsUids.contains(user.uid)
                    val isSelf = user.uid == currentUid
                    tvBadge.visibility = if (isFriend && !isSelf) View.VISIBLE else View.GONE
                    tvBadge.text = itemView.context.getString(R.string.friend_badge)
                }

                btnAddFriend?.let { button ->
                    val isFriend = friendsUids.contains(user.uid)
                    val isSelf = user.uid == currentUid
                    if (isFriend || isSelf) {
                        button.visibility = View.GONE
                        button.setOnClickListener(null)
                    } else {
                        button.visibility = View.VISIBLE
                        button.setOnClickListener { onAddFriend(user) }
                    }
                }

                when {
                    !user.photoUrl.isNullOrBlank() -> {
                        try {
                            com.bumptech.glide.Glide.with(ivAvatar.context)
                                .load(user.photoUrl)
                                .circleCrop()
                                .into(ivAvatar)
                            ivAvatar.visibility = View.VISIBLE
                            tvAvatar.visibility = View.GONE
                        } catch (_: Exception) {
                            ivAvatar.visibility = View.GONE
                            tvAvatar.visibility = View.VISIBLE
                        }
                    }
                    user.avatarConfig != null -> {
                        AvatarCamaronRenderer.applyToImageView(ivAvatar.context, ivAvatar, user.avatarConfig)
                        ivAvatar.visibility = View.VISIBLE
                        tvAvatar.visibility = View.GONE
                    }
                    else -> {
                        ivAvatar.visibility = View.GONE
                        tvAvatar.visibility = View.VISIBLE
                    }
                }

                itemView.setOnClickListener { onClick(user) }
            }
        }
    }
}
