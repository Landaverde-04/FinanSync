package com.grupo4.finansync.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.grupo4.finansync.MainActivity
import com.grupo4.finansync.R
import com.grupo4.finansync.ui.ajustes.AjustesFragment
import androidx.lifecycle.lifecycleScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.sync.SyncManager
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class AuthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar tema oscuro igual que MainActivity
        val prefs = getSharedPreferences(AjustesFragment.PREFS, Context.MODE_PRIVATE)
        val modoOscuro = prefs.getBoolean(AjustesFragment.KEY_MODO_OSCURO, false)
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
    }

    /**
     * Llamado desde HomeHostFragment cuando el login es exitoso.
     * Lanza MainActivity y cierra AuthActivity para que no quede en el back stack.
     */
    fun irAMain() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                if (idUsuario != null) {
                    val bd = BaseDatos.obtenerInstancia(applicationContext)
                    // Esta llamada es suspend — espera a que termine antes de continuar
                    SyncManager(bd).sincronizarTodo(idUsuario)
                    Log.d("AuthActivity", "Sincronización completada para $idUsuario")
                }
            } catch (e: Exception) {
                Log.e("AuthActivity", "Error sync: ${e.message}")
            }
            // Llegar aquí significa que la sync terminó (exitosa o no)
            // Solo ENTONCES lanzamos MainActivity
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    companion object {
        fun iniciar(context: Context) {
            context.startActivity(Intent(context, AuthActivity::class.java))
        }
    }
}