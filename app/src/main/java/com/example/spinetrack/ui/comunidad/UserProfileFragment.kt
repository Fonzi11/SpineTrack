package com.example.spinetrack.ui.comunidad

import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.spinetrack.R
import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.databinding.FragmentUserProfileBinding
import com.example.spinetrack.data.repository.UsuariosRepository
import com.google.firebase.database.FirebaseDatabase
import com.example.spinetrack.data.preferences.UserPreferences
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import androidx.core.os.bundleOf
import com.example.spinetrack.ui.perfil.AvatarCamaronRenderer

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private var uidArg: String? = null
    private var currentUid: String? = null
    private var viewedUser: UserProfile? = null
    private var isFriend: Boolean = false
    private var isProcessingFriendAction: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uidArg = arguments?.getString(ARG_UID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            currentUid = UserPreferences(requireContext()).userIdFlow.first()
            val uid = uidArg ?: return@launch

            val user = try {
                val snap = FirebaseDatabase.getInstance().getReference("users/$uid").get().await()
                @Suppress("UNCHECKED_CAST")
                val map = snap.value as? Map<String, Any>
                if (map != null) UserProfile.fromMap(uid, map) else UserProfile(uid = uid)
            } catch (_: Exception) {
                UserProfile(uid = uid)
            }

            viewedUser = user
            showUser(user)
            refreshFriendState(user.uid)
        }

        binding.btnAddFriend.setOnClickListener {
            toggleFriendship()
        }
    }

    private fun showUser(user: UserProfile) {
        binding.tvProfileAvatar.text = user.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvProfileName.text = user.nombre.ifBlank { "Usuario" }
        binding.tvProfileEmail.text = user.email
        binding.tvProfilePoints.text = getString(R.string.community_points_format, user.puntosTotales)
        binding.tvProfileLessons.text = getString(R.string.community_lessons_format, user.leccionesCompletadas)
        binding.tvProfileStreak.text = getString(R.string.community_streak_format, user.rachaActual)
        binding.tvProfileLevel.text = getString(R.string.community_level_format, user.nivel)
        when {
            !user.photoUrl.isNullOrBlank() -> {
                try {
                    com.bumptech.glide.Glide.with(binding.ivProfileAvatar.context)
                        .load(user.photoUrl)
                        .circleCrop()
                        .into(binding.ivProfileAvatar)
                    binding.ivProfileAvatar.visibility = View.VISIBLE
                    binding.tvProfileAvatar.visibility = View.GONE
                } catch (_: Exception) {
                    binding.ivProfileAvatar.visibility = View.GONE
                    binding.tvProfileAvatar.visibility = View.VISIBLE
                }
            }
            user.avatarConfig != null -> {
                AvatarCamaronRenderer.applyToImageView(
                    binding.ivProfileAvatar.context,
                    binding.ivProfileAvatar,
                    user.avatarConfig
                )
                binding.ivProfileAvatar.visibility = View.VISIBLE
                binding.tvProfileAvatar.visibility = View.GONE
            }
            else -> {
                binding.ivProfileAvatar.visibility = View.GONE
                binding.tvProfileAvatar.visibility = View.VISIBLE
            }
        }
        renderFriendActionButton()
    }

    private fun refreshFriendState(targetUid: String) {
        val current = currentUid
        if (current.isNullOrBlank() || current == targetUid) {
            binding.btnAddFriend.visibility = View.GONE
            return
        }

        binding.btnAddFriend.visibility = View.VISIBLE
        isProcessingFriendAction = true
        renderFriendActionButton()

        viewLifecycleOwner.lifecycleScope.launch {
            isFriend = withContext(Dispatchers.IO) {
                UsuariosRepository.esAmigo(current, targetUid)
            }
            isProcessingFriendAction = false
            renderFriendActionButton()
        }
    }

    private fun toggleFriendship() {
        val current = currentUid
        val target = viewedUser?.uid
        if (current.isNullOrBlank() || target.isNullOrBlank() || current == target || isProcessingFriendAction) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            isProcessingFriendAction = true
            renderFriendActionButton()

            val result = withContext(Dispatchers.IO) {
                if (isFriend) UsuariosRepository.eliminarAmigo(current, target)
                else UsuariosRepository.agregarAmigo(current, target)
            }

            if (result.isSuccess) {
                isFriend = !isFriend
                val msgRes = if (isFriend) R.string.friend_added_toast else R.string.friend_removed_toast
                Toast.makeText(requireContext(), getString(msgRes), Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult(
                    RESULT_FRIENDSHIP_CHANGED,
                    bundleOf("target_uid" to target, "is_friend" to isFriend)
                )
            } else {
                val message = result.exceptionOrNull()?.message ?: getString(R.string.error_register)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.friend_error_format, message),
                    Toast.LENGTH_SHORT
                ).show()
            }

            isProcessingFriendAction = false
            renderFriendActionButton()
        }
    }

    private fun renderFriendActionButton() {
        val targetUid = viewedUser?.uid
        if (currentUid.isNullOrBlank() || targetUid.isNullOrBlank() || currentUid == targetUid) {
            binding.btnAddFriend.visibility = View.GONE
            return
        }

        binding.btnAddFriend.visibility = View.VISIBLE
        binding.btnAddFriend.isEnabled = !isProcessingFriendAction
        if (isProcessingFriendAction) {
            applyFriendButtonStyleLoading()
            binding.btnAddFriend.text = getString(R.string.loading)
        } else if (isFriend) {
            applyFriendButtonStyleRemove()
            binding.btnAddFriend.text = getString(R.string.friend_remove)
        } else {
            applyFriendButtonStyleAdd()
            binding.btnAddFriend.text = getString(R.string.friend_add)
        }
    }

    private fun applyFriendButtonStyleAdd() {
        binding.btnAddFriend.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.peach_primary)
        )
        binding.btnAddFriend.strokeWidth = 0
        binding.btnAddFriend.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        binding.btnAddFriend.alpha = 1f
    }

    private fun applyFriendButtonStyleRemove() {
        binding.btnAddFriend.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.surface_white)
        )
        binding.btnAddFriend.strokeWidth = resources.displayMetrics.density.times(1f).toInt().coerceAtLeast(1)
        binding.btnAddFriend.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.bad_red)
        )
        binding.btnAddFriend.setTextColor(ContextCompat.getColor(requireContext(), R.color.bad_red))
        binding.btnAddFriend.alpha = 1f
    }

    private fun applyFriendButtonStyleLoading() {
        binding.btnAddFriend.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.peach_light)
        )
        binding.btnAddFriend.strokeWidth = 0
        binding.btnAddFriend.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_medium))
        binding.btnAddFriend.alpha = 0.9f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_UID = "uid"
        const val RESULT_FRIENDSHIP_CHANGED = "result_friendship_changed"
    }
}
