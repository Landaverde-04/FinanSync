package com.grupo4.finansync.ui.reportes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.modelo.TransaccionEntidad
import kotlinx.coroutines.flow.*
import java.util.Calendar
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)

/**
 * ViewModel de la pantalla de Reportes.
 *
 * Expone:
 *  - [resumen]               → ingresos, gastos y balance del periodo seleccionado
 *  - [transaccionesPeriodo]  → lista completa para armar la tabla del PDF
 *  - [nombresCategorias]     → mapa id → nombre para el PDF
 *  - [generarPeriodos]       → lista de etiquetas "Mes Año" para el Spinner
 */
class ReportesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BaseDatos.obtenerInstancia(application)
    private val transaccionDao = db.transaccionDao()
    private val categoriaDao   = db.categoriaDao()

    // ID de usuario: reemplazar por el ID real de Supabase Auth
    //private val idUsuario = "usuario-demo"
    private val _idUsuario = MutableStateFlow("")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repeat(10) {
                val id = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                if (!id.isNullOrEmpty()) {
                    _idUsuario.value = id
                    return@launch
                }
                delay(500L)
            }
        }
    }

    // ── Periodo seleccionado (índice en la lista de generarPeriodos) ──────
    private val _periodoIndex = MutableStateFlow(0)

    // Cache de periodos calculados (mes y año absolutos)
    private val periodos: List<Pair<Int, Int>> = run {
        val cal = Calendar.getInstance()
        (0 until 12).map { i ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            Pair(c.get(Calendar.MONTH), c.get(Calendar.YEAR))
        }
    }

    // Categorías:
    val nombresCategorias: StateFlow<Map<Int, String>> =
        _idUsuario
            .filter { it.isNotEmpty() }
            .flatMapLatest { id ->
                categoriaDao.obtenerCategoriasPorUsuario(id)
                    .map { lista -> lista.associate { it.idCategoria to it.nombreCategoria } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Transacciones del periodo:
    val transaccionesPeriodo: StateFlow<List<TransaccionEntidad>> =
        combine(
            _idUsuario.filter { it.isNotEmpty() }
                .flatMapLatest { id -> transaccionDao.obtenerTransaccionesPorUsuario(id) },
            _periodoIndex
        ) { lista, idx ->
            val (mes, anio) = periodos[idx]
            lista.filter { t ->
                val cal = Calendar.getInstance().apply { timeInMillis = t.creadoEn }
                cal.get(Calendar.MONTH) == mes && cal.get(Calendar.YEAR) == anio
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Resumen calculado ─────────────────────────────────────────────────
    data class ResumenReporte(
        val ingresos: Double = 0.0,
        val gastos: Double = 0.0,
        val balance: Double = 0.0,
        val etiquetaPeriodo: String = ""
    )

    val resumen: StateFlow<ResumenReporte> =
        combine(transaccionesPeriodo, _periodoIndex) { lista, idx ->
            val ingresos = lista.filter { it.tipo == "ingreso" }.sumOf { it.monto }
            val gastos   = lista.filter { it.tipo == "gasto"   }.sumOf { it.monto }
            ResumenReporte(ingresos, gastos, ingresos - gastos, generarPeriodos()[idx])
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenReporte())

    // ── API pública ───────────────────────────────────────────────────────
    fun cambiarPeriodo(index: Int) { _periodoIndex.value = index }

    /** Devuelve etiquetas como "Junio 2026", "Mayo 2026", … (últimos 12 meses). */
    fun generarPeriodos(): List<String> {
        val meses = arrayOf(
            "Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
        )
        return periodos.map { (mes, anio) -> "${meses[mes]} $anio" }
    }
}