package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.TransaccionDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.TransaccionEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de TRANSACCIONES.
 * Es el repositorio más usado del proyecto: alimenta el historial,
 * los gráficos y el cálculo de balance de la pantalla principal.
 */
class RepositorioTransaccion(private val transaccionDao: TransaccionDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    fun obtenerTransaccionesPorUsuario(idUsuario: String): Flow<List<TransaccionEntidad>> =
        transaccionDao.obtenerTransaccionesPorUsuario(idUsuario)

    fun obtenerTransaccionesPorUsuarioYTipo(idUsuario: String, tipo: String): Flow<List<TransaccionEntidad>> =
        transaccionDao.obtenerTransaccionesPorUsuarioYTipo(idUsuario, tipo)

    fun obtenerTransaccionesPorUsuarioYCategoria(idUsuario: String, idCategoria: Int): Flow<List<TransaccionEntidad>> =
        transaccionDao.obtenerTransaccionesPorUsuarioYCategoria(idUsuario, idCategoria)

    suspend fun obtenerTransaccionPorId(idTransaccion: Int): TransaccionEntidad? =
        transaccionDao.obtenerTransaccionPorId(idTransaccion)

    // Suma ingresos o gastos para calcular el balance del usuario
    suspend fun sumarMontoTransaccionesPorTipo(idUsuario: String, tipo: String): Double =
        transaccionDao.sumarMontoTransaccionesPorTipo(idUsuario, tipo)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarTransaccion(transaccion: TransaccionEntidad) {
        transaccionDao.insertarTransaccion(transaccion)
        try {
            SupabaseCliente.cliente.postgrest["transacciones"].upsert(transaccion)
        } catch (e: Exception) {
            Log.e("RepositorioTransaccion", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarTransaccion(transaccion: TransaccionEntidad) {
        transaccionDao.actualizarTransaccion(transaccion)
        try {
            SupabaseCliente.cliente.postgrest["transacciones"].upsert(transaccion)
        } catch (e: Exception) {
            Log.e("RepositorioTransaccion", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarTransaccion(transaccion: TransaccionEntidad) {
        transaccionDao.eliminarTransaccion(transaccion)
        try {
            SupabaseCliente.cliente.postgrest["transacciones"].delete {
                filter { eq("idTransaccion", transaccion.idTransaccion) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioTransaccion", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
