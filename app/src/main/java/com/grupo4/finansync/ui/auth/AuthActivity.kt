package com.grupo4.finansync.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.grupo4.finansync.MainActivity
import com.grupo4.finansync.R
import com.grupo4.finansync.ui.ajustes.AjustesFragment

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
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        fun iniciar(context: Context) {
            context.startActivity(Intent(context, AuthActivity::class.java))
        }
    }
}