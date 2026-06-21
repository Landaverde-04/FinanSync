package com.grupo4.finansync.modelo


fun CategoriaEntidad.obtenerEmoji(): String {
    if (nombreCategoria.isEmpty()) return "🏷️"
    val partes = nombreCategoria.split(" ", limit = 2)
    if (partes.size > 1 && partes[0].length <= 4) {
        return partes[0]
    }
    return "🏷️"
}

fun CategoriaEntidad.obtenerNombreLimpio(): String {
    if (nombreCategoria.isEmpty()) return ""
    val partes = nombreCategoria.split(" ", limit = 2)
    if (partes.size > 1 && partes[0].length <= 4) {
        return partes[1]
    }
    return nombreCategoria
}
