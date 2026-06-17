package com.grupo4.finansync.ui.detalle

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.grupo4.finansync.modelo.TransaccionEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de detalle.
 *
 * Carga la transacción por ID, resuelve el nombre de categoría y la URL
 * del comprobante (si existe) para exponerlos como [DetalleUiState].
 *
 * Expone también [eliminarTransaccion] y [abrirMapa].
 */
class DetalleTransaccionViewModel(
    application: Application,
    private val idTransaccion: Int
) : AndroidViewModel(application) {

    private val db = BaseDatos.obtenerInstancia(application)
    private val transaccionDao = db.transaccionDao()
    private val categoriaDao   = db.categoriaDao()
    private val comprobanteDao = db.comprobanteDao()

    // ── Estado UI ──────────────────────────────────────────────────────────
    data class DetalleUiState(
        val transaccion: TransaccionEntidad,
        val nombreCategoria: String,
        val urlComprobante: String?
    )

    private val _uiState = MutableStateFlow<DetalleUiState?>(null)
    val uiState: StateFlow<DetalleUiState?> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Init ───────────────────────────────────────────────────────────────
    init {
        cargarDetalle()
    }

    private fun cargarDetalle() {
        viewModelScope.launch {
            try {
                val transaccion = transaccionDao.obtenerTransaccionPorId(idTransaccion)
                    ?: run { _error.value = "Transacción no encontrada"; return@launch }

                val categoria = categoriaDao.obtenerCategoriaPorId(transaccion.idCategoria)
                val nombreCategoria = categoria?.nombreCategoria ?: "Sin categoría"

                val comprobante = comprobanteDao.obtenerComprobantePorTransaccion(idTransaccion)
                val urlComprobante = comprobante?.urlImagen

                _uiState.value = DetalleUiState(transaccion, nombreCategoria, urlComprobante)
            } catch (e: Exception) {
                _error.value = "Error al cargar: ${e.message}"
            }
        }
    }

    // ── Acciones ───────────────────────────────────────────────────────────
    fun eliminarTransaccion() {
        viewModelScope.launch {
            try {
                val t = _uiState.value?.transaccion ?: return@launch

                // Ejecutar ambas eliminaciones en paralelo
                val eliminarRoom = async(Dispatchers.IO) {
                    transaccionDao.eliminarTransaccion(t)
                }

                val eliminarSupabase = async(Dispatchers.IO) {
                    SupabaseCliente.cliente.postgrest["transacciones"]
                        .delete {
                            filter {
                                eq("idUsuario", t.idUsuario)
                                eq("creadoEn", t.creadoEn)
                            }
                        }
                }

                // Esperar que ambas terminen
                eliminarRoom.await()
                eliminarSupabase.await()

            } catch (e: Exception) {
                _error.value = "Error al eliminar: ${e.message}"
            }
        }
    }

    fun abrirMapa(context: Context) {
        val t = _uiState.value?.transaccion ?: return
        val lat = t.latitud ?: return
        val lng = t.longitud ?: return
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${t.descripcion})")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Si no hay Maps instalado, abre en el navegador
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
            )
        }
    }

    fun limpiarError() { _error.value = null }

    // ── Factory ────────────────────────────────────────────────────────────
    class Factory(
        private val application: Application,
        private val idTransaccion: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DetalleTransaccionViewModel(application, idTransaccion) as T
    }
}