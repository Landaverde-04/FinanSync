package com.grupo4.finansync.ui.transaccion

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioProgresoAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.databinding.FragmentNuevaTransaccionBinding
import com.grupo4.finansync.databinding.ItemAporteAhorroBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pantalla para registrar una nueva transacción (ingreso o gasto).
 * En INGRESO, permite destinar parte del dinero a UNO O VARIOS planes de ahorro.
 */
class NuevaTransaccionFragment : Fragment() {

    private var _binding: FragmentNuevaTransaccionBinding? = null
    private val binding get() = _binding!!

    private fun obtenerIdUsuarioActual(): String? =
        SupabaseCliente.cliente.auth.currentUserOrNull()?.id

    private var tipoActual = "gasto"
    private var listaCategorias: List<CategoriaEntidad> = emptyList()
    private var listaPlanes: List<PlanAhorroEntidad> = emptyList()

    // Las filas de aporte que el usuario fue agregando (cada una = un plan + monto)
    private val filasAporte = mutableListOf<ItemAporteAhorroBinding>()

    private val fechaSeleccionada: Calendar = Calendar.getInstance()

    private val viewModel: TransaccionViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext())
        TransaccionViewModel.Factory(
            RepositorioTransaccion(bd.transaccionDao()),
            RepositorioUsuario(bd.usuarioDao()),
            RepositorioCategoria(bd.categoriaDao()),
            RepositorioPlanAhorro(bd.planAhorroDao()),
            RepositorioProgresoAhorro(bd.progresoAhorroDao())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNuevaTransaccionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        actualizarTextosFechaHora()
        pintarPestania("gasto")

        binding.tabIngreso.setOnClickListener { seleccionarTipo("ingreso") }
        binding.tabGasto.setOnClickListener { seleccionarTipo("gasto") }
        binding.txtFecha.setOnClickListener { abrirSelectorFecha() }
        binding.txtHora.setOnClickListener { abrirSelectorHora() }

        val idUsuario = obtenerIdUsuarioActual()
        if (idUsuario != null) {
            viewModel.cargarCategoriasPorTipo(idUsuario, tipoActual)
            viewModel.cargarPlanesActivos(idUsuario)
        }

        observarCategorias()
        observarPlanes()
        configurarAhorro()

        binding.btnGuardar.setOnClickListener { guardarTransaccion() }
    }

    // ── CATEGORÍAS ─────────────────────────────────────────────────────────────

    private fun observarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorias.collect { lista ->
                listaCategorias = lista
                val nombres = lista.map { it.nombreCategoria }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    nombres
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerCategoria.adapter = adapter
            }
        }
    }

    // ── PESTAÑAS ────────────────────────────────────────────────────────────────

    private fun seleccionarTipo(tipo: String) {
        if (tipoActual == tipo) return
        pintarPestania(tipo)
        obtenerIdUsuarioActual()?.let { viewModel.cargarCategoriasPorTipo(it, tipo) }
    }

    private fun pintarPestania(tipo: String) {
        tipoActual = tipo
        val verde = ContextCompat.getColor(requireContext(), R.color.verde_ingreso)
        val rojo = ContextCompat.getColor(requireContext(), R.color.rojo_gasto)
        val gris = ContextCompat.getColor(requireContext(), R.color.gris_texto)

        if (tipo == "ingreso") {
            binding.lblIngreso.setTextColor(verde)
            binding.indicadorIngreso.setBackgroundColor(verde)
            binding.lblGasto.setTextColor(gris)
            binding.indicadorGasto.setBackgroundColor(Color.TRANSPARENT)
            binding.iconoTipo.text = "➕"
            binding.iconoTipo.background.setTint(verde)
        } else {
            binding.lblGasto.setTextColor(rojo)
            binding.indicadorGasto.setBackgroundColor(rojo)
            binding.lblIngreso.setTextColor(gris)
            binding.indicadorIngreso.setBackgroundColor(Color.TRANSPARENT)
            binding.iconoTipo.text = "➖"
            binding.iconoTipo.background.setTint(rojo)
        }
        actualizarVisibilidadAhorro()
    }

    // ── AHORRO (varios planes) ───────────────────────────────────────────────────

    private fun observarPlanes() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.planesActivos.collect { lista ->
                listaPlanes = lista
                actualizarVisibilidadAhorro()
            }
        }
    }

    private fun configurarAhorro() {
        binding.checkAhorro.setOnCheckedChangeListener { _, marcado ->
            binding.detalleAhorro.visibility = if (marcado) View.VISIBLE else View.GONE
            if (marcado && filasAporte.isEmpty()) {
                agregarFilaAporte()   // al activar, ya aparece una fila lista
            }
        }
        binding.btnAgregarPlan.setOnClickListener { agregarFilaAporte() }
    }

    /** La sección de ahorro solo aparece en ingreso y si hay planes activos. */
    private fun actualizarVisibilidadAhorro() {
        val mostrar = tipoActual == "ingreso" && listaPlanes.isNotEmpty()
        binding.seccionAhorro.visibility = if (mostrar) View.VISIBLE else View.GONE
        if (!mostrar) {
            binding.checkAhorro.isChecked = false
            binding.detalleAhorro.visibility = View.GONE
            limpiarFilas()
        }
    }

    /** Crea una nueva fila de aporte (plan + monto) y la agrega al contenedor. */
    private fun agregarFilaAporte() {
        val fila = ItemAporteAhorroBinding.inflate(
            layoutInflater, binding.contenedorAportes, false
        )

        // Llenar el spinner de la fila con los planes activos
        val nombres = listaPlanes.map { it.nombrePlan ?: "Plan sin nombre" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fila.spinnerPlan.adapter = adapter

        // Al elegir plan, mostrar el sugerido y el faltante
        fila.spinnerPlan.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    autocompletarFila(fila)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }

        // Botón de quitar la fila
        fila.btnQuitar.setOnClickListener {
            binding.contenedorAportes.removeView(fila.root)
            filasAporte.remove(fila)
            actualizarTotalAhorro()
        }

        // Recalcular el total cada vez que cambie el monto de la fila
        fila.inputMonto.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { actualizarTotalAhorro() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.contenedorAportes.addView(fila.root)
        filasAporte.add(fila)
        autocompletarFila(fila)
    }

    /** Rellena el sugerido de una fila según el plan elegido y muestra el faltante. */
    private fun autocompletarFila(fila: ItemAporteAhorroBinding) {
        val pos = fila.spinnerPlan.selectedItemPosition
        if (pos < 0 || pos >= listaPlanes.size) return
        val plan = listaPlanes[pos]
        val ingreso = binding.inputMonto.text.toString().toDoubleOrNull() ?: 0.0

        val sugerido = viewModel.calcularSugerido(plan, ingreso)
        if (sugerido > 0) fila.inputMonto.setText(sugerido.toString())

        viewLifecycleOwner.lifecycleScope.launch {
            val faltante = viewModel.obtenerFaltante(plan.idAhorro, plan.montoMeta)
            fila.txtInfo.text = if (plan.montoMeta != null)
                "Falta $%.2f para la meta".format(faltante)
            else
                "Plan sin meta fija"
        }
    }

    /** Suma los montos de todas las filas y lo muestra. */
    private fun actualizarTotalAhorro() {
        val total = filasAporte.sumOf { it.inputMonto.text.toString().toDoubleOrNull() ?: 0.0 }
        binding.txtTotalAhorro.text = "Total a ahorrar: $%.2f".format(total)
    }

    private fun limpiarFilas() {
        binding.contenedorAportes.removeAllViews()
        filasAporte.clear()
        binding.txtTotalAhorro.text = ""
    }

    // ── FECHA / HORA ─────────────────────────────────────────────────────────────

    private fun abrirSelectorFecha() {
        DatePickerDialog(
            requireContext(),
            { _, anio, mes, dia ->
                fechaSeleccionada.set(Calendar.YEAR, anio)
                fechaSeleccionada.set(Calendar.MONTH, mes)
                fechaSeleccionada.set(Calendar.DAY_OF_MONTH, dia)
                actualizarTextosFechaHora()
            },
            fechaSeleccionada.get(Calendar.YEAR),
            fechaSeleccionada.get(Calendar.MONTH),
            fechaSeleccionada.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun abrirSelectorHora() {
        TimePickerDialog(
            requireContext(),
            { _, hora, minuto ->
                fechaSeleccionada.set(Calendar.HOUR_OF_DAY, hora)
                fechaSeleccionada.set(Calendar.MINUTE, minuto)
                actualizarTextosFechaHora()
            },
            fechaSeleccionada.get(Calendar.HOUR_OF_DAY),
            fechaSeleccionada.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun actualizarTextosFechaHora() {
        val formatoFecha = SimpleDateFormat("dd/MMM/yyyy", Locale("es"))
        val formatoHora = SimpleDateFormat("hh:mm a", Locale("es"))
        binding.txtFecha.text = formatoFecha.format(fechaSeleccionada.time)
        binding.txtHora.text = formatoHora.format(fechaSeleccionada.time)
    }

    // ── GUARDAR ──────────────────────────────────────────────────────────────────

    private fun guardarTransaccion() {
        val montoTexto = binding.inputMonto.text.toString().trim()
        if (montoTexto.isEmpty()) {
            binding.inputMonto.error = "Ingresa un monto"
            return
        }
        val monto = montoTexto.toDoubleOrNull()
        if (monto == null || monto <= 0) {
            binding.inputMonto.error = "El monto debe ser mayor a 0"
            return
        }

        val posCat = binding.spinnerCategoria.selectedItemPosition
        if (listaCategorias.isEmpty() || posCat < 0) {
            Toast.makeText(requireContext(), "Aún no hay categorías, espera un momento", Toast.LENGTH_SHORT).show()
            return
        }
        val categoriaElegida = listaCategorias[posCat]

        val idUsuario = obtenerIdUsuarioActual()
        if (idUsuario == null) {
            Toast.makeText(requireContext(), "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val descripcion = binding.inputDescripcion.text.toString().trim()

        val llevaAhorro = tipoActual == "ingreso" &&
            binding.seccionAhorro.visibility == View.VISIBLE &&
            binding.checkAhorro.isChecked &&
            filasAporte.isNotEmpty()

        if (llevaAhorro) {
            guardarConAhorro(idUsuario, categoriaElegida.idCategoria, monto, descripcion)
        } else {
            guardarSimple(idUsuario, categoriaElegida.idCategoria, monto, descripcion)
        }
    }

    private fun guardarSimple(idUsuario: String, idCategoria: Int, monto: Double, descripcion: String) {
        val transaccion = TransaccionEntidad(
            idTransaccion = 0,
            idUsuario = idUsuario,
            idCategoria = idCategoria,
            monto = monto,
            tipo = tipoActual,
            descripcion = descripcion,
            latitud = null,
            longitud = null,
            creadoEn = fechaSeleccionada.timeInMillis
        )
        viewModel.agregarTransaccion(transaccion)
        Toast.makeText(requireContext(), "Transacción guardada", Toast.LENGTH_SHORT).show()
        finalizarGuardado()
    }

    /**
     * Guardado de ingreso con aportes a VARIOS planes.
     * Valida cada fila, la suma total contra el ingreso, y ajusta al faltante de cada meta.
     */
    private fun guardarConAhorro(idUsuario: String, idCategoria: Int, ingreso: Double, descripcion: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val aportes = mutableListOf<Pair<Int, Double>>()
            val planesUsados = mutableSetOf<Int>()
            var totalAhorro = 0.0

            for (fila in filasAporte) {
                val pos = fila.spinnerPlan.selectedItemPosition
                if (pos < 0 || pos >= listaPlanes.size) continue
                val plan = listaPlanes[pos]

                // No permitir el mismo plan dos veces
                if (!planesUsados.add(plan.idAhorro)) {
                    Toast.makeText(requireContext(), "Repetiste un plan: revisa las filas", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val montoFila = fila.inputMonto.text.toString().toDoubleOrNull()
                if (montoFila == null || montoFila <= 0) {
                    fila.inputMonto.error = "Monto inválido"
                    return@launch
                }

                // Ajustar al faltante de la meta del plan
                val faltante = viewModel.obtenerFaltante(plan.idAhorro, plan.montoMeta)
                if (plan.montoMeta != null && faltante <= 0.0) {
                    Toast.makeText(requireContext(), "El plan '${plan.nombrePlan}' ya alcanzó su meta", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val ahorroFila = if (plan.montoMeta != null && montoFila > faltante) faltante else montoFila

                aportes.add(plan.idAhorro to ahorroFila)
                totalAhorro += ahorroFila
            }

            if (aportes.isEmpty()) {
                Toast.makeText(requireContext(), "Agrega al menos un plan con monto", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // VALIDACIÓN CLAVE: la suma de todos los ahorros no puede superar el ingreso
            if (totalAhorro > ingreso) {
                Toast.makeText(
                    requireContext(),
                    "El total a ahorrar ($%.2f) supera el ingreso ($%.2f)".format(totalAhorro, ingreso),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val ingresoRestante = ingreso - totalAhorro
            val transaccion = TransaccionEntidad(
                idTransaccion = 0,
                idUsuario = idUsuario,
                idCategoria = idCategoria,
                monto = ingresoRestante,
                tipo = "ingreso",
                descripcion = descripcion,
                latitud = null,
                longitud = null,
                creadoEn = fechaSeleccionada.timeInMillis
            )

            viewModel.guardarIngresoConAhorro(transaccion, aportes)
            Toast.makeText(
                requireContext(),
                "Guardado: ingreso $%.2f, ahorro total $%.2f".format(ingresoRestante, totalAhorro),
                Toast.LENGTH_LONG
            ).show()
            finalizarGuardado()
        }
    }

    private fun finalizarGuardado() {
        binding.inputMonto.text?.clear()
        binding.inputDescripcion.text?.clear()
        binding.checkAhorro.isChecked = false
        limpiarFilas()
        fechaSeleccionada.timeInMillis = System.currentTimeMillis()
        actualizarTextosFechaHora()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
