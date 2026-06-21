package com.grupo4.finansync.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.grupo4.finansync.MainActivity
import com.grupo4.finansync.R
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.ui.ajustes.AjustesFragment
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        aplicarTema()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        manejarDeepLinkRecuperacion(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        manejarDeepLinkRecuperacion(intent)
    }

    // ── Tema ──────────────────────────────────────────────────────────────
    private fun aplicarTema() {
        val prefs = getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        val modoOscuro = prefs.getBoolean(
            AjustesFragment.KEY_MODO_OSCURO,
            false
        )

        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    // ── Deep link recuperación ────────────────────────────────────────────
    private fun manejarDeepLinkRecuperacion(intent: Intent?) {
        val uri = intent?.data ?: return

        if (!esDeepLinkAuth(uri)) return

        val datosLink = obtenerDatosDelDeepLink(uri)

        val esRecuperacion = esRecuperacionPassword(datosLink)

        val code = obtenerParametro(uri, "code")

        val accessToken = obtenerParametro(uri, "access_token")
        val refreshToken = obtenerParametro(uri, "refresh_token")
        val expiresIn = obtenerParametro(uri, "expires_in")
            ?.toLongOrNull()
            ?: 3600L
        val tokenType = obtenerParametro(uri, "token_type")
            ?: "Bearer"

        val tieneCode = !code.isNullOrBlank()

        val tieneTokens = !accessToken.isNullOrBlank() &&
                !refreshToken.isNullOrBlank()

        if (!esRecuperacion && !tieneCode && !tieneTokens) return

        if (!tieneCode && !tieneTokens) {
            Toast.makeText(
                this,
                "El enlace de recuperación no es válido. Solicita uno nuevo.",
                Toast.LENGTH_LONG
            ).show()

            navegarALogin()
            return
        }

        lifecycleScope.launch {
            prepararSesionDeRecuperacion(
                code = code,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                tokenType = tokenType
            )
        }
    }

    private suspend fun prepararSesionDeRecuperacion(
        code: String?,
        accessToken: String?,
        refreshToken: String?,
        expiresIn: Long,
        tokenType: String
    ) {
        val prefs = getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        /*
         * Primero desactivamos el modo recuperación para evitar
         * que quede activo por error.
         */
        prefs.edit()
            .putBoolean(
                AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
                false
            )
            .apply()

        /*
         * Si había un usuario logueado, se cierra su sesión.
         * Esto evita que se cambie la contraseña del usuario equivocado.
         */
        try {
            SupabaseCliente.cliente.auth.signOut()
        } catch (e: Exception) {
            // Ignorar si no había sesión activa
        }

        try {
            /*
             * Caso 1:
             * El enlace trae code.
             * Se intercambia por sesión.
             */
            if (!code.isNullOrBlank()) {
                SupabaseCliente.cliente.auth.exchangeCodeForSession(code)
            }

            /*
             * Caso 2:
             * El enlace trae access_token y refresh_token.
             * Se importa la sesión manualmente.
             */
            else if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                SupabaseCliente.cliente.auth.importSession(
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        tokenType = tokenType,
                        user = null
                    )
                )
            }

            else {
                throw Exception("Enlace sin code ni tokens")
            }

            /*
             * Si llegamos aquí, la sesión temporal de recuperación
             * ya pertenece al usuario dueño del enlace.
             */
            prefs.edit()
                .putBoolean(
                    AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
                    true
                )
                .apply()

            navegarAResetPassword()

        } catch (e: Exception) {
            prefs.edit()
                .putBoolean(
                    AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
                    false
                )
                .apply()

            Toast.makeText(
                this,
                "No se pudo validar el enlace. Solicita uno nuevo.",
                Toast.LENGTH_LONG
            ).show()

            navegarALogin()
        }
    }

    private fun esDeepLinkAuth(uri: Uri): Boolean {
        return uri.scheme == "finansync" && uri.host == "auth"
    }

    private fun obtenerDatosDelDeepLink(uri: Uri): String {
        val fragment = uri.fragment ?: ""
        val query = uri.query ?: ""

        return "$fragment&$query"
    }

    private fun esRecuperacionPassword(datosLink: String): Boolean {
        return datosLink.contains("type=recovery", ignoreCase = true) ||
                datosLink.contains("recovery", ignoreCase = true)
    }

    private fun obtenerParametro(
        uri: Uri,
        nombre: String
    ): String? {
        /*
         * Caso 1:
         * finansync://auth?code=xxxx
         */
        uri.getQueryParameter(nombre)?.let {
            return it
        }

        /*
         * Caso 2:
         * finansync://auth#access_token=xxx&refresh_token=yyy&type=recovery
         */
        val fragment = uri.fragment ?: return null

        val parametros = fragment.split("&")

        for (parametro in parametros) {
            val partes = parametro.split(
                "=",
                limit = 2
            )

            if (partes.size == 2 && partes[0] == nombre) {
                return Uri.decode(partes[1])
            }
        }

        return null
    }

    // ── Navegación ────────────────────────────────────────────────────────
    private fun navegarAResetPassword() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

        val navController = navHost?.navController ?: return

        navController.navigate(
            R.id.resetPasswordFragment,
            null,
            navOptions {
                popUpTo(R.id.nav_graph) {
                    inclusive = true
                }
            }
        )
    }

    private fun navegarALogin() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

        val navController = navHost?.navController ?: return

        navController.navigate(
            R.id.loginFragment,
            null,
            navOptions {
                popUpTo(R.id.nav_graph) {
                    inclusive = true
                }
            }
        )
    }

    // ── Ir a Main ─────────────────────────────────────────────────────────
    fun irAMain() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()
    }

    companion object {
        fun iniciar(context: Context) {
            val intent = Intent(
                context,
                AuthActivity::class.java
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            )

            context.startActivity(intent)
        }
    }
}