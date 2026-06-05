package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.CategoriaDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.CategoriaEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de CATEGORIAS.
 * Expone los Flow del DAO para que el ViewModel los observe,
 * y maneja la sincronización con Supabase en las operaciones de escritura.
 */
class RepositorioCategoria(private val categoriaDao: CategoriaDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    fun obtenerCategoriasPorUsuario(idUsuario: String): Flow<List<CategoriaEntidad>> =
        categoriaDao.obtenerCategoriasPorUsuario(idUsuario)

    fun obtenerCategoriasPorUsuarioYTipo(idUsuario: String, tipo: String): Flow<List<CategoriaEntidad>> =
        categoriaDao.obtenerCategoriasPorUsuarioYTipo(idUsuario, tipo)

    suspend fun obtenerCategoriaPorId(idCategoria: Int): CategoriaEntidad? =
        categoriaDao.obtenerCategoriaPorId(idCategoria)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarCategoria(categoria: CategoriaEntidad) {
        categoriaDao.insertarCategoria(categoria)
        try {
            SupabaseCliente.cliente.postgrest["categorias"].upsert(categoria)
        } catch (e: Exception) {
            Log.e("RepositorioCategoria", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarCategoria(categoria: CategoriaEntidad) {
        categoriaDao.actualizarCategoria(categoria)
        try {
            SupabaseCliente.cliente.postgrest["categorias"].upsert(categoria)
        } catch (e: Exception) {
            Log.e("RepositorioCategoria", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarCategoria(categoria: CategoriaEntidad) {
        categoriaDao.eliminarCategoria(categoria)
        try {
            SupabaseCliente.cliente.postgrest["categorias"].delete {
                filter { eq("idCategoria", categoria.idCategoria) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioCategoria", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
