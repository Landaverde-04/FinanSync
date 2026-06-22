package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Representa la tabla TRANSACCIONES en la base de datos local.
 * Es la entidad central del proyecto: registra cada movimiento financiero
 * del usuario junto con su categoría y, de forma opcional, su ubicación GPS.
 *
 * Se definen dos FKs:
 *  - idUsuario → USUARIOS: al borrar el usuario se eliminan sus transacciones.
 *  - idCategoria → CATEGORIAS: al borrar la categoría se hace lo mismo.
 */
@Serializable
@Entity(
    tableName = "transacciones",
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
    // Los índices evitan lecturas lentas al filtrar por usuario o categoría
    indices = [Index("idUsuario"), Index("idCategoria")]
)
data class TransaccionEntidad(

    @PrimaryKey(autoGenerate = true)
    val idTransaccion: Int = 0,

    val idUsuario: String,

    val idCategoria: Int,

    val monto: Double,

    // Valores permitidos: "ingreso" o "gasto"
    val tipo: String,

    val descripcion: String,

    // Coordenadas opcionales capturadas al registrar la transacción
    val latitud: Double? = null,

    val longitud: Double? = null,

    // Marca de tiempo Unix (milisegundos)
    val creadoEn: Long,

    // SOLO LOCAL: indica si esta transacción ya se subió a Supabase.
    // @Transient evita que se envíe a la nube (Supabase no necesita esta columna).
    // false = pendiente de subir (guardada offline); true = ya sincronizada.
    @Transient
    val sincronizada: Boolean = true
)
