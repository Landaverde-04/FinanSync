package com.grupo4.finansync.ui.categoria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.modelo.CategoriaEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.postgrest.postgrest

class CategoriaViewModel(
    private val repositorioCategoria: RepositorioCategoria,
    private val repositorioTransaccion: RepositorioTransaccion
) : ViewModel() {

    private val _categorias = MutableStateFlow<List<CategoriaEntidad>>(emptyList())
    val categorias: StateFlow<List<CategoriaEntidad>> = _categorias.asStateFlow()

    fun cargarCategorias(idUsuario: String) {
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuario(idUsuario).collect { lista ->
                _categorias.value = lista
                if (lista.isEmpty()) {
                    sembrarCategoriasPorDefecto(idUsuario)
                }
            }
        }
    }

    fun insertarCategoria(categoria: CategoriaEntidad) {
        viewModelScope.launch {
            repositorioCategoria.insertarCategoria(categoria)
        }
    }

    fun eliminarCategoria(categoria: CategoriaEntidad, onResultado: (exito: Boolean, mensaje: String) -> Unit) {
        viewModelScope.launch {
            val transacciones = repositorioTransaccion.obtenerTransaccionesPorUsuarioYCategoria(
                categoria.idUsuario,
                categoria.idCategoria
            ).firstOrNull() ?: emptyList()

            if (transacciones.isNotEmpty()) {
                onResultado(false, "No puedes eliminar esta categoría porque tiene transacciones asociadas.")
            } else {
                repositorioCategoria.eliminarCategoria(categoria)
                onResultado(true, "Categoría eliminada con éxito.")
            }
        }
    }

    private suspend fun sembrarCategoriasPorDefecto(idUsuario: String) {
        val categoriasMock = listOf(
            "🍔 Comida" to "gasto",
            "🚌 Transporte" to "gasto",
            "🎬 Entretenimiento" to "gasto",
            "🏥 Salud" to "gasto",
            "💰 Salario" to "ingreso",
            "🏷️ Otros" to "gasto"
        )
        for ((nombre, tipo) in categoriasMock) {
            repositorioCategoria.insertarCategoria(
                CategoriaEntidad(
                    idCategoria = 0,
                    idUsuario = idUsuario,
                    nombreCategoria = nombre,
                    tipo = tipo
                )
            )
        }
    }
 
    fun sincronizarPendientes(idUsuario: String, context: android.content.Context) {
        viewModelScope.launch {
            try {
                // 1. Procesar eliminaciones pendientes
                val prefs = context.getSharedPreferences("eliminaciones_pendientes_m3", android.content.Context.MODE_PRIVATE)
                val todas = prefs.all
                todas.keys.filter { it.startsWith("cat_${idUsuario}_") }.forEach { clave ->
                    val idCategoria = prefs.getInt(clave, -1)
                    if (idCategoria != -1) {
                        try {
                            SupabaseCliente.cliente.postgrest["categorias"].delete {
                                filter { eq("idCategoria", idCategoria) }
                            }
                            prefs.edit().remove(clave).apply()
                            android.util.Log.d("CategoriaViewModel", "Eliminación pendiente de categoría sincronizada: $idCategoria")
                        } catch (e: Exception) {
                            android.util.Log.e("CategoriaViewModel", "Error al eliminar categoría pendiente: ${e.message}")
                        }
                    }
                }

                // 2. Procesar inserciones pendientes
                val locales = repositorioCategoria.obtenerCategoriasPorUsuario(idUsuario).firstOrNull() ?: emptyList()
                if (locales.isNotEmpty()) {
                    val remotas = SupabaseCliente.cliente.postgrest["categorias"]
                        .select { filter { eq("idUsuario", idUsuario) } }
                        .decodeList<CategoriaEntidad>()
                    val idsEnNube = remotas.map { it.idCategoria }.toSet()

                    val pendientes = locales.filter { it.idCategoria !in idsEnNube }
                    pendientes.forEach { cat ->
                        try {
                            SupabaseCliente.cliente.postgrest["categorias"].upsert(cat)
                            android.util.Log.d("CategoriaViewModel", "Inserción pendiente de categoría sincronizada: ${cat.nombreCategoria}")
                        } catch (e: Exception) {
                            android.util.Log.e("CategoriaViewModel", "Error al subir categoría pendiente: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CategoriaViewModel", "Error en sincronizarPendientes: ${e.message}")
            }
        }
    }

    class Factory(
        private val repositorioCategoria: RepositorioCategoria,
        private val repositorioTransaccion: RepositorioTransaccion
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: java.lang.Class<T>): T {
            return CategoriaViewModel(repositorioCategoria, repositorioTransaccion) as T
        }
    }
}
