package com.example.spinetrack.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.R
import com.example.spinetrack.databinding.FragmentResetPasswordBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeAuthState()
    }

    private fun setupListeners() {
        // Botón enviar
        binding.btnSend.setOnClickListener {
            if (validateInputs()) {
                val email = binding.etEmail.text.toString().trim()
                authViewModel.resetPassword(email) {
                    showSuccess()
                    findNavController().navigate(R.id.action_reset_password_to_login)
                }
            }
        }

        // Link volver al login
        binding.tvBackLogin.setOnClickListener {
            findNavController().navigate(R.id.action_reset_password_to_login)
        }

        // Limpiar errores al escribir
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.tilEmail.error = null
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.errorMessage.collect { errorMessage ->
                errorMessage?.let {
                    showError(it)
                    authViewModel.clearError()
                }
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

        return isValid
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess() {
        Snackbar.make(
            binding.root,
            getString(R.string.reset_success),
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}