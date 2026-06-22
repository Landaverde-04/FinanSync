package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Representa la tabla COMPROBANTES en la base de datos local.
 * Almacena la imagen capturada por CameraX y el texto extraído por ML Kit OCR
 * asociados a una transacción específica.
 *
 * textoOcr es nullable porque el reconocimiento puede fallar o no haberse
 * ejecutado aún al momento de guardar el registro.
 */
@Serializable
@Entity(
    tableName = "comprobantes",
    foreignKeys = [
        ForeignKey(
            entity = TransaccionEntidad::class,
            parentColumns = ["idTransaccion"],
            childColumns = ["idTransaccion"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("idTransaccion")]
)
data class ComprobanteEntidad(

    @PrimaryKey(autoGenerate = true)
    val idComprobante: Int = 0,

    val idTransaccion: Int,

    // URL local o remota (Supabase Storage) de la imagen del comprobante
    val urlImagen: String,

    // Texto extraído por OCR; null si aún no se procesó o falló el reconocimiento
    val textoOcr: String? = null,

    val creadoEn: Long,

    // SOLO LOCAL: false = pendiente de subir (guardado offline); true = ya en la nube.
    @Transient
    val sincronizada: Boolean = true
)
