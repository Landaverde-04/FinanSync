package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.PlanAhorroDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de PLANES_AHORRO.
 * Usado por el módulo M4 para mostrar y gestionar las estrategias
 * de ahorro del usuario (meta fija, porcentaje o monto fijo).
 */
class RepositorioPlanAhorro(private val planAhorroDao: PlanAhorroDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    fun obtenerPlanesAhorroPorUsuario(idUsuario: String): Flow<List<PlanAhorroEntidad>> =
        planAhorroDao.obtenerPlanesAhorroPorUsuario(idUsuario)

    // Solo planes activos para el cálculo del período actual
    fun obtenerPlanesAhorroActivosPorUsuario(idUsuario: String): Flow<List<PlanAhorroEntidad>> =
        planAhorroDao.obtenerPlanesAhorroActivosPorUsuario(idUsuario)

    suspend fun obtenerPlanAhorroPorId(idAhorro: Int): PlanAhorroEntidad? =
        planAhorroDao.obtenerPlanAhorroPorId(idAhorro)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarPlanAhorro(planAhorro: PlanAhorroEntidad) {
        planAhorroDao.insertarPlanAhorro(planAhorro)
        try {
            SupabaseCliente.cliente.postgrest["planes_ahorro"].upsert(planAhorro)
        } catch (e: Exception) {
            Log.e("RepositorioPlanAhorro", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarPlanAhorro(planAhorro: PlanAhorroEntidad) {
        planAhorroDao.actualizarPlanAhorro(planAhorro)
        try {
            // Se llama al activar/desactivar un plan o cambiar su configuración
            SupabaseCliente.cliente.postgrest["planes_ahorro"].upsert(planAhorro)
        } catch (e: Exception) {
            Log.e("RepositorioPlanAhorro", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarPlanAhorro(planAhorro: PlanAhorroEntidad) {
        planAhorroDao.eliminarPlanAhorro(planAhorro)
        try {
            SupabaseCliente.cliente.postgrest["planes_ahorro"].delete {
                filter { eq("idAhorro", planAhorro.idAhorro) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioPlanAhorro", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
