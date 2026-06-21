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
