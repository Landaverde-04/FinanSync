package com.grupo4.finansync.data.repositorio

import android.util.Log
import com.grupo4.finansync.bd.dao.UsuarioDao
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.modelo.UsuarioEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de USUARIOS.
 * Aplica estrategia offline-first: Room es la fuente de verdad local.
 * Cada escritura se replica en Supabase dentro de un try-catch para que
 * los errores de red no interrumpan la operación local.
 */
class RepositorioUsuario(private val usuarioDao: UsuarioDao) {

    // ── LECTURA ──────────────────────────────────────────────────────────────

    // El Flow emite automáticamente cuando cambia la tabla; el ViewModel lo observa
    fun obtenerTodosLosUsuarios(): Flow<List<UsuarioEntidad>> =
        usuarioDao.obtenerTodosLosUsuarios()

    suspend fun obtenerUsuarioPorId(idUsuario: String): UsuarioEntidad? =
        usuarioDao.obtenerUsuarioPorId(idUsuario)

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    suspend fun insertarUsuario(usuario: UsuarioEntidad) {
        usuarioDao.insertarUsuario(usuario)
        try {
            SupabaseCliente.cliente.postgrest["usuarios"].upsert(usuario)
        } catch (e: Exception) {
            Log.e("RepositorioUsuario", "Error al sincronizar inserción: ${e.message}")
        }
    }

    suspend fun actualizarUsuario(usuario: UsuarioEntidad) {
        usuarioDao.actualizarUsuario(usuario)
        try {
            // upsert reemplaza el registro si ya existe (igual que REPLACE en Room)
            SupabaseCliente.cliente.postgrest["usuarios"].upsert(usuario)
        } catch (e: Exception) {
            Log.e("RepositorioUsuario", "Error al sincronizar actualización: ${e.message}")
        }
    }

    suspend fun eliminarUsuario(usuario: UsuarioEntidad) {
        usuarioDao.eliminarUsuario(usuario)
        try {
            SupabaseCliente.cliente.postgrest["usuarios"].delete {
                filter { eq("idUsuario", usuario.idUsuario) }
            }
        } catch (e: Exception) {
            Log.e("RepositorioUsuario", "Error al sincronizar eliminación: ${e.message}")
        }
    }
}
