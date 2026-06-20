package com.grupo4.finansync.ui.dashboards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

data class GastoPorCategoria(val nombreCategoria: String, val monto: Float)

class DashboardViewModel(
    private val repoPlanes: RepositorioPlanAhorro,
    private val repoTransacciones: RepositorioTransaccion,
    private val repoCategorias: RepositorioCategoria
) : ViewModel() {

    val idUsuarioReal: String = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario-prueba-001"

    val listaPlanesActivos = repoPlanes.obtenerPlanesAhorroActivosPorUsuario(idUsuarioReal).asLiveData()

    private fun obtenerInicioDelMesActual(): Long {
        val calendario = Calendar.getInstance()
        calendario.set(Calendar.DAY_OF_MONTH, 1)
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)
        return calendario.timeInMillis
    }

    private val ingresosHistoricosTotales = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            lista.filter { it.tipo.lowercase(Locale.ROOT) == "ingreso" }.sumOf { it.monto }
        }

    private val gastosHistoricosTotales = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            lista.filter { it.tipo.lowercase(Locale.ROOT) == "gasto" }.sumOf { it.monto }
        }

    val balanceDisponibleReal = ingresosHistoricosTotales.combine(gastosHistoricosTotales) { ingresos, gastos ->
        ingresos - gastos
    }.asLiveData()

    val totalIngresosLiveData = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            val inicioMes = obtenerInicioDelMesActual()
            lista.filter {
                it.tipo.lowercase(Locale.ROOT) == "ingreso" && it.creadoEn >= inicioMes
            }.sumOf { it.monto }
        }.asLiveData()

    val totalGastosLiveData = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            val inicioMes = obtenerInicioDelMesActual()
            lista.filter {
                it.tipo.lowercase(Locale.ROOT) == "gasto" && it.creadoEn >= inicioMes
            }.sumOf { it.monto }
        }.asLiveData()

    val transaccionesGastos = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            val inicioMes = obtenerInicioDelMesActual()
            lista.filter { it.tipo.lowercase(Locale.ROOT) == "gasto" && it.creadoEn >= inicioMes }
        }.asLiveData()

    val transaccionesRecientes = repoTransacciones.obtenerTransaccionesRecientes(idUsuarioReal).asLiveData()

    val gastosPorCategoriaReal = repoTransacciones.obtenerTransaccionesPorUsuario(idUsuarioReal)
        .map { lista ->
            val inicioMes = obtenerInicioDelMesActual()
            lista.filter { it.tipo.lowercase(Locale.ROOT) == "gasto" && it.creadoEn >= inicioMes }
        }
        .combine(repoCategorias.obtenerCategoriasPorUsuario(idUsuarioReal)) { transacciones, categorias ->
            if (transacciones.isEmpty()) return@combine emptyList<GastoPorCategoria>()

            val todasLasCategorias = transacciones.groupBy { it.idCategoria }
                .map { (idCat, listaDeTransacciones) ->
                    val nombreReal = categorias.find { it.idCategoria == idCat }?.nombreCategoria ?: "Categoría $idCat"
                    GastoPorCategoria(nombreReal, listaDeTransacciones.sumOf { it.monto }.toFloat())
                }
                .sortedByDescending { it.monto }

            if (todasLasCategorias.size <= 5) {
                todasLasCategorias
            } else {
                val top4 = todasLasCategorias.take(4).toMutableList()
                val sumaRestante = todasLasCategorias.drop(4).sumOf { it.monto.toDouble() }.toFloat()
                top4.add(GastoPorCategoria("Otros", sumaRestante))
                top4
            }
        }.asLiveData()
}