package com.grupo4.finansync.data.repositorio

import android.content.Context
import android.util.Log
import com.grupo4.finansync.bd.dao.PresupuestoDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.PresupuestoEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de PRESUPUESTO.
 * El módulo M4 usa este repositorio para definir límites de gasto
 * por categoría y período, y compararlos contra las transacciones reales.
 */
class RepositorioPresupuesto(
    private val presupuestoDao: PresupuestoDao,
    private val context: Context? = null
) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    fun obtenerPresupuestosPorUsuario(idUsuario: String): Flow<List<PresupuestoEntidad>> =
        presupuestoDao.obtenerPresupuestosPorUsuario(idUsuario)

    fun obtenerPresupuestosPorUsuarioYPeriodo(idUsuario: String, periodo: String): Flow<List<PresupuestoEntidad>> =
        presupuestoDao.obtenerPresupuestosPorUsuarioYPeriodo(idUsuario, periodo)

    // Consulta puntual para validar si una transacción supera el límite de su categoría
    suspend fun obtenerPresupuestoPorUsuarioYCategoria(idUsuario: String, idCategoria: Int): PresupuestoEntidad? =
        presupuestoDao.obtenerPresupuestoPorUsuarioYCategoria(idUsuario, idCategoria)

    suspend fun obtenerPresupuestoPorId(idPresupuesto: Int): PresupuestoEntidad? =
        presupuestoDao.obtenerPresupuestoPorId(idPresupuesto)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarPresupuesto(presupuesto: PresupuestoEntidad): Long {
        val idGenerado = presupuestoDao.insertarPresupuesto(presupuesto)
        try {
            SupabaseCliente.cliente.postgrest["presupuesto"].upsert(presupuesto.copy(idPresupuesto = idGenerado.toInt()))
        } catch (e: Exception) {
            Log.e("RepositorioPresupuesto", "Error al sincronizar inserción: ${e.message}")
        }
        return idGenerado
    }

    suspend fun actualizarPresupuesto(presupuesto: PresupuestoEntidad) {
        presupuestoDao.actualizarPresupuesto(presupuesto)
        try {
            SupabaseCliente.cliente.postgrest["presupuesto"].upsert(presupuesto)
        } catch (e: Exception) {
            Log.e("RepositorioPresupuesto", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarPresupuesto(presupuesto: PresupuestoEntidad) {
        presupuestoDao.eliminarPresupuesto(presupuesto)
        try {
            SupabaseCliente.cliente.postgrest["presupuesto"].delete {
                filter { eq("idPresupuesto", presupuesto.idPresupuesto) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioPresupuesto", "Error al sincronizar eliminación: ${e.message}")
            context?.let { ctx ->
                try {
                    val prefs = ctx.getSharedPreferences("eliminaciones_pendientes_m3", Context.MODE_PRIVATE)
                    val clave = "pres_${presupuesto.idUsuario}_${presupuesto.idPresupuesto}"
                    prefs.edit().putInt(clave, presupuesto.idPresupuesto).apply()
                    Log.d("RepositorioPresupuesto", "Guardada eliminación pendiente de presupuesto local: ${presupuesto.idPresupuesto}")
                } catch (ex: Exception) {
                    Log.e("RepositorioPresupuesto", "Error al guardar eliminación pendiente: ${ex.message}")
                }
            }
        }
    }
}
