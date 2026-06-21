package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla PLANES_AHORRO.
 * El módulo M4 (ahorro) usa este DAO para mostrar el plan vigente
 * y permitir al usuario crear o pausar estrategias de ahorro.
 */
@Dao
interface PlanAhorroDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPlanAhorro(planAhorro: PlanAhorroEntidad)

    @Update
    suspend fun actualizarPlanAhorro(planAhorro: PlanAhorroEntidad)

    @Delete
    suspend fun eliminarPlanAhorro(planAhorro: PlanAhorroEntidad)

    @Query("SELECT * FROM planes_ahorro WHERE idAhorro = :idAhorro")
    suspend fun obtenerPlanAhorroPorId(idAhorro: Int): PlanAhorroEntidad?

    // Todos los planes del usuario (activos e inactivos) para mostrar historial
    @Query("SELECT * FROM planes_ahorro WHERE idUsuario = :idUsuario")
    fun obtenerPlanesAhorroPorUsuario(idUsuario: String): Flow<List<PlanAhorroEntidad>>

    // Solo los planes activos; la UI los usa para calcular el ahorro del período actual
    @Query("SELECT * FROM planes_ahorro WHERE idUsuario = :idUsuario AND activo = 1")
    fun obtenerPlanesAhorroActivosPorUsuario(idUsuario: String): Flow<List<PlanAhorroEntidad>>
}
