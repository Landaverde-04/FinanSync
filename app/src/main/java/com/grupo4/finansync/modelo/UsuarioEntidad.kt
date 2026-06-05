package com.grupo4.finansync.modelo

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Representa la tabla USUARIOS en la base de datos local.
 * El idUsuario es un UUID generado por Supabase Auth; se almacena como String
 * para mantener coherencia con el backend remoto.
 */
@Serializable
@Entity(tableName = "usuarios")
data class UsuarioEntidad(

    @PrimaryKey
    val idUsuario: String,

    val email: String,

    val nombreUsuario: String,

    // Marca de tiempo Unix (milisegundos) de cuando se creó el registro
    val creadoEn: Long
)
