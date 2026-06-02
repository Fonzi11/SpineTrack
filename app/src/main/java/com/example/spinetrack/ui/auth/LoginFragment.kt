package com.example.spinetrack.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.R
import com.example.spinetrack.databinding.FragmentLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import com.example.spinetrack.data.model.AuthState  // ← correcto
import com.example.spinetrack.MainActivity

class LoginFragment : Fragment() {

    companion object {
        private const val TAG = "LoginFragment"
    }

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

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
        credentialManager = CredentialManager.create(requireContext())
        setupListeners()
        observeAuthState()
    }

    private fun setupListeners() {
        // Botón de login
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                authViewModel.login(email, password)
            }
        }

        // Link a registro
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        // Boton a recuperar contrasena
        binding.btnForgotPassword.setOnClickListener {
            val emailPrefill = binding.etEmail.text?.toString()?.trim().orEmpty()
            findNavController().navigate(
                R.id.action_login_to_reset_password,
                bundleOf("email_prefill" to emailPrefill)
            )
        }

        // Botón de Google
        binding.btnGoogle.setOnClickListener {
            iniciarLoginGoogle()
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
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnLogin.isEnabled = !isLoading
                binding.btnGoogle.isEnabled = !isLoading
            }
        }
    }

    private fun iniciarLoginGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val clientId = getString(R.string.default_web_client_id)
                if (clientId.isBlank()) {
                    showError("Configuracion incompleta de Google Sign-In (client ID vacio)")
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = requireContext(),
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    if (googleCredential.idToken.isBlank()) {
                        showError("No se pudo obtener el token de Google")
                        return@launch
                    }
                    authViewModel.loginWithGoogle(googleCredential.idToken)
                } else {
                    showError(getString(R.string.error_google_sign_in))
                }
            } catch (_: GetCredentialCancellationException) {
                // Usuario canceló el diálogo de selección de cuenta.
            } catch (_: NoCredentialException) {
                showError("No hay cuentas de Google disponibles en el dispositivo")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Error en CredentialManager", e)
                showError("Error de Google Sign-In: ${e.message ?: "credenciales no disponibles"}")
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Error parseando Google ID token", e)
                showError("No se pudo procesar la respuesta de Google")
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