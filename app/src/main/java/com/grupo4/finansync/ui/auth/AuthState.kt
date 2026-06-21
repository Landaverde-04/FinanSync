package com.grupo4.finansync.ui.auth

sealed class AuthState {
    object Inactivo : AuthState()
    object Cargando : AuthState()
    data class Exito(val idUsuario: String) : AuthState()
    data class Error(val mensaje: String) : AuthState()
    object RecuperacionEnviada : AuthState()
    object PasswordActualizada : AuthState()
}