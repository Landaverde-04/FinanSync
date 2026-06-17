package com.grupo4.finansync.ui.historial

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.TransaccionEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BaseDatos.obtenerInstancia(application)
    private val transaccionDao = db.transaccionDao()
    private val categoriaDao = db.categoriaDao()

    // Emite "" al inicio; se actualiza cuando Supabase restaura la sesión
    private val _idUsuario = MutableStateFlow("")

    init {
        // Supabase puede tardar unos instantes en restaurar la sesión en memoria.
        // Reintentamos hasta 10 veces con 500ms de espera entre cada intento.
        viewModelScope.launch(Dispatchers.IO) {
            repeat(10) { intento ->
                val id = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                if (!id.isNullOrEmpty()) {
                    _idUsuario.value = id
                    return@launch
                }
                delay(500L)
            }
        }
    }

    // ── Filtros ───────────────────────────────────────────────────────────────
    private val _filtroTipo = MutableStateFlow("todos")
    val filtroTipo: StateFlow<String> = _filtroTipo.asStateFlow()

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda: StateFlow<String> = _textoBusqueda.asStateFlow()

    // ── Categorías: se activan cuando el ID ya está disponible ───────────────
    val nombresCategorias: StateFlow<Map<Int, String>> =
        _idUsuario
            .filter { it.isNotEmpty() }
            .flatMapLatest { id ->
                categoriaDao.obtenerCategoriasPorUsuario(id)
                    .map { lista -> lista.associate { it.idCategoria to it.nombreCategoria } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── Transacciones: reactivas al ID ────────────────────────────────────────
    private val todasLasTransacciones: Flow<List<TransaccionEntidad>> =
        _idUsuario
            .filter { it.isNotEmpty() }
            .flatMapLatest { id ->
                transaccionDao.obtenerTransaccionesPorUsuario(id)
            }

    // ── Lista filtrada ────────────────────────────────────────────────────────
    val transaccionesFiltradas: StateFlow<List<TransaccionEntidad>> =
        combine(todasLasTransacciones, _filtroTipo, _textoBusqueda) { lista, tipo, texto ->
            lista
                .filter { t ->
                    when (tipo) {
                        "ingreso" -> t.tipo == "ingreso"
                        "gasto"   -> t.tipo == "gasto"
                        else      -> true
                    }
                }
                .filter { t ->
                    texto.isBlank() || t.descripcion.contains(texto, ignoreCase = true)
                }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Resumen del mes actual ────────────────────────────────────────────────
    data class ResumenUiState(
        val ingresos: Double = 0.0,
        val gastos: Double = 0.0,
        val balance: Double = 0.0,
        val etiquetaPeriodo: String = ""
    )

    val resumen: StateFlow<ResumenUiState> =
        transaccionesFiltradas
            .map { lista ->
                val ahora    = Calendar.getInstance()
                val mesActual  = ahora.get(Calendar.MONTH)
                val anioActual = ahora.get(Calendar.YEAR)

                val delMes = lista.filter { t ->
                    val cal = Calendar.getInstance().apply { timeInMillis = t.creadoEn }
                    cal.get(Calendar.MONTH) == mesActual && cal.get(Calendar.YEAR) == anioActual
                }

                val ingresos = delMes.filter { it.tipo == "ingreso" }.sumOf { it.monto }
                val gastos   = delMes.filter { it.tipo == "gasto"   }.sumOf { it.monto }

                val meses = arrayOf(
                    "enero","febrero","marzo","abril","mayo","junio",
                    "julio","agosto","septiembre","octubre","noviembre","diciembre"
                )
                val etiqueta = "Total ${meses[mesActual].replaceFirstChar { it.uppercase() }} $anioActual"
                ResumenUiState(ingresos, gastos, ingresos - gastos, etiqueta)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenUiState())

    fun cambiarFiltroTipo(tipo: String) { _filtroTipo.value = tipo }
    fun cambiarBusqueda(texto: String)  { _textoBusqueda.value = texto }
}