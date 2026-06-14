package com.grupo4.finansync.ui.transaccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.UsuarioEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransaccionViewModel(
    private val repositorio: RepositorioTransaccion,
    // Repositorios extra solo para sembrar datos de prueba (mock). Se quitan cuando M2/M3 estén listos.
    private val repositorioUsuario: RepositorioUsuario,
    private val repositorioCategoria: RepositorioCategoria
) : ViewModel() {

    // Lista de categorías del usuario, que el spinner va a observar.
    private val _categorias = MutableStateFlow<List<CategoriaEntidad>>(emptyList())
    val categorias: StateFlow<List<CategoriaEntidad>> = _categorias.asStateFlow()

    /**
     * TEMPORAL (mock): crea un usuario y varias categorías de prueba si no existen,
     * para que las transacciones tengan a quién "pertenecer" y el spinner muestre opciones.
     * Cuando M2 (login) y M3 (categorías) estén listos, este método se elimina.
     */
    fun sembrarDatosDePrueba(idUsuario: String) {
        viewModelScope.launch {
            // 1. Crear el usuario de prueba (REPLACE: si ya existe, no pasa nada)
            repositorioUsuario.insertarUsuario(
                UsuarioEntidad(
                    idUsuario = idUsuario,
                    email = "prueba@finansync.com",
                    nombreUsuario = "Usuario Prueba",
                    creadoEn = System.currentTimeMillis()
                )
            )

            // 2. Crear varias categorías solo si todavía no hay ninguna para el usuario
            val yaExiste = repositorioCategoria.obtenerCategoriaPorId(1)
            if (yaExiste == null) {
                val categoriasMock = listOf(
                    "Comida" to "gasto",
                    "Transporte" to "gasto",
                    "Entretenimiento" to "gasto",
                    "Servicios" to "gasto",
                    "Salario" to "ingreso",
                    "Otros" to "gasto"
                )
                for ((nombre, tipo) in categoriasMock) {
                    repositorioCategoria.insertarCategoria(
                        CategoriaEntidad(
                            idCategoria = 0,          // Room asigna el id automáticamente
                            idUsuario = idUsuario,
                            nombreCategoria = nombre,
                            tipo = tipo
                        )
                    )
                }
            }
        }
    }

    /**
     * Carga en el spinner SOLO las categorías del tipo indicado ("ingreso" o "gasto").
     * Usa la consulta del DAO que ya filtra por tipo, así un gasto no muestra
     * categorías de ingreso y viceversa.
     *
     * Se llama cada vez que el usuario cambia de pestaña.
     */
    fun cargarCategoriasPorTipo(idUsuario: String, tipo: String) {
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuarioYTipo(idUsuario, tipo).collect { lista ->
                _categorias.value = lista
            }
        }
    }

    // 1. ESTADO DE LA UI: Aquí guardamos la lista de transacciones que verá el usuario
    // El guion bajo (_) es privado para que la vista no pueda modificar la lista directamente.
    private val _transacciones = MutableStateFlow<List<TransaccionEntidad>>(emptyList())
    val transacciones: StateFlow<List<TransaccionEntidad>> = _transacciones.asStateFlow()

    // Mapa idCategoria -> nombre, para que la lista muestre el nombre y no el número.
    private val _mapaCategorias = MutableStateFlow<Map<Int, String>>(emptyMap())
    val mapaCategorias: StateFlow<Map<Int, String>> = _mapaCategorias.asStateFlow()

    // 2. LECTURA CONTINUA: Empezamos a escuchar la base de datos
    fun cargarDatosUsuario(idUsuario: String) {
        viewModelScope.launch {
            // "collect" se queda escuchando. Si Room detecta un cambio, actualizará _transacciones automáticamente
            repositorio.obtenerTransaccionesPorUsuario(idUsuario).collect { listaActualizada ->
                _transacciones.value = listaActualizada
            }
        }
        // En paralelo, mantenemos actualizado el mapa de nombres de categorías
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuario(idUsuario).collect { categorias ->
                // Convertimos la lista en un mapa { idCategoria -> nombre }
                _mapaCategorias.value = categorias.associate { it.idCategoria to it.nombreCategoria }
            }
        }
    }

    // 3. ESCRITURA: Mandamos a guardar y el ViewModel maneja el hilo secundario (Coroutines)
    fun agregarTransaccion(transaccion: TransaccionEntidad) {
        viewModelScope.launch {
            repositorio.insertarTransaccion(transaccion)
        }
    }

    fun eliminarTransaccion(transaccion: TransaccionEntidad) {
        viewModelScope.launch {
            repositorio.eliminarTransaccion(transaccion)
        }
    }

    /**
     * FÁBRICA del ViewModel.
     * Android no sabe crear un ViewModel que pide un repositorio en su constructor.
     * Esta clase le enseña: "cuando te pidan un TransaccionViewModel, usá este repositorio".
     */
    class Factory(
        private val repositorio: RepositorioTransaccion,
        private val repositorioUsuario: RepositorioUsuario,
        private val repositorioCategoria: RepositorioCategoria
    ) : ViewModelProvider.Factory {

        // Android llama a este método cuando necesita crear el ViewModel
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Construimos el ViewModel pasándole los repositorios y lo devolvemos
            @Suppress("UNCHECKED_CAST")
            return TransaccionViewModel(
                repositorio, repositorioUsuario, repositorioCategoria
            ) as T
        }
    }
}