package com.example.spinetrack.ui.comunidad

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.spinetrack.data.model.UserProfile
import com.example.spinetrack.databinding.FragmentUserProfileBinding
import com.example.spinetrack.data.repository.UsuariosRepository
import com.google.firebase.database.FirebaseDatabase
import com.example.spinetrack.data.local.UserPreferences as LocalUserPrefs
import android.widget.Toast
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private var uidArg: String? = null

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
        uidArg?.let { uid ->
            // cargar datos simples desde /users/{uid}
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val snap = FirebaseDatabase.getInstance().getReference("users/$uid").get().await()
                    @Suppress("UNCHECKED_CAST")
                    val map = snap.value as? Map<String, Any>
                    val user = if (map != null) UserProfile.fromMap(uid, map) else UserProfile(uid = uid)
                    showUser(user)
                } catch (e: Exception) {
                    // mostrar fallback
                    showUser(UserProfile(uid = uid))
                }
            }
        }
    }

    private fun showUser(user: UserProfile) {
        binding.tvProfileAvatar.text = user.nombre.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvProfileName.text = user.nombre.ifBlank { "Usuario" }
        binding.tvProfileEmail.text = user.email
        // cargar foto si existe
        if (!user.photoUrl.isNullOrBlank()) {
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
        } else {
            binding.ivProfileAvatar.visibility = View.GONE
            binding.tvProfileAvatar.visibility = View.VISIBLE
        }

        // Verificar si ya es amigo y configurar botón
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = LocalUserPrefs(requireContext())
            val current = prefs.userIdFlow.first()
            if (current.isNullOrBlank() || current == user.uid) {
                binding.btnAddFriend.visibility = View.GONE
                return@launch
            }
            val isFriend = withContext(Dispatchers.IO) { UsuariosRepository.esAmigo(current, user.uid) }
            if (isFriend) {
                binding.btnAddFriend.text = "Eliminar amigo"
                binding.btnAddFriend.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO) { UsuariosRepository.eliminarAmigo(current, user.uid) }
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Amigo eliminado", Toast.LENGTH_SHORT).show()
                            binding.btnAddFriend.text = "Agregar amigo"
                            showUser(user) // refrescar estado
                        } else {
                            Toast.makeText(requireContext(), "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                binding.btnAddFriend.text = "Agregar amigo"
                binding.btnAddFriend.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val res = withContext(Dispatchers.IO) { UsuariosRepository.agregarAmigo(current, user.uid) }
                        if (res.isSuccess) {
                            Toast.makeText(requireContext(), "Amigo agregado", Toast.LENGTH_SHORT).show()
                            binding.btnAddFriend.text = "Eliminar amigo"
                            showUser(user) // refrescar estado
                        } else {
                            Toast.makeText(requireContext(), "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_UID = "uid"
        fun newInstance(uid: String): UserProfileFragment {
            val f = UserProfileFragment()
            val b = Bundle()
            b.putString(ARG_UID, uid)
            f.arguments = b
            return f
        }
    }
}

