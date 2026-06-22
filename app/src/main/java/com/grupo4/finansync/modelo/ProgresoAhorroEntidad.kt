package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Representa la tabla PROGRESO_AHORRO en la base de datos local.
 * Cada fila es un registro puntual del monto ahorrado dentro de un plan,
 * permitiendo construir un historial de progreso a lo largo del tiempo.
 */
@Serializable
@Entity(
    tableName = "progreso_ahorro",
    foreignKeys = [
        ForeignKey(
            entity = PlanAhorroEntidad::class,
            parentColumns = ["idAhorro"],
            childColumns = ["idAhorro"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idAhorro")]
)
data class ProgresoAhorroEntidad(

    @PrimaryKey(autoGenerate = true)
    val idAhorroProgreso: Int = 0,

    val idAhorro: Int,

    // Monto acumulado en este corte de tiempo
    val montoAhorrado: Double,

    // Marca de tiempo Unix (milisegundos) del momento en que se registró el progreso
    val registradoEn: Long,

    // SOLO LOCAL: false = pendiente de subir (guardado offline); true = ya en la nube.
    @Transient
    val sincronizada: Boolean = true
)
