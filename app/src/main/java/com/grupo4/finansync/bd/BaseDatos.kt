package com.grupo4.finansync.bd

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.grupo4.finansync.bd.dao.CategoriaDao
import com.grupo4.finansync.bd.dao.ComprobanteDao
import com.grupo4.finansync.bd.dao.PlanAhorroDao
import com.grupo4.finansync.bd.dao.PresupuestoDao
import com.grupo4.finansync.bd.dao.ProgresoAhorroDao
import com.grupo4.finansync.bd.dao.TransaccionDao
import com.grupo4.finansync.bd.dao.UsuarioDao
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.ComprobanteEntidad
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import com.grupo4.finansync.modelo.PresupuestoEntidad
import com.grupo4.finansync.modelo.ProgresoAhorroEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.UsuarioEntidad

/**
 * Punto de entrada único a la base de datos Room de FinanSync.
 *
 * Se incrementa [version] cada vez que se modifique el esquema.
 * [fallbackToDestructiveMigration] borra y recrea la BD cuando la versión
 * cambia sin una Migration definida; aceptable en etapa de desarrollo.
 *
 * El Singleton garantiza que solo exista una instancia de la BD en memoria,
 * evitando problemas de concurrencia y consumo innecesario de recursos.
 */
@Database(
    entities = [
        UsuarioEntidad::class,
        CategoriaEntidad::class,
        TransaccionEntidad::class,
        ComprobanteEntidad::class,
        PlanAhorroEntidad::class,
        ProgresoAhorroEntidad::class,
        PresupuestoEntidad::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BaseDatos : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun transaccionDao(): TransaccionDao
    abstract fun comprobanteDao(): ComprobanteDao
    abstract fun planAhorroDao(): PlanAhorroDao
    abstract fun progresoAhorroDao(): ProgresoAhorroDao
    abstract fun presupuestoDao(): PresupuestoDao


    companion object {

        // @Volatile asegura que el valor de INSTANCIA sea siempre el más reciente
        // en todos los hilos, evitando leer una copia desactualizada de la caché
        @Volatile
        private var INSTANCIA: BaseDatos? = null

        fun obtenerInstancia(contexto: Context): BaseDatos {
            // Si ya existe una instancia se devuelve directamente
            return INSTANCIA ?: synchronized(this) {
                // Doble verificación dentro del bloque sincronizado por si dos
                // hilos llegaron al mismo tiempo al primer null check
                val instancia = Room.databaseBuilder(
                    contexto.applicationContext,
                    BaseDatos::class.java,
                    "finansync_bd"
                )
                    // Destruye y recrea la BD si el esquema cambia sin Migration.
                    // En producción se debe reemplazar por migraciones explícitas.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCIA = instancia
                instancia
            }
        }
    }
}
