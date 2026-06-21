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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import android.util.Log

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BaseDatos.obtenerInstancia(application)
    private val transaccionDao = db.transaccionDao()
    private val categoriaDao = db.categoriaDao()

    // Emite "" al inicio; se actualiza cuando Supabase restaura la sesión
    private val _idUsuario = MutableStateFlow("")

    // Estado de carga de sesión (true mientras no se ha resuelto el ID de usuario)
    val cargandoSesion: StateFlow<Boolean> =
        _idUsuario.map { it.isEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _mensajeSincronizacion = MutableStateFlow<String?>(null)
    val mensajeSincronizacion: StateFlow<String?> = _mensajeSincronizacion.asStateFlow()

    fun limpiarMensajeSincronizacion() { _mensajeSincronizacion.value = null }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repeat(20) { intento ->
                val id = SupabaseCliente.cliente.auth.currentUserOrNull()?.id
                android.util.Log.d("HistorialDebug", "Intento $intento, id=$id")
                if (!id.isNullOrEmpty()) {
                    _idUsuario.value = id
                    android.util.Log.d("HistorialDebug", "ID asignado: $id")
                    // Verificar inmediatamente cuántas transacciones hay en Room
                    val cuenta = transaccionDao.obtenerTransaccionesPorUsuario(id).first().size
                    android.util.Log.d("HistorialDebug", "Room tiene $cuenta transacciones para este usuario")
                    return@launch
                }
                delay(100L)
            }
        }
    }

    // Sincronizado al regresar conexion WIFI:
    fun reintentarSincronizacionPendiente() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val idUsuario = _idUsuario.value
                if (idUsuario.isEmpty()) return@launch

                val enNube = SupabaseCliente.cliente.postgrest["transacciones"]
                    .select { filter { eq("idUsuario", idUsuario) } }
                    .decodeList<TransaccionEntidad>()
                val idsEnNube = enNube.map { it.idTransaccion }.toSet()

                val enRoom = transaccionDao.obtenerTransaccionesPorUsuario(idUsuario).first()
                val pendientes = enRoom.filter { it.idTransaccion !in idsEnNube }

                var subidasExitosas = 0
                pendientes.forEach { t ->
                    try {
                        SupabaseCliente.cliente.postgrest["transacciones"].upsert(t)
                        subidasExitosas++
                        Log.d("Sync", "Subida pendiente: ${t.idTransaccion}")
                    } catch (e: Exception) {
                        Log.e("Sync", "Sigue sin poder subir ${t.idTransaccion}: ${e.message}")
                    }
                }

                if (subidasExitosas > 0) {
                    _mensajeSincronizacion.value =
                        "✅ $subidasExitosas transacción(es) pendientes sincronizada(s) exitosamente"
                }
            } catch (e: Exception) {
                Log.e("Sync", "Error al reintentar: ${e.message}")
            }
        }
    }

    fun reintentarEliminacionesPendientes() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<android.app.Application>()
                .getSharedPreferences("eliminaciones_pendientes", android.content.Context.MODE_PRIVATE)
            val pendientes = prefs.all

            var eliminacionesExitosas = 0

            pendientes.forEach { (clave, valor) ->
                try {
                    val partes = (valor as String).split("|")
                    val idUsuario = partes[0]
                    val creadoEn = partes[1].toLong()

                    SupabaseCliente.cliente.postgrest["transacciones"]
                        .delete {
                            filter {
                                eq("idUsuario", idUsuario)
                                eq("creadoEn", creadoEn)
                            }
                        }

                    prefs.edit().remove(clave).apply()
                    eliminacionesExitosas++
                    Log.d("Sync", "Eliminación pendiente confirmada: $clave")
                } catch (e: Exception) {
                    Log.e("Sync", "Sigue sin poder eliminar $clave: ${e.message}")
                }
            }

            if (eliminacionesExitosas > 0) {
                _mensajeSincronizacion.value =
                    "✅ $eliminacionesExitosas eliminación(es) pendiente(s) sincronizadas exitosamente"
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