package com.grupo4.finansync.data.sync

import android.util.Log
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioComprobante
import com.grupo4.finansync.data.repositorio.RepositorioProgresoAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.ComprobanteEntidad
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import com.grupo4.finansync.modelo.PresupuestoEntidad
import com.grupo4.finansync.modelo.ProgresoAhorroEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.UsuarioEntidad
import io.github.jan.supabase.postgrest.postgrest

/**
 * Sincronización de BAJADA: trae lo que hay en Supabase (nube) y lo guarda en Room (local).
 *
 * Por qué existe: la app guarda offline en Room y sube a Supabase cuando hay red.
 * Pero al instalar la app en otro equipo (o tras reinstalar), Room arranca vacío.
 * Este SyncManager "rellena" Room con lo que ya está respaldado en la nube.
 *
 * Es de UNA dirección (nube -> local). No resuelve conflictos porque en esta app
 * casi nadie escribe directo en Supabase: el flujo normal es app -> nube.
 *
 * IMPORTANTE: el orden de descarga respeta las llaves foráneas. Hay que bajar
 * primero las tablas "padre" (usuarios) y luego las "hijas" (transacciones, etc.),
 * o SQLite rechaza los registros hijos por FK.
 */
class SyncManager(private val bd: BaseDatos) {

    /**
     * Descarga todas las tablas del usuario desde Supabase a Room.
     * Devuelve true si terminó sin excepciones; false si algo falló (ej. sin red).
     */
    suspend fun sincronizarTodo(idUsuario: String): Boolean {
        return try {
            // ── PASO 0: SUBIR lo guardado offline ANTES de bajar ──
            // Si no subimos primero, la bajada pisaría las transacciones locales
            // que aún no están en la nube y se perderían.
            val repoTransaccion = RepositorioTransaccion(bd.transaccionDao())
            val subidas = repoTransaccion.subirPendientes(idUsuario)
            if (subidas > 0) Log.d("SyncManager", "Subidas $subidas transacciones pendientes")

            // También subir progreso de ahorro y comprobantes pendientes
            val progresosSubidos = RepositorioProgresoAhorro(bd.progresoAhorroDao()).subirPendientes()
            if (progresosSubidos > 0) Log.d("SyncManager", "Subidos $progresosSubidos progresos de ahorro")
            val comprobantesSubidos = RepositorioComprobante(bd.comprobanteDao()).subirPendientes()
            if (comprobantesSubidos > 0) Log.d("SyncManager", "Subidos $comprobantesSubidos comprobantes")

            // ── Orden por dependencias (padres -> hijos) ──

            // 1. USUARIOS (no depende de nadie)
            val usuarios = SupabaseCliente.cliente.postgrest["usuarios"]
                .select { filter { eq("idUsuario", idUsuario) } }
                .decodeList<UsuarioEntidad>()
            usuarios.forEach { bd.usuarioDao().insertarUsuario(it) }

            // 2. CATEGORIAS (depende de usuario)
            val categorias = SupabaseCliente.cliente.postgrest["categorias"]
                .select { filter { eq("idUsuario", idUsuario) } }
                .decodeList<CategoriaEntidad>()
            categorias.forEach { bd.categoriaDao().insertarCategoria(it) }

            // 3. PLANES_AHORRO (depende de usuario)
            val planes = SupabaseCliente.cliente.postgrest["planes_ahorro"]
                .select { filter { eq("idUsuario", idUsuario) } }
                .decodeList<PlanAhorroEntidad>()
            planes.forEach { bd.planAhorroDao().insertarPlanAhorro(it) }

            // 4. TRANSACCIONES (depende de usuario + categoría)
            val transacciones = SupabaseCliente.cliente.postgrest["transacciones"]
                .select { filter { eq("idUsuario", idUsuario) } }
                .decodeList<TransaccionEntidad>()
            transacciones.forEach { bd.transaccionDao().insertarTransaccion(it) }

            // 5. COMPROBANTES (depende de transacción)
            //    No tienen idUsuario; bajamos todos y dejamos que la FK filtre los válidos.
            val comprobantes = SupabaseCliente.cliente.postgrest["comprobantes"]
                .select()
                .decodeList<ComprobanteEntidad>()
            comprobantes.forEach {
                try { bd.comprobanteDao().insertarComprobante(it) } catch (_: Exception) {}
            }

            // 6. PROGRESO_AHORRO (depende de plan)
            val progresos = SupabaseCliente.cliente.postgrest["progreso_ahorro"]
                .select()
                .decodeList<ProgresoAhorroEntidad>()
            progresos.forEach {
                try { bd.progresoAhorroDao().insertarProgresoAhorro(it) } catch (_: Exception) {}
            }

            // 7. PRESUPUESTO (depende de usuario + categoría)
            val presupuestos = SupabaseCliente.cliente.postgrest["presupuesto"]
                .select { filter { eq("idUsuario", idUsuario) } }
                .decodeList<PresupuestoEntidad>()
            presupuestos.forEach { bd.presupuestoDao().insertarPresupuesto(it) }

            Log.d("SyncManager", "Sincronización completada para $idUsuario")
            true
        } catch (e: Exception) {
            // Sin red o algún problema: la app sigue funcionando con lo que tenga en Room
            Log.e("SyncManager", "Error al sincronizar: ${e.message}")
            false
        }
    }
}
