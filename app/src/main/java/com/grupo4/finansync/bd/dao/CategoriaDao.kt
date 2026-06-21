package com.grupo4.finansync.bd.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.grupo4.finansync.modelo.CategoriaEntidad
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la tabla CATEGORIAS.
 * Las consultas filtran siempre por idUsuario para garantizar que cada
 * usuario solo vea sus propias categorías (aislamiento de datos).
 */
@Dao
interface CategoriaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarCategoria(categoria: CategoriaEntidad): Long

    @Update
    suspend fun actualizarCategoria(categoria: CategoriaEntidad)

    @Delete
    suspend fun eliminarCategoria(categoria: CategoriaEntidad)

    @Query("SELECT * FROM categorias WHERE idCategoria = :idCategoria")
    suspend fun obtenerCategoriaPorId(idCategoria: Int): CategoriaEntidad?

    // Devuelve todas las categorías de un usuario; Flow notifica cambios en tiempo real
    @Query("SELECT * FROM categorias WHERE idUsuario = :idUsuario")
    fun obtenerCategoriasPorUsuario(idUsuario: String): Flow<List<CategoriaEntidad>>

    // Filtra además por tipo ("ingreso" o "gasto") para mostrar listas específicas en la UI
    @Query("SELECT * FROM categorias WHERE idUsuario = :idUsuario AND tipo = :tipo")
    fun obtenerCategoriasPorUsuarioYTipo(idUsuario: String, tipo: String): Flow<List<CategoriaEntidad>>
}
