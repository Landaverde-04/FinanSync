package com.grupo4.finansync.ui.auth

import android.content.SharedPreferences
import android.util.Patterns
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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Inactivo)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── LOGIN con correo + contraseña ─────────────────────────────────────
    fun login(correo: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            try {
                SupabaseCliente.cliente.auth.signInWith(Email) {
                    email = correo
                    this.password = password
                }

                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                    ?: throw Exception("No se pudo obtener el usuario")

                guardarCredencialesParaBiometria(
                    correo = correo,
                    password = password
                )

                limpiarModoRecuperacionPassword()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    interpretarError(e.message ?: "")
                )
            }
        }
    }

    // ── LOGIN con biométrico ──────────────────────────────────────────────
    fun loginConBiometrico() {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            try {
                val correo = prefs.getString(
                    AuthPrefs.PREF_CORREO_GUARDADO,
                    null
                ) ?: throw Exception("No hay correo guardado")

                val passwordCifrada = prefs.getString(
                    AuthPrefs.PREF_PASSWORD_CIFRADA,
                    null
                ) ?: throw Exception("No hay contraseña guardada")

                val password = BiometricKeyManager.descifrar(passwordCifrada)
                    ?: throw Exception("No se pudieron recuperar las credenciales")

                SupabaseCliente.cliente.auth.signInWith(Email) {
                    email = correo
                    this.password = password
                }

                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                    ?: throw Exception("No se pudo obtener el usuario")

                limpiarModoRecuperacionPassword()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                limpiarCredencialesBiometricas()

                _authState.value = AuthState.Error(
                    "No se pudo iniciar sesión con huella. Ingresa tu contraseña."
                )
            }
        }
    }

    // ── REGISTRO ──────────────────────────────────────────────────────────
    fun registrar(nombre: String, correo: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            try {
                SupabaseCliente.cliente.auth.signUpWith(Email) {
                    email = correo
                    this.password = password
                }

                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                    ?: throw Exception("No se pudo crear la cuenta")

                val usuario = UsuarioEntidad(
                    idUsuario = idUsuario,
                    email = correo,
                    nombreUsuario = nombre,
                    creadoEn = System.currentTimeMillis()
                )

                repositorioUsuario.insertarUsuario(usuario)

                guardarCredencialesParaBiometria(
                    correo = correo,
                    password = password
                )

                limpiarModoRecuperacionPassword()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    interpretarError(e.message ?: "")
                )
            }
        }
    }

    // ── RECUPERAR CONTRASEÑA ──────────────────────────────────────────────
    fun recuperarContrasena(correo: String) {
        val correoLimpio = correo.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches()) {
            _authState.value = AuthState.Error("Ingresa un correo válido")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            try {
                SupabaseCliente.cliente.auth.resetPasswordForEmail(
                    email = correoLimpio,
                    redirectUrl = "finansync://auth"
                )

                _authState.value = AuthState.RecuperacionEnviada

            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    interpretarError(e.message ?: "")
                )
            }
        }
    }

    // ── NUEVA CONTRASEÑA POR RECUPERACIÓN ─────────────────────────────────
    fun actualizarContrasena(nuevaPassword: String) {
        val recuperacionActiva = prefs.getBoolean(
            AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
            false
        )

        if (!recuperacionActiva) {
            _authState.value = AuthState.Error(
                "No hay una recuperación de contraseña activa. Abre nuevamente el enlace del correo."
            )
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            try {
                /*
                 * No validamos con currentUserOrNull().
                 *
                 * En recuperación de contraseña, la sesión puede estar activa
                 * por access_token / refresh_token aunque currentUserOrNull()
                 * todavía devuelva null.
                 *
                 * updateUser() será quien valide si el token de recuperación
                 * realmente sirve.
                 */
                SupabaseCliente.cliente.auth.updateUser {
                    password = nuevaPassword
                }

                limpiarCredencialesBiometricas()
                limpiarModoRecuperacionPassword()

                /*
                 * Cerramos la sesión temporal de recuperación para volver al login.
                 */
                try {
                    SupabaseCliente.cliente.auth.signOut()
                } catch (e: Exception) {
                    // No detener el flujo si falla el signOut
                }

                _authState.value = AuthState.PasswordActualizada

            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    interpretarError(e.message ?: "")
                )
            }
        }
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────
    fun cerrarSesion(mantenerHuella: Boolean = true) {
        viewModelScope.launch {
            try {
                SupabaseCliente.cliente.auth.signOut()
            } catch (e: Exception) {
                // No bloquear salida si falla Supabase
            }

            val huellaActiva = prefs.getBoolean(
                AuthPrefs.PREF_HUELLA_ACTIVA,
                false
            )

            if (!mantenerHuella || !huellaActiva) {
                limpiarCredencialesBiometricas()
            }

            limpiarModoRecuperacionPassword()

            _authState.value = AuthState.Inactivo
        }
    }

    // ── HELPERS PÚBLICOS ──────────────────────────────────────────────────
    fun haySesionActiva(): Boolean {
        return SupabaseCliente.cliente.auth.currentUserOrNull() != null
    }

    fun hayCredencialesBiometricas(): Boolean {
        return prefs.getBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false) &&
                prefs.getString(AuthPrefs.PREF_CORREO_GUARDADO, null) != null &&
                prefs.getString(AuthPrefs.PREF_PASSWORD_CIFRADA, null) != null
    }

    fun hayCredencialesGuardadas(): Boolean {
        return prefs.getString(AuthPrefs.PREF_CORREO_GUARDADO, null) != null &&
                prefs.getString(AuthPrefs.PREF_PASSWORD_CIFRADA, null) != null
    }

    fun obtenerCorreoGuardado(): String {
        return prefs.getString(AuthPrefs.PREF_CORREO_GUARDADO, "") ?: ""
    }

    fun obtenerIdUsuario(): String? {
        return SupabaseCliente.cliente.auth.currentUserOrNull()?.id
    }

    fun activarHuellaLocal(): Boolean {
        if (!hayCredencialesGuardadas()) return false

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, true)
            .apply()

        return true
    }

    fun desactivarHuellaLocal() {
        limpiarCredencialesBiometricas()
    }

    fun resetearEstado() {
        _authState.value = AuthState.Inactivo
    }

    // ── HELPERS PRIVADOS ──────────────────────────────────────────────────
    private fun guardarCredencialesParaBiometria(
        correo: String,
        password: String
    ) {
        val passwordCifrada = BiometricKeyManager.cifrar(password)

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, true)
            .putString(AuthPrefs.PREF_CORREO_GUARDADO, correo)
            .putString(AuthPrefs.PREF_PASSWORD_CIFRADA, passwordCifrada)
            .apply()
    }

    private fun limpiarCredencialesBiometricas() {
        BiometricKeyManager.eliminarClave()

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, false)
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
            .remove(AuthPrefs.PREF_PASSWORD_CIFRADA)
            .apply()

        // El correo se conserva para prellenar el campo del login.
    }

    private fun limpiarModoRecuperacionPassword() {
        prefs.edit()
            .putBoolean(
                AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
                false
            )
            .apply()
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

        error.contains("User not found", ignoreCase = true) ->
            "No existe una cuenta con ese correo"

        error.contains("rate limit", ignoreCase = true) ||
                error.contains("security purposes", ignoreCase = true) ->
            "Espera unos segundos antes de solicitar otro correo"

        error.contains("missing sub", ignoreCase = true) ||
                error.contains("invalid claim", ignoreCase = true) ||
                error.contains("session", ignoreCase = true) ->
            "La sesión de recuperación no está activa. Solicita otro correo y abre el enlace nuevamente."

        else ->
            "Error: $error"
    }
}