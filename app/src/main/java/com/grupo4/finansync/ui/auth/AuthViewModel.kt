package com.grupo4.finansync.ui.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.util.Locale

class AuthViewModel(
    private val repositorioUsuario: RepositorioUsuario,
    private val prefs: SharedPreferences,
    private val appContext: Context
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Inactivo)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // ── LOGIN con correo + contraseña ─────────────────────────────────────
    fun login(
        correo: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            if (!hayConexionInternet()) {
                intentarLoginOffline(
                    correo = correo,
                    password = password
                )
                return@launch
            }

            try {
                SupabaseCliente.cliente.auth.signInWith(Email) {
                    email = correo
                    this.password = password
                }

                val idUsuario =
                    SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                        ?: throw Exception("No se pudo obtener el usuario")

                guardarCredencialesParaAccesoLocal(
                    idUsuario = idUsuario,
                    correo = correo,
                    password = password
                )

                limpiarModoRecuperacionPassword()

                prefs.edit()
                    .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
                    .apply()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                val mensaje = e.message ?: ""

                if (esErrorConexion(mensaje)) {
                    intentarLoginOffline(
                        correo = correo,
                        password = password
                    )
                } else {
                    _authState.value = AuthState.Error(
                        interpretarError(mensaje)
                    )
                }
            }
        }
    }

    // ── LOGIN con biométrico ──────────────────────────────────────────────
    fun loginConBiometrico() {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            if (!hayConexionInternet()) {
                intentarLoginBiometricoOffline()
                return@launch
            }

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

                val idUsuario =
                    SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                        ?: throw Exception("No se pudo obtener el usuario")

                guardarCredencialesParaAccesoLocal(
                    idUsuario = idUsuario,
                    correo = correo,
                    password = password
                )

                limpiarModoRecuperacionPassword()

                prefs.edit()
                    .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
                    .apply()

                _authState.value = AuthState.Exito(idUsuario)

            } catch (e: Exception) {
                val mensaje = e.message ?: ""

                if (esErrorConexion(mensaje)) {
                    intentarLoginBiometricoOffline()
                } else {
                    _authState.value = AuthState.Error(
                        "No se pudo iniciar sesión con huella. Ingresa tu contraseña."
                    )
                }
            }
        }
    }

    // ── REGISTRO ──────────────────────────────────────────────────────────
    fun registrar(
        nombre: String,
        correo: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Cargando

            if (!hayConexionInternet()) {
                _authState.value = AuthState.Error(
                    "Necesitas conexión a internet para registrarte."
                )
                return@launch
            }

            try {
                SupabaseCliente.cliente.auth.signUpWith(Email) {
                    email = correo
                    this.password = password
                }

                val idUsuario =
                    SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                        ?: throw Exception("No se pudo crear la cuenta")

                val usuario = UsuarioEntidad(
                    idUsuario = idUsuario,
                    email = correo,
                    nombreUsuario = nombre,
                    creadoEn = System.currentTimeMillis()
                )

                repositorioUsuario.insertarUsuario(usuario)

                guardarCredencialesParaAccesoLocal(
                    idUsuario = idUsuario,
                    correo = correo,
                    password = password
                )

                limpiarModoRecuperacionPassword()

                prefs.edit()
                    .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
                    .apply()

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

            if (!hayConexionInternet()) {
                _authState.value = AuthState.Error(
                    "Necesitas conexión a internet para recuperar tu contraseña."
                )
                return@launch
            }

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

            if (!hayConexionInternet()) {
                _authState.value = AuthState.Error(
                    "Necesitas conexión a internet para actualizar tu contraseña."
                )
                return@launch
            }

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

                limpiarCredencialesLocales()
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
    fun cerrarSesion(
        mantenerHuella: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                SupabaseCliente.cliente.auth.signOut()
            } catch (e: Exception) {
                // No bloquear salida si falla Supabase
            }

            if (!mantenerHuella) {
                desactivarHuellaLocal()
            }

            prefs.edit()
                .putBoolean(AuthPrefs.PREF_SESION_PREVIA, false)
                .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
                .apply()

            limpiarModoRecuperacionPassword()

            _authState.value = AuthState.Inactivo
        }
    }

    // ── HELPERS PÚBLICOS ──────────────────────────────────────────────────
    fun haySesionActiva(): Boolean {
        val sesionLocalActiva = prefs.getBoolean(
            AuthPrefs.PREF_SESION_PREVIA,
            false
        )

        val ultimoUsuario = prefs.getString(
            AuthPrefs.PREF_ULTIMO_USUARIO_ID,
            null
        )

        return sesionLocalActiva && !ultimoUsuario.isNullOrBlank()
    }

    fun hayCredencialesBiometricas(): Boolean {
        val ultimoIdUsuario = prefs.getString(
            AuthPrefs.PREF_ULTIMO_USUARIO_ID,
            null
        )

        val idHuella = prefs.getString(
            AuthPrefs.PREF_HUELLA_USUARIO_ID,
            null
        )

        return prefs.getBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false) &&
                !ultimoIdUsuario.isNullOrBlank() &&
                idHuella == ultimoIdUsuario &&
                prefs.getString(AuthPrefs.PREF_CORREO_GUARDADO, null) != null &&
                prefs.getString(AuthPrefs.PREF_PASSWORD_CIFRADA, null) != null
    }

    fun hayCredencialesGuardadas(): Boolean {
        return prefs.getString(AuthPrefs.PREF_CORREO_GUARDADO, null) != null &&
                prefs.getString(AuthPrefs.PREF_PASSWORD_CIFRADA, null) != null &&
                prefs.getString(AuthPrefs.PREF_ULTIMO_USUARIO_ID, null) != null
    }

    fun obtenerCorreoGuardado(): String {
        return prefs.getString(
            AuthPrefs.PREF_CORREO_GUARDADO,
            ""
        ) ?: ""
    }

    fun obtenerIdUsuario(): String? {
        return SupabaseCliente.cliente.auth.currentUserOrNull()?.id
            ?: prefs.getString(
                AuthPrefs.PREF_ULTIMO_USUARIO_ID,
                null
            )
    }

    fun activarHuellaLocal(): Boolean {
        if (!hayCredencialesGuardadas()) {
            return false
        }

        val idUsuario = obtenerIdUsuario()
            ?: return false

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, true)
            .putString(
                AuthPrefs.PREF_HUELLA_USUARIO_ID,
                idUsuario
            )
            .apply()

        return true
    }

    fun desactivarHuellaLocal() {
        prefs.edit()
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
            .remove(AuthPrefs.PREF_HUELLA_USUARIO_ID)
            .apply()
    }

    fun resetearEstado() {
        _authState.value = AuthState.Inactivo
    }

    // ── HELPERS PRIVADOS ──────────────────────────────────────────────────
    private fun guardarCredencialesParaAccesoLocal(
        idUsuario: String,
        correo: String,
        password: String
    ) {
        val passwordCifrada = BiometricKeyManager.cifrar(password)

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, true)
            .putBoolean(AuthPrefs.PREF_ACCESO_OFFLINE_ACTIVO, true)
            .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
            .putString(
                AuthPrefs.PREF_ULTIMO_USUARIO_ID,
                idUsuario
            )
            .putString(
                AuthPrefs.PREF_ULTIMO_CORREO,
                normalizarCorreo(correo)
            )
            .putString(
                AuthPrefs.PREF_CORREO_GUARDADO,
                correo
            )
            .putString(
                AuthPrefs.PREF_PASSWORD_CIFRADA,
                passwordCifrada
            )
            .apply()
    }

    private fun intentarLoginOffline(
        correo: String,
        password: String
    ) {
        val accesoOfflineActivo = prefs.getBoolean(
            AuthPrefs.PREF_ACCESO_OFFLINE_ACTIVO,
            false
        )

        val ultimoIdUsuario = prefs.getString(
            AuthPrefs.PREF_ULTIMO_USUARIO_ID,
            null
        )

        val ultimoCorreo = prefs.getString(
            AuthPrefs.PREF_ULTIMO_CORREO,
            null
        )

        val passwordCifrada = prefs.getString(
            AuthPrefs.PREF_PASSWORD_CIFRADA,
            null
        )

        if (
            !accesoOfflineActivo ||
            ultimoIdUsuario.isNullOrBlank() ||
            ultimoCorreo.isNullOrBlank() ||
            passwordCifrada.isNullOrBlank()
        ) {
            _authState.value = AuthState.Error(
                "Sin conexión. Debes iniciar sesión con internet al menos una vez."
            )
            return
        }

        if (normalizarCorreo(correo) != ultimoCorreo) {
            _authState.value = AuthState.Error(
                "Sin conexión. Solo puede entrar el último usuario usado en este dispositivo."
            )
            return
        }

        val passwordGuardada =
            BiometricKeyManager.descifrar(passwordCifrada)

        if (passwordGuardada == null || passwordGuardada != password) {
            _authState.value = AuthState.Error(
                "Contraseña incorrecta para el acceso offline."
            )
            return
        }

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, true)
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, true)
            .apply()

        _authState.value = AuthState.Exito(ultimoIdUsuario)
    }

    private fun intentarLoginBiometricoOffline() {
        val accesoOfflineActivo = prefs.getBoolean(
            AuthPrefs.PREF_ACCESO_OFFLINE_ACTIVO,
            false
        )

        val ultimoIdUsuario = prefs.getString(
            AuthPrefs.PREF_ULTIMO_USUARIO_ID,
            null
        )

        if (
            !accesoOfflineActivo ||
            ultimoIdUsuario.isNullOrBlank() ||
            !hayCredencialesBiometricas()
        ) {
            _authState.value = AuthState.Error(
                "Sin conexión. No hay un acceso offline válido para este usuario."
            )
            return
        }

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, true)
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, true)
            .apply()

        _authState.value = AuthState.Exito(ultimoIdUsuario)
    }

    private fun limpiarCredencialesLocales() {
        BiometricKeyManager.eliminarClave()

        prefs.edit()
            .putBoolean(AuthPrefs.PREF_SESION_PREVIA, false)
            .putBoolean(AuthPrefs.PREF_HUELLA_ACTIVA, false)
            .putBoolean(AuthPrefs.PREF_ACCESO_OFFLINE_ACTIVO, false)
            .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
            .remove(AuthPrefs.PREF_HUELLA_USUARIO_ID)
            .remove(AuthPrefs.PREF_PASSWORD_CIFRADA)
            .remove(AuthPrefs.PREF_ULTIMO_USUARIO_ID)
            .remove(AuthPrefs.PREF_ULTIMO_CORREO)
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

    private fun normalizarCorreo(correo: String): String {
        return correo.trim().lowercase(Locale.ROOT)
    }

    private fun hayConexionInternet(): Boolean {
        return try {
            val connectivityManager =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                        as ConnectivityManager

            val network =
                connectivityManager.activeNetwork ?: return false

            val capabilities =
                connectivityManager.getNetworkCapabilities(network)
                    ?: return false

            capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )

        } catch (e: Exception) {
            false
        }
    }

    private fun esErrorConexion(error: String): Boolean {
        return error.contains("Unable to resolve host", ignoreCase = true) ||
                error.contains("network", ignoreCase = true) ||
                error.contains("SocketException", ignoreCase = true) ||
                error.contains("timeout", ignoreCase = true) ||
                error.contains("Failed to connect", ignoreCase = true) ||
                error.contains(
                    "No address associated with hostname",
                    ignoreCase = true
                )
    }

    // ── TRADUCCIÓN DE ERRORES ─────────────────────────────────────────────
    private fun interpretarError(error: String): String = when {
        error.contains("Invalid login credentials", ignoreCase = true) ->
            "Correo o contraseña incorrectos"

        error.contains("User already registered", ignoreCase = true) ->
            "Este correo ya tiene una cuenta. Inicia sesión."

        error.contains("Unable to resolve host", ignoreCase = true) ||
                error.contains("network", ignoreCase = true) ||
                error.contains("SocketException", ignoreCase = true) ||
                error.contains("timeout", ignoreCase = true) ||
                error.contains("Failed to connect", ignoreCase = true) ||
                error.contains(
                    "No address associated with hostname",
                    ignoreCase = true
                ) ->
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