package com.grupo4.finansync.ui.transaccion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioComprobante
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioProgresoAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.ComprobanteEntidad
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import com.grupo4.finansync.modelo.ProgresoAhorroEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.UsuarioEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransaccionViewModel(
    private val repositorio: RepositorioTransaccion,
    // Repositorios extra solo para sembrar datos de prueba (mock). Se quitan cuando M2/M3 estén listos.
    private val repositorioUsuario: RepositorioUsuario,
    private val repositorioCategoria: RepositorioCategoria,
    // Repositorios de ahorro: para destinar parte de un ingreso a un plan de ahorro
    private val repositorioPlanAhorro: RepositorioPlanAhorro,
    private val repositorioProgresoAhorro: RepositorioProgresoAhorro,
    // Para guardar la foto + texto OCR del comprobante ligado a la transacción
    private val repositorioComprobante: RepositorioComprobante
) : ViewModel() {

    // Lista de categorías del usuario, que el spinner va a observar.
    private val _categorias = MutableStateFlow<List<CategoriaEntidad>>(emptyList())
    val categorias: StateFlow<List<CategoriaEntidad>> = _categorias.asStateFlow()

    // ── AHORRO EN INGRESOS ────────────────────────────────────────────────────

    // Planes de ahorro activos del usuario, para mostrarlos en el selector.
    private val _planesActivos = MutableStateFlow<List<PlanAhorroEntidad>>(emptyList())
    val planesActivos: StateFlow<List<PlanAhorroEntidad>> = _planesActivos.asStateFlow()

    /** Carga los planes de ahorro ACTIVOS del usuario para el spinner de ahorro. */
    fun cargarPlanesActivos(idUsuario: String) {
        viewModelScope.launch {
            repositorioPlanAhorro.obtenerPlanesAhorroActivosPorUsuario(idUsuario).collect { lista ->
                _planesActivos.value = lista
            }
        }
    }

    /**
     * Calcula el monto que se SUGIERE ahorrar al elegir un plan, según su método:
     *  - "porcentaje": ese % del ingreso (ej. 10% de $100 = $10)
     *  - "monto fijo"/"fijo": el monto fijo configurado (ej. $3)
     *  - otro: 0 (que el usuario lo escriba)
     * No aplica todavía el límite del faltante; eso se valida al guardar.
     */
    fun calcularSugerido(plan: PlanAhorroEntidad, montoIngreso: Double): Double {
        val sugerido = when {
            plan.metodo.contains("porcentaje", ignoreCase = true) ->
                montoIngreso * (plan.porcentaje ?: 0.0) / 100.0
            else ->
                plan.montoFijo ?: 0.0
        }
        // Redondeamos a 2 decimales para evitar valores tipo 9.999999
        return Math.round(sugerido * 100.0) / 100.0
    }

    /**
     * Devuelve cuánto FALTA para completar la meta de un plan:
     *   faltante = montoMeta - (suma de lo ya ahorrado)
     * Si el plan no tiene meta, devuelve un valor muy grande (sin tope práctico).
     * Es suspend porque consulta la BD (la suma del progreso).
     */
    suspend fun obtenerFaltante(idAhorro: Int, montoMeta: Double?): Double {
        val yaAhorrado = repositorioProgresoAhorro.sumarMontoAhorradoPorPlan(idAhorro)
        val meta = montoMeta ?: Double.MAX_VALUE
        val faltante = meta - yaAhorrado
        return if (faltante < 0) 0.0 else faltante
    }

    /**
     * TEMPORAL (mock): crea un usuario y varias categorías de prueba si no existen,
     * para que las transacciones tengan a quién "pertenecer" y el spinner muestre opciones.
     * Cuando M2 (login) y M3 (categorías) estén listos, este método se elimina.
     */
    fun sembrarDatosDePrueba(idUsuario: String) {
        viewModelScope.launch {
            // 1. Crear el usuario de prueba (REPLACE: si ya existe, no pasa nada)
            repositorioUsuario.insertarUsuario(
                UsuarioEntidad(
                    idUsuario = idUsuario,
                    email = "prueba@finansync.com",
                    nombreUsuario = "Usuario Prueba",
                    creadoEn = System.currentTimeMillis()
                )
            )

            // 2. Crear varias categorías solo si todavía no hay ninguna para el usuario
            val yaExiste = repositorioCategoria.obtenerCategoriaPorId(1)
            if (yaExiste == null) {
                val categoriasMock = listOf(
                    "Comida" to "gasto",
                    "Transporte" to "gasto",
                    "Entretenimiento" to "gasto",
                    "Servicios" to "gasto",
                    "Salario" to "ingreso",
                    "Otros" to "gasto"
                )
                for ((nombre, tipo) in categoriasMock) {
                    repositorioCategoria.insertarCategoria(
                        CategoriaEntidad(
                            idCategoria = 0,          // Room asigna el id automáticamente
                            idUsuario = idUsuario,
                            nombreCategoria = nombre,
                            tipo = tipo
                        )
                    )
                }
            }
        }
    }

    /**
     * Carga en el spinner SOLO las categorías del tipo indicado ("ingreso" o "gasto").
     * Usa la consulta del DAO que ya filtra por tipo, así un gasto no muestra
     * categorías de ingreso y viceversa.
     *
     * Se llama cada vez que el usuario cambia de pestaña.
     */
    fun cargarCategoriasPorTipo(idUsuario: String, tipo: String) {
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuarioYTipo(idUsuario, tipo).collect { lista ->
                _categorias.value = lista
            }
        }
    }

    // 1. ESTADO DE LA UI: Aquí guardamos la lista de transacciones que verá el usuario
    // El guion bajo (_) es privado para que la vista no pueda modificar la lista directamente.
    private val _transacciones = MutableStateFlow<List<TransaccionEntidad>>(emptyList())
    val transacciones: StateFlow<List<TransaccionEntidad>> = _transacciones.asStateFlow()

    // Mapa idCategoria -> nombre, para que la lista muestre el nombre y no el número.
    private val _mapaCategorias = MutableStateFlow<Map<Int, String>>(emptyMap())
    val mapaCategorias: StateFlow<Map<Int, String>> = _mapaCategorias.asStateFlow()

    // 2. LECTURA CONTINUA: Empezamos a escuchar la base de datos
    fun cargarDatosUsuario(idUsuario: String) {
        viewModelScope.launch {
            // "collect" se queda escuchando. Si Room detecta un cambio, actualizará _transacciones automáticamente
            repositorio.obtenerTransaccionesPorUsuario(idUsuario).collect { listaActualizada ->
                _transacciones.value = listaActualizada
            }
        }
        // En paralelo, mantenemos actualizado el mapa de nombres de categorías
        viewModelScope.launch {
            repositorioCategoria.obtenerCategoriasPorUsuario(idUsuario).collect { categorias ->
                // Convertimos la lista en un mapa { idCategoria -> nombre }
                _mapaCategorias.value = categorias.associate { it.idCategoria to it.nombreCategoria }
            }
        }
    }

    // 3. ESCRITURA: Mandamos a guardar y el ViewModel maneja el hilo secundario (Coroutines)
    //    rutaFoto/textoOcr son opcionales: si vienen, se guarda un comprobante ligado.
    fun agregarTransaccion(
        transaccion: TransaccionEntidad,
        rutaFoto: String? = null,
        textoOcr: String? = null
    ) {
        viewModelScope.launch {
            val idTransaccion = repositorio.insertarTransaccion(transaccion).toInt()
            guardarComprobanteSiHay(idTransaccion, rutaFoto, textoOcr, transaccion.creadoEn)
        }
    }

    /** Si hay foto, guarda el comprobante ligado a la transacción recién creada. */
    private suspend fun guardarComprobanteSiHay(
        idTransaccion: Int,
        rutaFoto: String?,
        textoOcr: String?,
        creadoEn: Long
    ) {
        if (!rutaFoto.isNullOrBlank()) {
            repositorioComprobante.insertarComprobante(
                ComprobanteEntidad(
                    idComprobante = 0,
                    idTransaccion = idTransaccion,
                    urlImagen = rutaFoto,
                    textoOcr = textoOcr,
                    creadoEn = creadoEn
                )
            )
        }
    }

    /**
     * Guarda un INGRESO que destina parte a uno o varios planes de ahorro.
     *
     * @param transaccion el ingreso YA REDUCIDO (monto = ingreso - total ahorrado)
     * @param aportes lista de (idPlan, montoAhorrado) a registrar en progreso_ahorro
     *
     * Guarda la transacción y, por cada aporte, registra un progreso de ahorro.
     * Las validaciones (no pasar la meta, no ahorrar más que el ingreso) se hacen
     * ANTES en el Fragment; aquí solo persistimos lo ya validado.
     */
    fun guardarIngresoConAhorro(
        transaccion: TransaccionEntidad,
        aportes: List<Pair<Int, Double>>,
        rutaFoto: String? = null,
        textoOcr: String? = null
    ) {
        viewModelScope.launch {
            // 1. Guardar la transacción de ingreso (ya reducida) y su comprobante si hay
            val idTransaccion = repositorio.insertarTransaccion(transaccion).toInt()
            guardarComprobanteSiHay(idTransaccion, rutaFoto, textoOcr, transaccion.creadoEn)

            // 2. Por cada plan elegido, registrar el dinero aportado
            for ((idPlan, monto) in aportes) {
                if (monto > 0) {
                    repositorioProgresoAhorro.insertarProgresoAhorro(
                        ProgresoAhorroEntidad(
                            idAhorroProgreso = 0,           // Room asigna el id
                            idAhorro = idPlan,
                            montoAhorrado = monto,
                            registradoEn = transaccion.creadoEn
                        )
                    )
                }
            }
        }
    }

    fun eliminarTransaccion(transaccion: TransaccionEntidad) {
        viewModelScope.launch {
            repositorio.eliminarTransaccion(transaccion)
        }
    }

    /**
     * FÁBRICA del ViewModel.
     * Android no sabe crear un ViewModel que pide un repositorio en su constructor.
     * Esta clase le enseña: "cuando te pidan un TransaccionViewModel, usá este repositorio".
     */
    class Factory(
        private val repositorio: RepositorioTransaccion,
        private val repositorioUsuario: RepositorioUsuario,
        private val repositorioCategoria: RepositorioCategoria,
        private val repositorioPlanAhorro: RepositorioPlanAhorro,
        private val repositorioProgresoAhorro: RepositorioProgresoAhorro,
        private val repositorioComprobante: RepositorioComprobante
    ) : ViewModelProvider.Factory {

        // Android llama a este método cuando necesita crear el ViewModel
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Construimos el ViewModel pasándole los repositorios y lo devolvemos
            @Suppress("UNCHECKED_CAST")
            return TransaccionViewModel(
                repositorio, repositorioUsuario, repositorioCategoria,
                repositorioPlanAhorro, repositorioProgresoAhorro, repositorioComprobante
            ) as T
        }
    }
}