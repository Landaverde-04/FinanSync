package com.grupo4.finansync.ui.transaccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.modelo.TransaccionEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransaccionViewModel(private val repositorio: RepositorioTransaccion) : ViewModel() {

    // 1. ESTADO DE LA UI: Aquí guardamos la lista de transacciones que verá el usuario
    // El guion bajo (_) es privado para que la vista no pueda modificar la lista directamente.
    private val _transacciones = MutableStateFlow<List<TransaccionEntidad>>(emptyList())
    val transacciones: StateFlow<List<TransaccionEntidad>> = _transacciones.asStateFlow()

    // 2. LECTURA CONTINUA: Empezamos a escuchar la base de datos
    fun cargarDatosUsuario(idUsuario: String) {
        viewModelScope.launch {
            // "collect" se queda escuchando. Si Room detecta un cambio, actualizará _transacciones automáticamente
            repositorio.obtenerTransaccionesPorUsuario(idUsuario).collect { listaActualizada ->
                _transacciones.value = listaActualizada
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
}