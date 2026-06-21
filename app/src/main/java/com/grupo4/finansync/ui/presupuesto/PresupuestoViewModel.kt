package com.grupo4.finansync.ui.presupuesto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPresupuesto
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.modelo.PresupuestoEntidad
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.obtenerEmoji
import com.grupo4.finansync.modelo.obtenerNombreLimpio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlinx.coroutines.flow.firstOrNull
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.postgrest.postgrest

data class PresupuestoUI(
    val entidad: PresupuestoEntidad,
    val categoriaNombre: String,
    val categoriaEmoji: String,
    val montoGastado: Double,
    val porcentajeConsumo: Int
)

class PresupuestoViewModel(
    private val repositorioPresupuesto: RepositorioPresupuesto,
    private val repositorioCategoria: RepositorioCategoria,
    private val repositorioTransaccion: RepositorioTransaccion
) : ViewModel() {

    private val _presupuestosUI = MutableStateFlow<List<PresupuestoUI>>(emptyList())
    val presupuestosUI: StateFlow<List<PresupuestoUI>> = _presupuestosUI.asStateFlow()

    private val _categoriasDisponibles = MutableStateFlow<List<CategoriaEntidad>>(emptyList())
    val categoriasDisponibles: StateFlow<List<CategoriaEntidad>> = _categoriasDisponibles.asStateFlow()

    fun cargarPresupuestos(idUsuario: String) {
        viewModelScope.launch {
            combine(
                repositorioPresupuesto.obtenerPresupuestosPorUsuario(idUsuario),
                repositorioCategoria.obtenerCategoriasPorUsuario(idUsuario),
                repositorioTransaccion.obtenerTransaccionesPorUsuario(idUsuario)
            ) { presupuestos, categorias, transacciones ->
                val mapaCategorias = categorias.associateBy { it.idCategoria }

                presupuestos.map { p ->
                    val cat = mapaCategorias[p.idCategoria]
                    val nombreLimpio = cat?.obtenerNombreLimpio() ?: "Categoría"
                    val emoji = cat?.obtenerEmoji() ?: "🏷️"

                    val inicio = obtenerInicioDelPeriodo(p.periodo)
                    val gastado = transacciones.filter {
                        it.idCategoria == p.idCategoria &&
                        it.tipo == "gasto" &&
                        it.creadoEn >= inicio
                    }.sumOf { it.monto }

                    val porcentaje = if (p.montoLimite > 0) {
                        ((gastado / p.montoLimite) * 100).toInt()
                    } else 0

                    PresupuestoUI(p, nombreLimpio, emoji, gastado, porcentaje)
                }
            }.collect { listaUI ->
                _presupuestosUI.value = listaUI
            }
        }
    }

    fun cargarCategoriasGastos(idUsuario: String) {
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuarioYTipo(idUsuario, "gasto").collect { lista ->
                _categoriasDisponibles.value = lista
            }
        }
    }

    fun insertarPresupuesto(presupuesto: PresupuestoEntidad) {
        viewModelScope.launch {
            repositorioPresupuesto.insertarPresupuesto(presupuesto)
        }
    }

    fun eliminarPresupuesto(presupuesto: PresupuestoEntidad) {
        viewModelScope.launch {
            repositorioPresupuesto.eliminarPresupuesto(presupuesto)
        }
    }

    private fun obtenerInicioDelPeriodo(periodo: String): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        when (periodo.lowercase()) {
            "semanal" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            }
            "mensual" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
            }
            "anual" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }
 
    fun sincronizarPendientes(idUsuario: String, context: android.content.Context) {
        viewModelScope.launch {
            try {
                // 1. Procesar eliminaciones pendientes
                val prefs = context.getSharedPreferences("eliminaciones_pendientes_m3", android.content.Context.MODE_PRIVATE)
                val todas = prefs.all
                todas.keys.filter { it.startsWith("pres_${idUsuario}_") }.forEach { clave ->
                    val idPresupuesto = prefs.getInt(clave, -1)
                    if (idPresupuesto != -1) {
                        try {
                            SupabaseCliente.cliente.postgrest["presupuesto"].delete {
                                filter { eq("idPresupuesto", idPresupuesto) }
                            }
                            prefs.edit().remove(clave).apply()
                            android.util.Log.d("PresupuestoViewModel", "Eliminación pendiente de presupuesto sincronizada: $idPresupuesto")
                        } catch (e: Exception) {
                            android.util.Log.e("PresupuestoViewModel", "Error al eliminar presupuesto pendiente: ${e.message}")
                        }
                    }
                }

                // 2. Procesar inserciones pendientes
                val locales = repositorioPresupuesto.obtenerPresupuestosPorUsuario(idUsuario).firstOrNull() ?: emptyList()
                if (locales.isNotEmpty()) {
                    val remotas = SupabaseCliente.cliente.postgrest["presupuesto"]
                        .select { filter { eq("idUsuario", idUsuario) } }
                        .decodeList<PresupuestoEntidad>()
                    val idsEnNube = remotas.map { it.idPresupuesto }.toSet()

                    val pendientes = locales.filter { it.idPresupuesto !in idsEnNube }
                    pendientes.forEach { pres ->
                        try {
                            SupabaseCliente.cliente.postgrest["presupuesto"].upsert(pres)
                            android.util.Log.d("PresupuestoViewModel", "Inserción pendiente de presupuesto sincronizada: ${pres.idPresupuesto}")
                        } catch (e: Exception) {
                            android.util.Log.e("PresupuestoViewModel", "Error al subir presupuesto pendiente: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PresupuestoViewModel", "Error en sincronizarPendientes: ${e.message}")
            }
        }
    }

    class Factory(
        private val repositorioPresupuesto: RepositorioPresupuesto,
        private val repositorioCategoria: RepositorioCategoria,
        private val repositorioTransaccion: RepositorioTransaccion
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: java.lang.Class<T>): T {
            return PresupuestoViewModel(repositorioPresupuesto, repositorioCategoria, repositorioTransaccion) as T
        }
    }
}
