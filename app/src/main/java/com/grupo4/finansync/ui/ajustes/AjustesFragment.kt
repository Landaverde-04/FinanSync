package com.grupo4.finansync.ui.ajustes

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.databinding.FragmentAjustesBinding
import com.grupo4.finansync.ui.auth.AuthActivity
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class AjustesFragment : Fragment() {

    private var _binding: FragmentAjustesBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val PREFS = "finansync_prefs"
        const val KEY_MODO_OSCURO = "modo_oscuro"
        const val KEY_VOZ = "lectura_voz"
        const val KEY_HUELLA = "login_huella"
        private const val KEY_SESION_PREVIA = "finansync_sesion_previa"
    }

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

    private fun configurarToolbar() {
        binding.toolbarAjustes.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun cargarDatosUsuario() {
        // TODO: reemplazar con datos reales de SupabaseCliente.cliente.auth.currentUserOrNull()
        val nombre = "Kevin Landaverde"
        val correo = "kevin@correo.com"
        binding.txtNombreUsuario.text = nombre
        binding.txtCorreoUsuario.text = correo
        binding.txtAvatarInicial.text = nombre.firstOrNull()?.uppercase() ?: "U"
    }

    private fun cargarPreferencias() {
        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        binding.switchModoOscuro.setOnCheckedChangeListener(null)
        binding.switchLecturaVoz.setOnCheckedChangeListener(null)
        binding.switchHuella.setOnCheckedChangeListener(null)

        binding.switchModoOscuro.isChecked = prefs.getBoolean(KEY_MODO_OSCURO, false)
        binding.switchLecturaVoz.isChecked = prefs.getBoolean(KEY_VOZ, true)
        binding.switchHuella.isChecked = prefs.getBoolean(KEY_HUELLA, false)
    }

    private fun configurarSwitches() {
        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        binding.switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MODO_OSCURO, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
        }

        binding.switchLecturaVoz.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VOZ, isChecked).apply()
        }

        binding.switchHuella.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HUELLA, isChecked).apply()
            if (isChecked) {
                Toast.makeText(requireContext(), "Login con huella activado", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                    try {
                        SupabaseCliente.cliente.auth.signOut()
                    } catch (e: Exception) {
                        // Si falla el logout remoto no es crítico
                    }
                    // Borrar flag de sesión previa (oculta el botón de huella en el próximo login)
                    requireContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_SESION_PREVIA, false).apply()

                    // Lanzar AuthActivity y cerrar MainActivity
                    AuthActivity.iniciar(requireContext())
                    requireActivity().finish()
                }
            }
            .show()
    }
}