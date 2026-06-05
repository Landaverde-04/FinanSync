package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.ComprobanteDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.ComprobanteEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de COMPROBANTES.
 * Usado por el módulo M2 (CameraX + OCR) para guardar imágenes
 * y su texto extraído asociado a una transacción.
 */
class RepositorioComprobante(private val comprobanteDao: ComprobanteDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    fun obtenerComprobantesPorTransaccion(idTransaccion: Int): Flow<List<ComprobanteEntidad>> =
        comprobanteDao.obtenerComprobantesPorTransaccion(idTransaccion)

    suspend fun obtenerComprobantePorId(idComprobante: Int): ComprobanteEntidad? =
        comprobanteDao.obtenerComprobantePorId(idComprobante)

    // Devuelve comprobantes sin texto OCR para procesarlos en segundo plano
    suspend fun obtenerComprobantesSinOcr(idTransaccion: Int): List<ComprobanteEntidad> =
        comprobanteDao.obtenerComprobantesSinOcr(idTransaccion)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarComprobante(comprobante: ComprobanteEntidad) {
        comprobanteDao.insertarComprobante(comprobante)
        try {
            SupabaseCliente.cliente.postgrest["comprobantes"].upsert(comprobante)
        } catch (e: Exception) {
            Log.e("RepositorioComprobante", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarComprobante(comprobante: ComprobanteEntidad) {
        comprobanteDao.actualizarComprobante(comprobante)
        try {
            // Se llama principalmente cuando el OCR termina y actualiza textoOcr
            SupabaseCliente.cliente.postgrest["comprobantes"].upsert(comprobante)
        } catch (e: Exception) {
            Log.e("RepositorioComprobante", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarComprobante(comprobante: ComprobanteEntidad) {
        comprobanteDao.eliminarComprobante(comprobante)
        try {
            SupabaseCliente.cliente.postgrest["comprobantes"].delete {
                filter { eq("idComprobante", comprobante.idComprobante) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioComprobante", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
