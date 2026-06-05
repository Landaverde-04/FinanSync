package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.ProgresoAhorroEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla PROGRESO_AHORRO.
 * Permite construir la línea de tiempo del ahorro de un plan específico
 * y calcular el acumulado total para compararlo con la meta.
 */
@Dao
interface ProgresoAhorroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarProgresoAhorro(progreso: ProgresoAhorroEntidad)

    @Update
    suspend fun actualizarProgresoAhorro(progreso: ProgresoAhorroEntidad)

    @Delete
    suspend fun eliminarProgresoAhorro(progreso: ProgresoAhorroEntidad)

    // Historial de registros de un plan, del más reciente al más antiguo
    @Query("SELECT * FROM progreso_ahorro WHERE idAhorro = :idAhorro ORDER BY registradoEn DESC")
    fun obtenerProgresoPorPlan(idAhorro: Int): Flow<List<ProgresoAhorroEntidad>>

    // Suma acumulada del plan; se usa para calcular el porcentaje de avance hacia la meta
    @Query("SELECT COALESCE(SUM(montoAhorrado), 0.0) FROM progreso_ahorro WHERE idAhorro = :idAhorro")
    suspend fun sumarMontoAhorradoPorPlan(idAhorro: Int): Double

    // Último registro del plan para mostrar el progreso más reciente en la UI
    @Query("SELECT * FROM progreso_ahorro WHERE idAhorro = :idAhorro ORDER BY registradoEn DESC LIMIT 1")
    suspend fun obtenerUltimoProgresoPorPlan(idAhorro: Int): ProgresoAhorroEntidad?
}
