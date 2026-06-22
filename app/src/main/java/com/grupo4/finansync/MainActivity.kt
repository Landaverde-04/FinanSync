package com.grupo4.finansync

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.sync.SyncManager
import com.grupo4.finansync.ui.ajustes.AjustesFragment
import com.grupo4.finansync.ui.auth.AuthActivity
import com.grupo4.finansync.ui.auth.AuthPrefs
import com.grupo4.finansync.ui.categoria.MenuM3Fragment
import com.grupo4.finansync.ui.dashboards.dashboardFragment
import com.grupo4.finansync.ui.dashboards.fragmentGraficosReportes
import com.grupo4.finansync.ui.dashboards.listaPlanesFragment
import com.grupo4.finansync.ui.historial.HistorialFragment
import com.grupo4.finansync.ui.transaccion.NuevaTransaccionFragment
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Intent
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pantalla principal.
 * Después del login muestra primero el Dashboard.
 * Las demás opciones se abren desde el menú lateral.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        aplicarTema()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        topAppBar = findViewById(R.id.topAppBar)
        navigationView = findViewById(R.id.navigationView)

        configurarMenuLateral()
        configurarCambiosDeBackStack()

        if (savedInstanceState == null) {
            abrirDashboard()
        }

        sincronizarDesdeLaNube()
    }

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

    private fun configurarMenuLateral() {
        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->

            when (item.itemId) {

                R.id.nav_dashboard -> {
                    abrirDashboard()
                }

                R.id.nav_agregar -> {
                    abrirOpcionMenu(
                        fragment = NuevaTransaccionFragment(),
                        titulo = "Agregar transacción",
                        idMenu = R.id.nav_agregar
                    )
                }

                R.id.nav_movimientos -> {
                    abrirOpcionMenu(
                        fragment = HistorialFragment(),
                        titulo = "Movimientos",
                        idMenu = R.id.nav_movimientos
                    )
                }

                R.id.nav_reportes -> {
                    abrirOpcionMenu(
                        fragment = fragmentGraficosReportes(),
                        titulo = "Reportes",
                        idMenu = R.id.nav_reportes
                    )
                }

                R.id.nav_categorias -> {
                    abrirOpcionMenu(
                        fragment = MenuM3Fragment(),
                        titulo = "Categorías y presupuestos",
                        idMenu = R.id.nav_categorias
                    )
                }

                R.id.nav_ahorro -> {
                    abrirOpcionMenu(
                        fragment = listaPlanesFragment(),
                        titulo = "Planes de Ahorro",
                        idMenu = R.id.nav_ahorro
                    )
                }

                R.id.nav_ajustes -> {
                    abrirOpcionMenu(
                        fragment = AjustesFragment(),
                        titulo = "Ajustes",
                        idMenu = R.id.nav_ajustes
                    )
                }

                R.id.nav_cerrar_sesion -> {
                    cerrarSesion()
                }
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun abrirDashboard() {
        limpiarBackStack()

        abrirFragment(
            fragment = dashboardFragment(),
            titulo = "Dashboard",
            agregarAlBackStack = false
        )

        navigationView.setCheckedItem(R.id.nav_dashboard)
    }

    private fun abrirOpcionMenu(
        fragment: Fragment,
        titulo: String,
        idMenu: Int
    ) {
        limpiarBackStack()

        abrirFragment(
            fragment = fragment,
            titulo = titulo,
            agregarAlBackStack = true
        )

        navigationView.setCheckedItem(idMenu)
    }

    private fun abrirFragment(
        fragment: Fragment,
        titulo: String,
        agregarAlBackStack: Boolean
    ) {
        topAppBar.title = titulo

        val transaccion = supportFragmentManager.beginTransaction()
            .replace(
                R.id.contenedorFragment,
                fragment
            )

        if (agregarAlBackStack) {
            transaccion.addToBackStack(null)
        }

        transaccion.commit()
    }

    private fun limpiarBackStack() {
        supportFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    private fun configurarCambiosDeBackStack() {
        supportFragmentManager.addOnBackStackChangedListener {
            actualizarTituloSegunFragment()
        }
    }

    private fun actualizarTituloSegunFragment() {
        when (supportFragmentManager.findFragmentById(R.id.contenedorFragment)) {

            is dashboardFragment -> {
                topAppBar.title = "Dashboard"
                navigationView.setCheckedItem(R.id.nav_dashboard)
            }

            is NuevaTransaccionFragment -> {
                topAppBar.title = "Agregar transacción"
                navigationView.setCheckedItem(R.id.nav_agregar)
            }

            is HistorialFragment -> {
                topAppBar.title = "Movimientos"
                navigationView.setCheckedItem(R.id.nav_movimientos)
            }

            is fragmentGraficosReportes -> {
                topAppBar.title = "Reportes"
                navigationView.setCheckedItem(R.id.nav_reportes)
            }

            is MenuM3Fragment -> {
                topAppBar.title = "Categorías y presupuestos"
                navigationView.setCheckedItem(R.id.nav_categorias)
            }

            is AjustesFragment -> {
                topAppBar.title = "Ajustes"
                navigationView.setCheckedItem(R.id.nav_ajustes)
            }

            is listaPlanesFragment -> {
                topAppBar.title = "Planes de ahorro"
            }
        }
    }

    private fun cerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Quieres salir de tu cuenta?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->

                lifecycleScope.launch {
                    val prefs = getSharedPreferences(
                        AuthPrefs.PREFS,
                        Context.MODE_PRIVATE
                    )

                    /*
                     * Primero cerramos la sesión local.
                     *
                     * Esto es lo más importante para que AuthActivity
                     * no vuelva a mandar al Dashboard cuando estás offline.
                     */
                    prefs.edit()
                        .putBoolean(AuthPrefs.PREF_SESION_PREVIA, false)
                        .putBoolean(AuthPrefs.PREF_MODO_OFFLINE, false)
                        .putBoolean(
                            AuthPrefs.PREF_RECUPERACION_PASSWORD_ACTIVA,
                            false
                        )
                        .apply()

                    /*
                     * Luego intentamos cerrar Supabase.
                     *
                     * Si no hay internet, no dejamos que esto bloquee
                     * la salida al login.
                     */
                    withTimeoutOrNull(1200L) {
                        try {
                            SupabaseCliente.cliente.auth.signOut()
                        } catch (e: Exception) {
                            Log.e(
                                "MainActivity",
                                "Error al cerrar sesión en Supabase: ${e.message}"
                            )
                        }
                    }

                    irAlLogin()
                }
            }
            .show()
    }

    private fun irAlLogin() {
        val intent = Intent(
            this,
            AuthActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun sincronizarDesdeLaNube() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                if (idUsuario == null) {
                    Log.d("MainActivity", "Sin sesión activa, omitiendo sincronización")
                    return@launch
                }

                // Evitar sincronizar de nuevo si AuthActivity ya lo hizo hace poco
                val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val ultimaSync = prefs.getLong("ultima_sync_$idUsuario", 0L)
                val ahora = System.currentTimeMillis()

                if (ahora - ultimaSync < 10_000L) {
                    Log.d("MainActivity", "Sincronización reciente, se omite duplicado")
                    return@launch
                }

                val bd = BaseDatos.obtenerInstancia(applicationContext)
                val ok = SyncManager(bd).sincronizarTodo(idUsuario)
                prefs.edit().putLong("ultima_sync_$idUsuario", ahora).apply()
                Log.d("MainActivity", "Sincronización: ${if (ok) "OK" else "falló"}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al sincronizar: ${e.message}")
            }
        }
    }
}