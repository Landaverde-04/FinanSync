package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.PresupuestoEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla PRESUPUESTO.
 * El módulo M4 compara los montos de TRANSACCIONES contra los límites
 * definidos aquí para alertar al usuario cuando está por agotarse su presupuesto.
 */
@Dao
interface PresupuestoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarPresupuesto(presupuesto: PresupuestoEntidad): Long

    @Update
    suspend fun actualizarPresupuesto(presupuesto: PresupuestoEntidad)

    @Delete
    suspend fun eliminarPresupuesto(presupuesto: PresupuestoEntidad)

    @Query("SELECT * FROM presupuesto WHERE idPresupuesto = :idPresupuesto")
    suspend fun obtenerPresupuestoPorId(idPresupuesto: Int): PresupuestoEntidad?

    // Todos los presupuestos del usuario para mostrar en el resumen
    @Query("SELECT * FROM presupuesto WHERE idUsuario = :idUsuario")
    fun obtenerPresupuestosPorUsuario(idUsuario: String): Flow<List<PresupuestoEntidad>>

    // Recupera el presupuesto de una categoría específica para validar el límite
    @Query("SELECT * FROM presupuesto WHERE idUsuario = :idUsuario AND idCategoria = :idCategoria")
    suspend fun obtenerPresupuestoPorUsuarioYCategoria(idUsuario: String, idCategoria: Int): PresupuestoEntidad?

    // Filtra por período para mostrar únicamente los presupuestos del ciclo actual
    @Query("SELECT * FROM presupuesto WHERE idUsuario = :idUsuario AND periodo = :periodo")
    fun obtenerPresupuestosPorUsuarioYPeriodo(idUsuario: String, periodo: String): Flow<List<PresupuestoEntidad>>
}
