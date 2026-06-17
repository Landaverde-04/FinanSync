package com.grupo4.finansync

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.sync.SyncManager
import com.grupo4.finansync.ui.menu.MenuFragment
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.grupo4.finansync.ui.ajustes.AjustesFragment

/**
 * Pantalla principal (temporal).
 * Arranca mostrando el MENÚ del módulo, desde donde se navega a las pantallas.
 * Cuando M2 monte el Navigation Component, esta Activity hospedará el grafo de navegación.
 */
class MainActivity : AppCompatActivity() {

    // Usuario de prueba (mock). Vendrá de la sesión real de Supabase Auth (M2).
    private val idUsuarioMock = "usuario-prueba-001"

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Aplicar tema ANTES de setContentView ──────────────────────────
        // Si se hace después, la Activity ya se dibujó con el tema anterior
        val prefs = getSharedPreferences(AjustesFragment.PREFS, Context.MODE_PRIVATE)
        val modoOscuro = prefs.getBoolean(AjustesFragment.KEY_MODO_OSCURO, false)
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        // ─────────────────────────────────────────────────────────────────
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cargamos el menú dentro del contenedor, solo la primera vez
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.contenedorFragment, MenuFragment())
                .commit()
        }

        // Sincronizar de la nube al abrir la app (en segundo plano).
        // Trae a Room lo que esté en Supabase. Si no hay red, simplemente no hace nada.
        sincronizarDesdeLaNube()
    }

    private fun sincronizarDesdeLaNube() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Obtener el ID del usuario autenticado real
                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                if (idUsuario == null) {
                    Log.d("MainActivity", "Sin sesión activa, omitiendo sincronización")
                    return@launch
                }
                val bd = BaseDatos.obtenerInstancia(applicationContext)
                val syncManager = SyncManager(bd)
                val ok = syncManager.sincronizarTodo(idUsuario)
                Log.d("MainActivity", "Sincronización inicial: ${if (ok) "OK" else "sin conexión / falló"}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al sincronizar: ${e.message}")
            }
        }
    }
}
