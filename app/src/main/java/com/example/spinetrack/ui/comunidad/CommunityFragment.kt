package com.example.spinetrack.ui.comunidad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.UsuariosRepository
import com.example.spinetrack.databinding.FragmentCommunityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.withContext

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private var currentUid: String? = null
    private var friendsUids: Set<String> = emptySet()

    private val adapter = UsersAdapter { user ->
        val bundle = Bundle().apply { putString("uid", user.uid) }
        findNavController().navigate(com.example.spinetrack.R.id.nav_user_profile, bundle)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter

        val prefs = UserPreferences(requireContext())
        lifecycleScope.launch {
            currentUid = prefs.userIdFlow.first()
            // Cargar lista de amigos del usuario actual
            currentUid?.let { uid ->
                val result = withContext(Dispatchers.IO) { UsuariosRepository.obtenerAmigos(uid) }
                if (result.isSuccess) {
                    friendsUids = result.getOrDefault(emptyList()).map { it.uid }.toSet()
                }
            }

            binding.progressLoading.visibility = View.VISIBLE
            UsuariosRepository.escucharUsuariosPublicos().collect { list ->
                binding.progressLoading.visibility = View.GONE
                adapter.setFriends(friendsUids, currentUid)
                adapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class UsersAdapter(val onClick: (UserProfile) -> Unit) : androidx.recyclerview.widget.ListAdapter<UserProfile, UsersAdapter.Holder>(DIFF) {
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(com.example.spinetrack.R.layout.item_user, parent, false)
            return Holder(v, onClick)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position), friendsUids, currentUid)
        }

        class Holder(itemView: View, val onClick: (UserProfile) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val ivAvatar = itemView.findViewById<android.widget.ImageView>(com.example.spinetrack.R.id.iv_avatar)
            private val tvAvatar = itemView.findViewById<android.widget.TextView>(com.example.spinetrack.R.id.tv_avatar)
            private val tvNombre = itemView.findViewById<android.widget.TextView>(com.example.spinetrack.R.id.tv_nombre)
            private val tvEmail = itemView.findViewById<android.widget.TextView>(com.example.spinetrack.R.id.tv_email)
            private val tvBadge = itemView.findViewById<TextView?>(com.example.spinetrack.R.id.tv_friend_badge)

            fun bind(user: UserProfile, friendsUids: Set<String>, currentUid: String?) {
                val initial = user.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                tvAvatar.text = initial
                tvNombre.text = user.nombre.ifBlank { "Usuario" }
                tvEmail.text = user.email

                // Mostrar badge de amigo si aplica (no mostrar para uno mismo)
                if (tvBadge != null) {
                    val isFriend = friendsUids.contains(user.uid)
                    val isSelf = user.uid == currentUid
                    tvBadge.visibility = if (isFriend && !isSelf) View.VISIBLE else View.GONE
                    tvBadge.text = "Amigo"
                }

                if (!user.photoUrl.isNullOrBlank()) {
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
                } else {
                    ivAvatar.visibility = View.GONE
                    tvAvatar.visibility = View.VISIBLE
                }

                itemView.setOnClickListener { onClick(user) }
            }
        }
    }
}

