package com.grupo4.finansync.ui.auth

object AuthPrefs {

    const val PREFS = "finansync_prefs"

    const val PREF_SESION_PREVIA =
        "finansync_sesion_previa"

    /*
     * Indica si hay un login con huella configurado.
     * Esta preferencia sola no basta para saber si pertenece
     * al usuario actual.
     */
    const val PREF_HUELLA_ACTIVA =
        "finansync_huella_activa"

    /*
     * Guarda el ID del usuario de Supabase que activó la huella.
     * Así evitamos que otro usuario vea la huella como activa.
     */
    const val PREF_HUELLA_USUARIO_ID =
        "finansync_huella_usuario_id"

    /*
     * Credenciales cifradas.
     *
     * Se usan para:
     * - Login biométrico.
     * - Login offline del último usuario validado.
     */
    const val PREF_CORREO_GUARDADO =
        "finansync_correo_guardado"

    const val PREF_PASSWORD_CIFRADA =
        "finansync_password_cifrada"

    /*
     * Se activa únicamente cuando el usuario abrió un link válido
     * de recuperación de contraseña.
     */
    const val PREF_RECUPERACION_PASSWORD_ACTIVA =
        "finansync_recuperacion_password_activa"

    /*
     * Último usuario validado correctamente con internet.
     * Este usuario será el único que podrá entrar offline.
     */
    const val PREF_ULTIMO_USUARIO_ID =
        "finansync_ultimo_usuario_id"

    const val PREF_ULTIMO_CORREO =
        "finansync_ultimo_correo"

    /*
     * Indica que este dispositivo ya tiene permitido
     * acceso offline para el último usuario validado.
     */
    const val PREF_ACCESO_OFFLINE_ACTIVO =
        "finansync_acceso_offline_activo"

    /*
     * Indica si la sesión actual entró en modo offline.
     */
    const val PREF_MODO_OFFLINE =
        "finansync_modo_offline"
}