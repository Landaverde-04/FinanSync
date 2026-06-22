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
        val id = comprobanteDao.insertarComprobante(
            comprobante.copy(sincronizada = false)
        ).toInt()
        try {
            SupabaseCliente.cliente.postgrest["comprobantes"]
                .upsert(comprobante.copy(idComprobante = id))
            comprobanteDao.marcarComoSincronizado(id)
        } catch (e: Exception) {
            Log.e("RepositorioComprobante", "Sin conexión, queda pendiente: ${e.message}")
        }
    }

    /** Sube los comprobantes guardados offline. Devuelve cuántos subió. */
    suspend fun subirPendientes(): Int {
        val pendientes = comprobanteDao.obtenerNoSincronizados()
        var subidos = 0
        for (c in pendientes) {
            try {
                SupabaseCliente.cliente.postgrest["comprobantes"].upsert(c)
                comprobanteDao.marcarComoSincronizado(c.idComprobante)
                subidos++
            } catch (e: Exception) {
                Log.e("RepositorioComprobante", "No se pudo subir ${c.idComprobante}: ${e.message}")
            }
        }
        return subidos
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
