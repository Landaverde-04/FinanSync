package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.ComprobanteEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla COMPROBANTES.
 * Lo utiliza principalmente el módulo de CameraX/OCR (Módulo M2)
 * para guardar y recuperar las imágenes y el texto extraído.
 */
@Dao
interface ComprobanteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarComprobante(comprobante: ComprobanteEntidad)

    @Update
    suspend fun actualizarComprobante(comprobante: ComprobanteEntidad)

    @Delete
    suspend fun eliminarComprobante(comprobante: ComprobanteEntidad)

    @Query("SELECT * FROM comprobantes WHERE idComprobante = :idComprobante")
    suspend fun obtenerComprobantePorId(idComprobante: Int): ComprobanteEntidad?

    // Recupera todos los comprobantes de una transacción (puede haber más de uno)
    @Query("SELECT * FROM comprobantes WHERE idTransaccion = :idTransaccion")
    fun obtenerComprobantesPorTransaccion(idTransaccion: Int): Flow<List<ComprobanteEntidad>>

    // Útil para mostrar comprobantes sin OCR procesado y lanzar el análisis pendiente
    @Query("SELECT * FROM comprobantes WHERE idTransaccion = :idTransaccion AND textoOcr IS NULL")
    suspend fun obtenerComprobantesSinOcr(idTransaccion: Int): List<ComprobanteEntidad>

    @Query("SELECT * FROM comprobantes WHERE idTransaccion = :idTransaccion LIMIT 1")
    suspend fun obtenerComprobantePorTransaccion(idTransaccion: Int): ComprobanteEntidad?
}
