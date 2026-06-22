package com.grupo4.finansync.ui.auth

import android.content.Context
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.gotrue.auth

object SesionLocal {

    fun obtenerIdUsuario(
        context: Context
    ): String? {
        val idSupabase =
            SupabaseCliente.cliente.auth.currentUserOrNull()?.id

        if (!idSupabase.isNullOrBlank()) {
            return idSupabase
        }

        val prefs = context.getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        return prefs.getString(
            AuthPrefs.PREF_ULTIMO_USUARIO_ID,
            null
        )
    }

    fun estaEnModoOffline(
        context: Context
    ): Boolean {
        val prefs = context.getSharedPreferences(
            AuthPrefs.PREFS,
            Context.MODE_PRIVATE
        )

        return prefs.getBoolean(
            AuthPrefs.PREF_MODO_OFFLINE,
            false
        )
    }
}