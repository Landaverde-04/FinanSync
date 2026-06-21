package com.grupo4.finansync.ui.ajustes

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.databinding.FragmentAjustesBinding
import com.grupo4.finansync.ui.auth.AuthActivity
import com.grupo4.finansync.ui.auth.AuthPrefs
import com.grupo4.finansync.ui.auth.BiometricKeyManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class AjustesFragment : Fragment() {

    private var _binding: FragmentAjustesBinding? = null
    private val binding get() = _binding!!

    private var ignorarCambioHuella = false

    companion object {
        const val KEY_MODO_OSCURO = "modo_oscuro"
        const val KEY_VOZ = "lectura_voz"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAjustesBinding.inflate(
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

        configurarToolbar()
        cargarDatosUsuario()
        cargarPreferencias()
        configurarSwitches()
        configurarCerrarSesion()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────
    private fun configurarToolbar() {
        binding.toolbarAjustes.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // ── Datos del usuario ─────────────────────────────────────────────────
    private fun cargarDatosUsuario() {
        val usuario = SupabaseCliente.cliente.auth.currentUserOrNull()

        val correo = usuario?.email ?: "correo no disponible"

        val nombre = correo
            .substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }

        binding.txtNombreUsuario.text = nombre
        binding.txtCorreoUsuario.text = correo
        binding.txtAvatarInicial.text =
            nombre.firstOrNull()?.uppercase() ?: "U"
    }

    // ── Cargar preferencias ───────────────────────────────────────────────
    private fun cargarPreferencias() {
        val prefs = requireContext().getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        binding.switchModoOscuro.setOnCheckedChangeListener(null)
        binding.switchLecturaVoz.setOnCheckedChangeListener(null)
        binding.switchHuella.setOnCheckedChangeListener(null)

        binding.switchModoOscuro.isChecked =
            prefs.getBoolean(KEY_MODO_OSCURO, false)

        binding.switchLecturaVoz.isChecked =
            prefs.getBoolean(KEY_VOZ, true)

        binding.switchHuella.isChecked =
            prefs.getBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
    }

    // ── Configurar switches ───────────────────────────────────────────────
    private fun configurarSwitches() {
        val prefs = requireContext().getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        binding.switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(KEY_MODO_OSCURO, isChecked)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
            )

            requireActivity().recreate()
        }

        binding.switchLecturaVoz.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(KEY_VOZ, isChecked)
                .apply()
        }

        binding.switchHuella.setOnCheckedChangeListener { _, isChecked ->
            if (ignorarCambioHuella) return@setOnCheckedChangeListener

            if (isChecked) {
                activarLoginConHuella()
            } else {
                desactivarLoginConHuella()
            }
        }
    }

    // ── Cambiar switch sin disparar listener ──────────────────────────────
    private fun cambiarSwitchHuella(valor: Boolean) {
        ignorarCambioHuella = true
        binding.switchHuella.isChecked = valor
        ignorarCambioHuella = false
    }

    // ── Activar huella ────────────────────────────────────────────────────
    private fun activarLoginConHuella() {
        val context = requireContext()

        val prefs = context.getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        val manager = BiometricManager.from(context)

        val puedeUsarHuella = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        if (!puedeUsarHuella) {
            cambiarSwitchHuella(false)

            Toast.makeText(
                context,
                "Debes tener una huella configurada en el teléfono",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val correoGuardado = prefs.getString(
            AuthPrefs.PREF_CORREO_GUARDADO,
            null
        )

        val passwordGuardada = prefs.getString(
            AuthPrefs.PREF_PASSWORD_CIFRADA,
            null
        )

        if (correoGuardado.isNullOrEmpty() || passwordGuardada.isNullOrEmpty()) {
            cambiarSwitchHuella(false)

            Toast.makeText(
                context,
                "Primero inicia sesión con correo y contraseña para activar la huella",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)

                prefs.edit()
                    .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, true)
                    .putBoolean(AuthPrefs.PREF_SESION_PREVIA, true)
                    .apply()

                cambiarSwitchHuella(true)

                Toast.makeText(
                    context,
                    "Login con huella activado",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)

                cambiarSwitchHuella(false)

                if (
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    Toast.makeText(
                        context,
                        "Error: $errString",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()

                Toast.makeText(
                    context,
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
            .setTitle("Activar login con huella")
            .setSubtitle(correoGuardado)
            .setDescription("Confirma tu huella para activar el acceso rápido")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        prompt.authenticate(info)
    }

    // ── Desactivar huella ─────────────────────────────────────────────────
    private fun desactivarLoginConHuella() {
        val prefs = requireContext().getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
            .remove(AuthPrefs.PREF_PASSWORD_CIFRADA)
            .apply()

        BiometricKeyManager.eliminarClave()

        Toast.makeText(
            requireContext(),
            "Login con huella desactivado",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ── Cerrar sesión ─────────────────────────────────────────────────────
    private fun configurarCerrarSesion() {
        binding.btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun cerrarSesion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Quieres salir de tu cuenta?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->

                lifecycleScope.launch {
                    val prefs = requireContext().getSharedPreferences(
                        AuthPrefs.PREFS,
                        Context.MODE_PRIVATE
                    )

                    val huellaActiva = prefs.getBoolean(
                        AuthPrefs.PREF_HUELLA_ACTIVA,
                        false
                    )

                    try {
                        SupabaseCliente.cliente.auth.signOut()
                    } catch (e: Exception) {
                        // Si falla el logout remoto no detenemos la salida
                    }

                    /*
                     Si la huella está activa, conservamos las credenciales cifradas.
                     Así el usuario puede volver a entrar con huella.
                    */
                    if (!huellaActiva) {
                        BiometricKeyManager.eliminarClave()

                        prefs.edit()
                            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, false)
                            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
                            .remove(AuthPrefs.PREF_PASSWORD_CIFRADA)
                            .apply()
                    }

                    AuthActivity.iniciar(requireContext())
                    requireActivity().finish()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}