package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Representa la tabla PLANES_AHORRO en la base de datos local.
 * Un plan define la estrategia de ahorro elegida por el usuario.
 *
 * Los tres campos de monto son opcionales porque cada método de ahorro
 * utiliza un subconjunto diferente:
 *  - "meta fija"   → usa montoMeta
 *  - "porcentaje"  → usa porcentaje
 *  - "monto fijo"  → usa montoFijo
 */
@Serializable
@Entity(
    tableName = "planes_ahorro",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntidad::class,
            parentColumns = ["idUsuario"],
            childColumns = ["idUsuario"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idUsuario")]
)
data class PlanAhorroEntidad(

    @PrimaryKey(autoGenerate = true)
    val idAhorro: Int = 0,

    val idUsuario: String,

    // Describe la estrategia: "meta fija", "porcentaje", "monto fijo", etc.
    val metodo: String,

    val montoMeta: Double? = null,

    // Porcentaje del ingreso destinado al ahorro (0.0 – 100.0)
    val porcentaje: Double? = null,

    val montoFijo: Double? = null,

    // Indica si el plan está vigente; permite desactivarlo sin borrarlo
    val activo: Boolean
)
