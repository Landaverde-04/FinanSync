package com.grupo4.finansync.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }

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

        // Resetear estado al entrar a la pantalla
        viewModel.resetearEstado()

        configurarBiometric()
        observarEstado()

        binding.btnLogin.setOnClickListener {
            val correo = binding.etCorreo.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (validarCampos(correo, password)) {
                viewModel.login(correo, password)
            }
        }

        binding.tvIrRegistro.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnBiometric.setOnClickListener {
            mostrarDialogoHuella()
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle garantiza que dejamos de observar cuando la pantalla
            // no está visible, evitando navegaciones duplicadas
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { estado ->
                    when (estado) {
                        is AuthState.Cargando -> {
                            binding.progressLogin.visibility = View.VISIBLE
                            binding.btnLogin.isEnabled = false
                            binding.btnBiometric.isEnabled = false
                        }
                        is AuthState.Exito -> {
                            binding.progressLogin.visibility = View.GONE
                            findNavController().navigate(R.id.action_login_to_home)
                        }
                        is AuthState.Error -> {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.btnBiometric.isEnabled = true
                            Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                        }
                        is AuthState.Inactivo -> {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.btnBiometric.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun validarCampos(correo: String, password: String): Boolean {
        var valido = true
        if (correo.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.tilCorreo.error = "Ingresa un correo válido"
            valido = false
        } else {
            binding.tilCorreo.error = null
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingresa tu contraseña"
            valido = false
        } else {
            binding.tilPassword.error = null
        }
        return valido
    }

    private fun configurarBiometric() {
        val manager = BiometricManager.from(requireContext())
        val dispositivoTieneHuella = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        // Botón visible solo si el dispositivo tiene huella Y hubo login previo
        binding.btnBiometric.visibility =
            if (dispositivoTieneHuella && viewModel.haySessionPrevia()) View.VISIBLE
            else View.GONE
    }

    private fun mostrarDialogoHuella() {
        val executor = ContextCompat.getMainExecutor(requireContext())

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Huella correcta → la sesión de Supabase ya está guardada localmente
                findNavController().navigate(R.id.action_login_to_home)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(requireContext(), "Error: $errString", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onAuthenticationFailed() {
                Toast.makeText(requireContext(), "Huella no reconocida", Toast.LENGTH_SHORT).show()
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identificación con huella")
            .setSubtitle("Usa tu huella digital para entrar")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(info)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}