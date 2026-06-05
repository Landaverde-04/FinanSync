package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.UsuarioEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla USUARIOS.
 * Todas las operaciones de escritura son suspend para ejecutarse en un
 * hilo de fondo mediante Coroutines, evitando bloquear el hilo principal.
 * Las consultas de lectura devuelven Flow para que la UI reaccione
 * automáticamente a los cambios en la base de datos.
 */
@Dao
interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUsuario(usuario: UsuarioEntidad)

    @Update
    suspend fun actualizarUsuario(usuario: UsuarioEntidad)

    @Delete
    suspend fun eliminarUsuario(usuario: UsuarioEntidad)

    // Busca un usuario por su UUID (sincronizado con Supabase Auth)
    @Query("SELECT * FROM usuarios WHERE idUsuario = :idUsuario")
    suspend fun obtenerUsuarioPorId(idUsuario: String): UsuarioEntidad?

    // Emite la lista completa cada vez que cambia la tabla usuarios
    @Query("SELECT * FROM usuarios")
    fun obtenerTodosLosUsuarios(): Flow<List<UsuarioEntidad>>
}
