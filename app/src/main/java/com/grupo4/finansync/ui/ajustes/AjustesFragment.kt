package com.grupo4.finansync.ui.ajustes

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
//import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.databinding.FragmentAjustesBinding

/**
 * Pantalla 4 — Perfil / Ajustes.
 *
 * Persiste las preferencias del usuario en SharedPreferences:
 *  - Modo oscuro  → AppCompatDelegate.setDefaultNightMode()
 *  - Lectura voz  → leída por los fragments de detalle/reportes antes de hablar
 *  - Huella       → leída por el módulo de autenticación (M2)
 *
 * El nombre y correo se cargan de la sesión de Supabase Auth (GoTrue).
 * En esta entrega se muestran como placeholder; integrar con SupabaseCliente
 * cuando M2 exponga el usuario actual.
 */
class AjustesFragment : Fragment() {

    private var _binding: FragmentAjustesBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val PREFS = "finansync_prefs"
        const val KEY_MODO_OSCURO = "modo_oscuro"
        const val KEY_VOZ = "lectura_voz"
        const val KEY_HUELLA = "login_huella"
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAjustesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarToolbar()
        cargarDatosUsuario()
        cargarPreferencias()
        configurarSwitches()
        configurarCerrarSesion()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Configuración ──────────────────────────────────────────────────────
    private fun configurarToolbar() {
        binding.toolbarAjustes.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Carga nombre y correo desde Supabase Auth.
     * Hasta que M2 exponga el usuario, se muestra un placeholder.
     */
    private fun cargarDatosUsuario() {
        // TODO: obtener de SupabaseCliente.instancia.gotrue.currentUserOrNull()
        val nombre = "Kevin Landaverde"
        val correo = "kevin@correo.com"

        binding.txtNombreUsuario.text = nombre
        binding.txtCorreoUsuario.text = correo
        // Inicial del avatar
        binding.txtAvatarInicial.text = nombre.firstOrNull()?.uppercase() ?: "U"
    }

    private fun cargarPreferencias() {
        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Evitar disparar el listener al setear el estado inicial
        binding.switchModoOscuro.setOnCheckedChangeListener(null)
        binding.switchLecturaVoz.setOnCheckedChangeListener(null)
        binding.switchHuella.setOnCheckedChangeListener(null)

        binding.switchModoOscuro.isChecked = prefs.getBoolean(KEY_MODO_OSCURO, false)
        binding.switchLecturaVoz.isChecked = prefs.getBoolean(KEY_VOZ, true)
        binding.switchHuella.isChecked = prefs.getBoolean(KEY_HUELLA, false)
    }

    private fun configurarSwitches() {
        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Modo oscuro → cambia el tema de toda la app inmediatamente
        binding.switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MODO_OSCURO, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            // ── Forzar recreación de la Activity para que el tema se aplique ──
            requireActivity().recreate()
        }

        // Lectura por voz → se persiste; los fragmentos la consultan antes de hablar
        binding.switchLecturaVoz.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VOZ, isChecked).apply()
        }

        // Huella → notifica que se activó (la lógica de BiometricPrompt va en M2/login)
        binding.switchHuella.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HUELLA, isChecked).apply()
            if (isChecked) {
                Toast.makeText(
                    requireContext(),
                    "Login con huella activado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun configurarCerrarSesion() {
        binding.btnCerrarSesion.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres salir de tu cuenta?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    cerrarSesion()
                }
                .show()
        }
    }

    private fun cerrarSesion() {
        // TODO: SupabaseCliente.instancia.gotrue.logout() (suspend → coroutine en M2)
        // Por ahora solo navega de regreso al inicio (M2 maneja el flujo de auth)
        Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
        // findNavController().navigate(R.id.action_ajustes_to_login)
    }
}