package com.grupo4.finansync

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.UsuarioEntidad
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ejecutamos la prueba en cuanto la pantalla carga
//        probarConexionCompleta()
    }

//    private fun probarConexionCompleta() {
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                Log.d("PRUEBA_BD", "1. Iniciando base de datos local...")
//                val bd = BaseDatos.obtenerInstancia(applicationContext)
//
//                // 2. Crear un usuario de prueba (Local y en la Nube)
//                val idPrueba = UUID.randomUUID().toString()
//                val usuarioFake = UsuarioEntidad(idPrueba, "test@correo.com", "Usuario Prueba", System.currentTimeMillis())
//                bd.usuarioDao().insertarUsuario(usuarioFake)
//                com.grupo4.finansync.data.remote.SupabaseCliente.cliente.postgrest["usuarios"].upsert(usuarioFake)
//
//                // 3. Crear una categoría de prueba (Local y en la Nube)
//                val categoriaFake = CategoriaEntidad(1, idPrueba, "Almuerzo", "gasto")
//                bd.categoriaDao().insertarCategoria(categoriaFake)
//                com.grupo4.finansync.data.remote.SupabaseCliente.cliente.postgrest["categorias"].upsert(categoriaFake)
//
//                // 4. Crear el Repositorio y la Transacción
//                val repositorio = RepositorioTransaccion(bd.transaccionDao())
//                val nuevaTransaccion = TransaccionEntidad(
//                    idTransaccion = 1,
//                    idUsuario = idPrueba,
//                    idCategoria = 1,
//                    monto = 12.50,
//                    tipo = "gasto",
//                    descripcion = "Prueba de conexión a Supabase",
//                    latitud = null,
//                    longitud = null,
//                    creadoEn = System.currentTimeMillis()
//                )
//
//                Log.d("PRUEBA_BD", "2. Guardando en Room e intentando subir a Supabase...")
//                repositorio.insertarTransaccion(nuevaTransaccion)
//                Log.d("PRUEBA_BD", "¡Proceso de guardado terminado sin errores!")
//
//                // 5. Leer la base de datos local
//                repositorio.obtenerTransaccionesPorUsuario(idPrueba).collect { lista ->
//                    Log.d("PRUEBA_BD", "3. LECTURA: Tienes ${lista.size} transacción(es) en SQLite.")
//                    lista.forEach {
//                        Log.d("PRUEBA_BD", "-> Gasto: $${it.monto} en ${it.descripcion}")
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e("PRUEBA_BD", "Error grave en la prueba: ${e.message}")
//            }
//        }
//    }
}