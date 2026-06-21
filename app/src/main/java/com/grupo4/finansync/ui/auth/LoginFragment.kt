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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
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
        _binding = FragmentLoginBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.resetearEstado()

        configurarBiometric()
        observarEstado()
        configurarEventos()
    }

    private fun configurarEventos() {
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

        binding.tvOlvidePassword.setOnClickListener {
            mostrarDialogoRecuperacion()
        }
    }

    // ── Observar estado ───────────────────────────────────────────────────
    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
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
                            findNavController().navigate(
                                R.id.action_login_to_home
                            )
                        }

                        is AuthState.Error -> {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.btnBiometric.isEnabled = true

                            Snackbar.make(
                                binding.root,
                                estado.mensaje,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        is AuthState.RecuperacionEnviada -> {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.btnBiometric.isEnabled = true

                            Snackbar.make(
                                binding.root,
                                "✉️ Revisa tu correo. Te enviamos el link para restablecer tu contraseña.",
                                Snackbar.LENGTH_LONG
                            ).show()

                            viewModel.resetearEstado()
                        }

                        is AuthState.Inactivo -> {
                            binding.progressLogin.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            binding.btnBiometric.isEnabled = true
                        }

                        else -> {
                            // PasswordActualizada no aplica en Login
                        }
                    }
                }
            }
        }
    }

    // ── Validación ────────────────────────────────────────────────────────
    private fun validarCampos(
        correo: String,
        password: String
    ): Boolean {
        var valido = true

        if (
            correo.isEmpty() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()
        ) {
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

    // ── Recuperar contraseña ──────────────────────────────────────────────
    private fun mostrarDialogoRecuperacion() {
        val correoActual = binding.etCorreo.text.toString().trim()

        val input = TextInputEditText(requireContext()).apply {
            hint = "Correo electrónico"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(correoActual)
            setPadding(64, 32, 64, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Recuperar contraseña")
            .setMessage("Te enviaremos un link para restablecer tu contraseña.")
            .setView(input)
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Enviar") { _, _ ->
                viewModel.recuperarContrasena(
                    input.text.toString().trim()
                )
            }
            .show()
    }

    // ── Configurar botón biométrico ───────────────────────────────────────
    private fun configurarBiometric() {
        val manager = BiometricManager.from(requireContext())

        val dispositivoTieneHuella = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        val mostrar = dispositivoTieneHuella &&
                viewModel.hayCredencialesBiometricas()

        binding.btnBiometric.visibility =
            if (mostrar) View.VISIBLE else View.GONE

        if (mostrar) {
            val correoGuardado = viewModel.obtenerCorreoGuardado()

            if (correoGuardado.isNotEmpty()) {
                binding.etCorreo.setText(correoGuardado)
            }
        }
    }

    // ── Login con huella ──────────────────────────────────────────────────
    private fun mostrarDialogoHuella() {
        val executor = ContextCompat.getMainExecutor(requireContext())
        val correo = viewModel.obtenerCorreoGuardado()

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)

                viewModel.loginConBiometrico()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)

                if (
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    Toast.makeText(
                        requireContext(),
                        "Error: $errString",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()

                Toast.makeText(
                    requireContext(),
                    "Huella no reconocida. Intenta de nuevo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val prompt = BiometricPrompt(
            this,
            executor,
            callback
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar con huella")
            .setSubtitle(
                if (correo.isNotEmpty()) correo else "FinanSync"
            )
            .setDescription("Confirma tu identidad para acceder a tu cuenta")
            .setNegativeButtonText("Usar contraseña")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        prompt.authenticate(info)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}