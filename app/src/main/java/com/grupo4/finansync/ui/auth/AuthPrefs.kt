package com.grupo4.finansync.ui.auth

object AuthPrefs {

    const val PREFS = "finansync_prefs"

    const val PREF_SESION_PREVIA = "finansync_sesion_previa"
    const val PREF_HUELLA_ACTIVA = "finansync_huella_activa"
    const val PREF_CORREO_GUARDADO = "finansync_correo_guardado"
    const val PREF_PASSWORD_CIFRADA = "finansync_password_cifrada"

    /*
     * Se activa únicamente cuando el usuario abrió un link válido
     * de recuperación de contraseña.
     *
     * Esto evita que updateUser() cambie la contraseña de un usuario
     * que ya estaba logueado por error.
     */
    const val PREF_RECUPERACION_PASSWORD_ACTIVA =
        "finansync_recuperacion_password_activa"
}