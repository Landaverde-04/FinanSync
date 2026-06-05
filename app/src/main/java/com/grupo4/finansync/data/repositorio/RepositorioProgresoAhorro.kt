package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.ProgresoAhorroDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.ProgresoAhorroEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de PROGRESO_AHORRO.
 * Permite registrar cortes periódicos del monto ahorrado dentro de
 * un plan, y consultar el historial o el total acumulado.
 */
class RepositorioProgresoAhorro(private val progresoAhorroDao: ProgresoAhorroDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    // Historial completo del plan para construir la gráfica de progreso
    fun obtenerProgresoPorPlan(idAhorro: Int): Flow<List<ProgresoAhorroEntidad>> =
        progresoAhorroDao.obtenerProgresoPorPlan(idAhorro)

    // Total acumulado para comparar contra la meta del plan
    suspend fun sumarMontoAhorradoPorPlan(idAhorro: Int): Double =
        progresoAhorroDao.sumarMontoAhorradoPorPlan(idAhorro)

    // Último registro para mostrar el estado más reciente en la UI
    suspend fun obtenerUltimoProgresoPorPlan(idAhorro: Int): ProgresoAhorroEntidad? =
        progresoAhorroDao.obtenerUltimoProgresoPorPlan(idAhorro)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarProgresoAhorro(progreso: ProgresoAhorroEntidad) {
        progresoAhorroDao.insertarProgresoAhorro(progreso)
        try {
            SupabaseCliente.cliente.postgrest["progreso_ahorro"].upsert(progreso)
        } catch (e: Exception) {
            Log.e("RepositorioProgresoAhorro", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarProgresoAhorro(progreso: ProgresoAhorroEntidad) {
        progresoAhorroDao.actualizarProgresoAhorro(progreso)
        try {
            SupabaseCliente.cliente.postgrest["progreso_ahorro"].upsert(progreso)
        } catch (e: Exception) {
            Log.e("RepositorioProgresoAhorro", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarProgresoAhorro(progreso: ProgresoAhorroEntidad) {
        progresoAhorroDao.eliminarProgresoAhorro(progreso)
        try {
            SupabaseCliente.cliente.postgrest["progreso_ahorro"].delete {
                filter { eq("idAhorroProgreso", progreso.idAhorroProgreso) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioProgresoAhorro", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
