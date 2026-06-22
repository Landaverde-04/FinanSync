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

    // Devuelve el id (Long) que Room generó para la transacción.
    // Guarda como NO sincronizada y, si la subida a la nube funciona, la marca como sincronizada.
    suspend fun insertarTransaccion(transaccion: TransaccionEntidad): Long {
        // 1. Guardar local marcada como pendiente de subir
        val idGenerado = transaccionDao.insertarTransaccion(
            transaccion.copy(sincronizada = false)
        )
        // 2. Intentar subir a la nube
        try {
            SupabaseCliente.cliente.postgrest["transacciones"]
                .upsert(transaccion.copy(idTransaccion = idGenerado.toInt()))
            // 3. Si subió bien, marcar como sincronizada
            transaccionDao.marcarComoSincronizada(idGenerado.toInt())
        } catch (e: Exception) {
            // Sin red: queda como pendiente (sincronizada = false) para subir después
            Log.e("RepositorioTransaccion", "Sin conexión, queda pendiente de subir: ${e.message}")
        }
        return idGenerado
    }

    /**
     * Sube a Supabase las transacciones que quedaron pendientes (guardadas offline).
     * Se llama al recuperar la conexión / al abrir la app con red.
     * Devuelve cuántas logró subir.
     */
    suspend fun subirPendientes(idUsuario: String): Int {
        val pendientes = transaccionDao.obtenerNoSincronizadas(idUsuario)
        var subidas = 0
        for (t in pendientes) {
            try {
                SupabaseCliente.cliente.postgrest["transacciones"].upsert(t)
                transaccionDao.marcarComoSincronizada(t.idTransaccion)
                subidas++
            } catch (e: Exception) {
                Log.e("RepositorioTransaccion", "No se pudo subir pendiente ${t.idTransaccion}: ${e.message}")
            }
        }
        return subidas
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

    fun obtenerTransaccionesRecientes(idUsuario: String): Flow<List<TransaccionEntidad>> =
        transaccionDao.obtenerTransaccionesRecientes(idUsuario)
}
