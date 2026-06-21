package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.TransaccionEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla TRANSACCIONES.
 * Es el DAO más consultado del proyecto porque la pantalla principal
 * y los gráficos dependen de estos datos. Se exponen varias consultas
 * específicas para evitar traer más datos de los necesarios.
 */
@Dao
interface TransaccionDao {

    // Devuelve el id (rowId) generado por Room para la transacción insertada.
    // Necesario para ligar un comprobante a la transacción recién creada.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTransaccion(transaccion: TransaccionEntidad): Long

    @Update
    suspend fun actualizarTransaccion(transaccion: TransaccionEntidad)

    @Delete
    suspend fun eliminarTransaccion(transaccion: TransaccionEntidad)
    @Query("DELETE FROM transacciones WHERE idTransaccion = :id")
    suspend fun eliminarPorId(id: Int)

    @Query("SELECT * FROM transacciones WHERE idTransaccion = :idTransaccion")
    suspend fun obtenerTransaccionPorId(idTransaccion: Int): TransaccionEntidad?

    // Historial completo de un usuario ordenado del más reciente al más antiguo
    @Query("SELECT * FROM transacciones WHERE idUsuario = :idUsuario ORDER BY creadoEn DESC")
    fun obtenerTransaccionesPorUsuario(idUsuario: String): Flow<List<TransaccionEntidad>>

    // Filtra por tipo ("ingreso" o "gasto") para los cálculos de resumen
    @Query("SELECT * FROM transacciones WHERE idUsuario = :idUsuario AND tipo = :tipo ORDER BY creadoEn DESC")
    fun obtenerTransaccionesPorUsuarioYTipo(idUsuario: String, tipo: String): Flow<List<TransaccionEntidad>>

    // Útil para el módulo de gráficos: agrupa gastos/ingresos por categoría
    @Query("SELECT * FROM transacciones WHERE idUsuario = :idUsuario AND idCategoria = :idCategoria ORDER BY creadoEn DESC")
    fun obtenerTransaccionesPorUsuarioYCategoria(idUsuario: String, idCategoria: Int): Flow<List<TransaccionEntidad>>

    // Suma total por tipo para calcular balance; devuelve 0.0 si no hay registros
    @Query("SELECT COALESCE(SUM(monto), 0.0) FROM transacciones WHERE idUsuario = :idUsuario AND tipo = :tipo")
    suspend fun sumarMontoTransaccionesPorTipo(idUsuario: String, tipo: String): Double

    @Query("SELECT * FROM transacciones WHERE idUsuario = :idUsuario ORDER BY creadoEn DESC LIMIT 5")
    fun obtenerTransaccionesRecientes(idUsuario: String): Flow<List<TransaccionEntidad>>

}
