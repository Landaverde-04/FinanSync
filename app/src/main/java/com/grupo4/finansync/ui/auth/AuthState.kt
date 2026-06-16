package com.grupo4.finansync.ui.auth

/**
 * Representa los posibles estados de la pantalla de autenticación.
 * El Fragment observa este estado y actualiza la UI en consecuencia.
 */
sealed class AuthState {
    /** Estado inicial, sin ninguna operación en curso */
    object Inactivo : AuthState()

    /** Operación en curso (login o registro) */
    object Cargando : AuthState()

    /** Operación exitosa. idUsuario es el UUID de Supabase */
    data class Exito(val idUsuario: String) : AuthState()

    /** Ocurrió un error. mensaje es texto legible para mostrar al usuario */
    data class Error(val mensaje: String) : AuthState()
}