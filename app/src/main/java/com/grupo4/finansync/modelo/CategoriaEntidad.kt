package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Representa la tabla CATEGORIAS en la base de datos local.
 * Cada categoría pertenece a un usuario y define si agrupa ingresos o gastos.
 *
 * La FK con CASCADE DELETE garantiza que al eliminar un usuario sus categorías
 * se eliminen automáticamente, evitando registros huérfanos.
 */
@Serializable
@Entity(
    tableName = "categorias",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntidad::class,
            parentColumns = ["idUsuario"],
            childColumns = ["idUsuario"],
            // Al borrar el usuario se eliminan sus categorías en cascada
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idUsuario")]
)
data class CategoriaEntidad(

    @PrimaryKey(autoGenerate = true)
    val idCategoria: Int = 0,

    val idUsuario: String,

    val nombreCategoria: String,

    // Valores permitidos: "ingreso" o "gasto"
    val tipo: String
)
