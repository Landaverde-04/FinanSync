package com.grupo4.finansync.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioUsuario

/**
 * Factory necesaria porque AuthViewModel recibe parámetros en su constructor.
 * Android no puede instanciar ViewModels con parámetros sin una Factory.
 */
class AuthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            val baseDatos = BaseDatos.obtenerInstancia(context.applicationContext)
            val repositorioUsuario = RepositorioUsuario(baseDatos.usuarioDao())
            val prefs = context.getSharedPreferences(
                "finansync_prefs",
                Context.MODE_PRIVATE
            )
            return AuthViewModel(repositorioUsuario, prefs) as T
        }
        throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    }
}