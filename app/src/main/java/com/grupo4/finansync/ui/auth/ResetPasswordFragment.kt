package com.grupo4.finansync.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentResetPasswordBinding
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(
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

        observarEstado()
        configurarEventos()
    }

    private fun configurarEventos() {
        binding.btnGuardarPassword.setOnClickListener {
            val nueva = binding.etNuevaPassword.text.toString()
            val confirmar = binding.etConfirmarPassword.text.toString()

            if (validar(nueva, confirmar)) {
                viewModel.actualizarContrasena(nueva)
            }
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { estado ->

                    when (estado) {
                        is AuthState.Cargando -> {
                            binding.progressReset.visibility = View.VISIBLE
                            binding.btnGuardarPassword.isEnabled = false
                        }

                        is AuthState.PasswordActualizada -> {
                            binding.progressReset.visibility = View.GONE
                            binding.btnGuardarPassword.isEnabled = true

                            Snackbar.make(
                                binding.root,
                                "✅ Contraseña actualizada. Inicia sesión.",
                                Snackbar.LENGTH_SHORT
                            ).show()

                            findNavController().navigate(
                                R.id.action_resetPassword_to_login
                            )
                        }

                        is AuthState.Error -> {
                            binding.progressReset.visibility = View.GONE
                            binding.btnGuardarPassword.isEnabled = true

                            Snackbar.make(
                                binding.root,
                                estado.mensaje,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        else -> {
                            binding.progressReset.visibility = View.GONE
                            binding.btnGuardarPassword.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun validar(
        nueva: String,
        confirmar: String
    ): Boolean {
        var valido = true

        if (nueva.isBlank()) {
            binding.tilNuevaPassword.error = "Ingresa la nueva contraseña"
            valido = false
        } else if (nueva.length < 6) {
            binding.tilNuevaPassword.error = "Mínimo 6 caracteres"
            valido = false
        } else {
            binding.tilNuevaPassword.error = null
        }

        if (confirmar.isBlank()) {
            binding.tilConfirmarPassword.error = "Confirma la contraseña"
            valido = false
        } else if (nueva != confirmar) {
            binding.tilConfirmarPassword.error = "Las contraseñas no coinciden"
            valido = false
        } else {
            binding.tilConfirmarPassword.error = null
        }

        return valido
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}