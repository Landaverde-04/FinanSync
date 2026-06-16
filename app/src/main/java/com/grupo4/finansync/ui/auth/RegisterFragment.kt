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
import com.grupo4.finansync.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.resetearEstado()
        observarEstado()

        binding.btnCrearCuenta.setOnClickListener {
            val nombre = binding.etNombre.text.toString().trim()
            val correo = binding.etCorreo.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()

            if (validarCampos(nombre, correo, password, confirm)) {
                viewModel.registrar(nombre, correo, password)
            }
        }

        binding.tvIrLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { estado ->
                    when (estado) {
                        is AuthState.Cargando -> {
                            binding.progressRegister.visibility = View.VISIBLE
                            binding.btnCrearCuenta.isEnabled = false
                        }
                        is AuthState.Exito -> {
                            binding.progressRegister.visibility = View.GONE
                            Snackbar.make(
                                binding.root,
                                "¡Cuenta creada! Ya puedes iniciar sesión.",
                                Snackbar.LENGTH_SHORT
                            ).show()
                            findNavController().navigate(R.id.action_register_to_login)
                        }
                        is AuthState.Error -> {
                            binding.progressRegister.visibility = View.GONE
                            binding.btnCrearCuenta.isEnabled = true
                            Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                        }
                        is AuthState.Inactivo -> {
                            binding.progressRegister.visibility = View.GONE
                            binding.btnCrearCuenta.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun validarCampos(
        nombre: String,
        correo: String,
        password: String,
        confirm: String
    ): Boolean {
        var valido = true

        if (nombre.isEmpty()) {
            binding.tilNombre.error = "Ingresa tu nombre"
            valido = false
        } else binding.tilNombre.error = null

        if (correo.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.tilCorreo.error = "Ingresa un correo válido"
            valido = false
        } else binding.tilCorreo.error = null

        if (password.length < 6) {
            binding.tilPassword.error = "Mínimo 6 caracteres"
            valido = false
        } else binding.tilPassword.error = null

        if (password != confirm) {
            binding.tilConfirmPassword.error = "Las contraseñas no coinciden"
            valido = false
        } else binding.tilConfirmPassword.error = null

        return valido
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}