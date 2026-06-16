package com.grupo4.finansync.ui.auth

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.modelo.UsuarioEntidad
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repositorioUsuario: RepositorioUsuario,
    private val prefs: SharedPreferences
) : ViewModel() {

    // Estado privado (mutable) — solo el ViewModel lo modifica
    private val _authState = MutableStateFlow<AuthState>(AuthState.Inactivo)
    // Estado público (solo lectura) — los Fragments lo observan
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── LOGIN ──────────────────────────────────────────────────────────────
    fun login(correo: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando
            try {
                // 1. Llamamos a Supabase Auth
                SupabaseCliente.cliente.auth.signInWith(Email) {
                    email = correo
                    this.password = password
                }

                // 2. Obtenemos el ID del usuario autenticado
                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                    ?: throw Exception("No se pudo obtener el usuario")

                // 3. Guardamos flag para el biométrico
                prefs.edit().putBoolean(PREF_SESION_PREVIA, true).apply()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                _authState.value = AuthState.Error(interpretarError(e.message ?: ""))
            }
        }
    }

    // ── REGISTRO ───────────────────────────────────────────────────────────
    fun registrar(nombre: String, correo: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando
            try {
                // 1. Crear usuario en Supabase Auth
                SupabaseCliente.cliente.auth.signUpWith(Email) {
                    email = correo
                    this.password = password
                }

                // 2. Obtener el UUID que Supabase asignó
                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                    ?: throw Exception("No se pudo crear la cuenta")

                // 3. Insertar en la tabla local (Room) y en Supabase (via repositorio)
                val usuario = UsuarioEntidad(
                    idUsuario = idUsuario,
                    email = correo,
                    nombreUsuario = nombre,
                    creadoEn = System.currentTimeMillis()
                )
                repositorioUsuario.insertarUsuario(usuario)

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                _authState.value = AuthState.Error(interpretarError(e.message ?: ""))
            }
        }
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────
    fun cerrarSesion() {
        viewModelScope.launch {
            try {
                SupabaseCliente.cliente.auth.signOut()
            } catch (e: Exception) {
                // Si falla el logout remoto no es crítico
            }
            prefs.edit().putBoolean(PREF_SESION_PREVIA, false).apply()
            _authState.value = AuthState.Inactivo
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    /** Verifica si hay sesión de Supabase activa en el dispositivo */
    fun haySesionActiva(): Boolean =
        SupabaseCliente.cliente.auth.currentUserOrNull() != null

    /** Hubo un login exitoso previo → mostrar botón de huella */
    fun haySessionPrevia(): Boolean =
        prefs.getBoolean(PREF_SESION_PREVIA, false)

    /** Devuelve el UUID del usuario actual (lo usan los demás módulos) */
    fun obtenerIdUsuario(): String? =
        SupabaseCliente.cliente.auth.currentUserOrNull()?.id

    /** Resetea el estado a Inactivo (útil al volver a la pantalla) */
    fun resetearEstado() {
        _authState.value = AuthState.Inactivo
    }

    // ── TRADUCCIÓN DE ERRORES ─────────────────────────────────────────────
    private fun interpretarError(error: String): String = when {
        error.contains("Invalid login credentials", ignoreCase = true) ->
            "Correo o contraseña incorrectos"
        error.contains("User already registered", ignoreCase = true) ->
            "Este correo ya tiene una cuenta. Inicia sesión."
        error.contains("Unable to resolve host", ignoreCase = true) ||
                error.contains("network", ignoreCase = true) ||
                error.contains("SocketException", ignoreCase = true) ->
            "Sin conexión a internet. Verifica tu red."
        error.contains("Email not confirmed", ignoreCase = true) ->
            "Confirma tu correo antes de iniciar sesión"
        error.contains("Password should be at least", ignoreCase = true) ->
            "La contraseña debe tener al menos 6 caracteres"
        else -> "Error: $error"
    }

    companion object {
        private const val PREF_SESION_PREVIA = "finansync_sesion_previa"
    }
}