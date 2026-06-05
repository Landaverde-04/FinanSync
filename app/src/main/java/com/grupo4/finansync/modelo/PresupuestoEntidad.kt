package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Representa la tabla PRESUPUESTO en la base de datos local.
 * Define el límite de gasto que el usuario establece por categoría y período
 * (ej. "mensual", "semanal"). Se compara contra las TRANSACCIONES para
 * determinar si el usuario está dentro de su presupuesto.
 */
@Serializable
@Entity(
    tableName = "presupuesto",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntidad::class,
            parentColumns = ["idUsuario"],
            childColumns = ["idUsuario"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoriaEntidad::class,
            parentColumns = ["idCategoria"],
            childColumns = ["idCategoria"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idUsuario"), Index("idCategoria")]
)
data class PresupuestoEntidad(

    @PrimaryKey(autoGenerate = true)
    val idPresupuesto: Int = 0,

    val idUsuario: String,

    val idCategoria: Int,

    // Monto máximo permitido en el período definido
    val montoLimite: Double,

    // Período de vigencia del presupuesto, ej: "mensual", "semanal", "anual"
    val periodo: String
)
