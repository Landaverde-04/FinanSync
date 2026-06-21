package com.grupo4.finansync

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.sync.SyncManager
import com.grupo4.finansync.ui.ajustes.AjustesFragment
import com.grupo4.finansync.ui.auth.AuthPrefs
import com.grupo4.finansync.ui.menu.MenuFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pantalla principal.
 * Arranca mostrando el menú del módulo.
 */
class MainActivity : AppCompatActivity() {

    // Usuario de prueba temporal.
    // Luego se debe reemplazar por el usuario real de Supabase Auth.
    private val idUsuarioMock = "usuario-prueba-001"

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Aplicar tema ANTES de setContentView ──────────────────────────
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
        // ─────────────────────────────────────────────────────────────────

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.contenedorFragment,
                    MenuFragment()
                )
                .commit()
        }

        sincronizarDesdeLaNube()
    }

    private fun sincronizarDesdeLaNube() {
        lifecycleScope.launch(Dispatchers.IO) {
            val bd = BaseDatos.obtenerInstancia(applicationContext)

            val syncManager = SyncManager(bd)

            val ok = syncManager.sincronizarTodo(idUsuarioMock)

            Log.d(
                "MainActivity",
                "Sincronización inicial: ${if (ok) "OK" else "sin conexión / falló"}"
            )
        }
    }
}