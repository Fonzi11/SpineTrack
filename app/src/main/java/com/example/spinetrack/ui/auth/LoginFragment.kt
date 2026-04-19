package com.example.spinetrack.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.R
import com.example.spinetrack.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import com.example.spinetrack.data.model.AuthState  // ← correcto
import com.example.spinetrack.MainActivity

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeAuthState()
    }

    private fun setupListeners() {
        // Botón de login
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                binding.btnLogin.setOnClickListener {
                    if (validateInputs()) {
                        val email = binding.etEmail.text.toString().trim()
                        val password = binding.etPassword.text.toString()
                        viewLifecycleOwner.lifecycleScope.launch {
                            authViewModel.login(email, password)
                        }
                    }
                }
            }
        }

        // Link a registro
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        // Link a recuperar contraseña
        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_reset_password)
        }

        // Botón de Google (preparado para futuro)
        binding.btnGoogle.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Login con Google estará disponible próximamente",
                Toast.LENGTH_SHORT
            ).show()
            // TODO: Implementar Google Sign-In con Credential Manager
        }

        // Limpiar errores al escribir
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilEmail.error = null
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilPassword.error = null
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> { }
                    is AuthState.Authenticated -> {
                        // Notificar a MainActivity para que muestre la barra de navegación
                        (activity as? MainActivity)?.onLoginSuccess()
                    }
                    is AuthState.Error -> {
                        showError(state.message)
                    }
                    is AuthState.Unauthenticated -> { }
                    else -> { } // ← respaldo
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnLogin.isEnabled = !isLoading
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validar email
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            isValid = false
        }

        // Validar contraseña
        val password = binding.etPassword.text.toString()
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_min)
            isValid = false
        }

        return isValid
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}