package com.grupo4.finansync.ui.transaccion

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
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

class NuevaTransaccionFragment : Fragment() {

    private var _binding: FragmentNuevaTransaccionBinding? = null
    private val binding get() = _binding!!

    private fun obtenerIdUsuarioActual(): String? =
        SupabaseCliente.cliente.auth.currentUserOrNull()?.id

    private var tipoActual = "gasto"
    private var listaCategorias: List<CategoriaEntidad> = emptyList()
    private var listaPlanes: List<PlanAhorroEntidad> = emptyList()

    private val filasAporte = mutableListOf<ItemAporteAhorroBinding>()
    private val fechaSeleccionada: Calendar = Calendar.getInstance()

    private var latitudActual: Double? = null
    private var longitudActual: Double? = null

    private val clienteUbicacion by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    private val permisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) obtenerUbicacion()
    }

    private val viewModel: TransaccionViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext())
        TransaccionViewModel.Factory(
            RepositorioTransaccion(bd.transaccionDao()),
            RepositorioUsuario(bd.usuarioDao()),
            RepositorioCategoria(bd.categoriaDao()),
            RepositorioPlanAhorro(bd.planAhorroDao()),
            RepositorioProgresoAhorro(bd.progresoAhorroDao()),
            com.grupo4.finansync.data.repositorio.RepositorioComprobante(bd.comprobanteDao())
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

        tipoActual = savedInstanceState?.getString(ESTADO_TIPO)
            ?: arguments?.getString(ARG_TIPO_TRANSACCION)
                    ?: TIPO_GASTO

        if (tipoActual != TIPO_INGRESO && tipoActual != TIPO_GASTO) {
            tipoActual = TIPO_GASTO
        }

        pintarPestania(tipoActual)

        binding.tabIngreso.setOnClickListener {
            seleccionarTipo("ingreso")
        }

        binding.tabGasto.setOnClickListener {
            seleccionarTipo("gasto")
        }

        binding.txtFecha.setOnClickListener {
            abrirSelectorFecha()
        }

        binding.txtHora.setOnClickListener {
            abrirSelectorHora()
        }

        binding.inputMonto.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    filasAporte.forEach { fila ->
                        autocompletarFila(fila)
                    }
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }
            }
        )

        val idUsuario = obtenerIdUsuarioActual()

        if (idUsuario != null) {
            viewModel.cargarCategoriasPorTipo(
                idUsuario,
                tipoActual
            )

            viewModel.cargarPlanesActivos(idUsuario)
        }

        observarCategorias()
        observarPlanes()
        configurarAhorro()
        configurarCamara()
        configurarCalculadora()
        pedirUbicacion()

        binding.btnGuardar.setOnClickListener {
            guardarTransaccion()
        }
    }

    private fun configurarCalculadora() {
        binding.btnCalculadora.setOnClickListener {
            CalculadoraDialog().show(parentFragmentManager, "calculadora")
        }
        parentFragmentManager.setFragmentResultListener(
            CalculadoraDialog.RESULTADO_CALC,
            viewLifecycleOwner
        ) { _, bundle ->
            val valor = bundle.getDouble(CalculadoraDialog.KEY_RESULTADO, 0.0)
            if (valor > 0) binding.inputMonto.setText(formatearMonto(valor))
        }
    }

    private fun formatearMonto(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

    private fun pedirUbicacion() {
        binding.switchUbicacion.setOnCheckedChangeListener { _, marcado ->
            binding.txtUbicacion.alpha = if (marcado) 1f else 0.4f
        }

        val tienePermiso = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (tienePermiso) obtenerUbicacion()
        else permisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun ubicacionFinal(): Pair<Double?, Double?> =
        if (binding.switchUbicacion.isChecked) latitudActual to longitudActual
        else null to null

    private fun obtenerUbicacion() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            binding.txtUbicacion.text = "📍 Ubicación no disponible"
            return
        }

        clienteUbicacion.lastLocation
            .addOnSuccessListener { ubicacion ->
                if (ubicacion != null) {
                    latitudActual = ubicacion.latitude
                    longitudActual = ubicacion.longitude
                    mostrarNombreLugar(ubicacion.latitude, ubicacion.longitude)
                } else {
                    binding.txtUbicacion.text = "📍 Ubicación no disponible"
                }
            }
            .addOnFailureListener {
                binding.txtUbicacion.text = "📍 Ubicación no disponible"
            }
    }

    private fun mostrarNombreLugar(lat: Double, lon: Double) {
        try {
            val geocoder = android.location.Geocoder(requireContext(), Locale("es"))
            @Suppress("DEPRECATION")
            val direcciones = geocoder.getFromLocation(lat, lon, 1)
            val nombre = direcciones?.firstOrNull()?.let { dir ->
                listOfNotNull(dir.locality ?: dir.subAdminArea, dir.countryName)
                    .joinToString(", ")
            }
            binding.txtUbicacion.text = if (!nombre.isNullOrBlank())
                "📍 $nombre"
            else
                "📍 %.4f, %.4f".format(lat, lon)
        } catch (e: Exception) {
            binding.txtUbicacion.text = "📍 %.4f, %.4f".format(lat, lon)
        }
    }

    private var rutaFotoComprobante: String? = null
    private var textoOcrComprobante: String? = null

    private fun configurarCamara() {
        binding.btnCamara.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.contenedorFragment, CapturaComprobanteFragment())
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.setFragmentResultListener(
            CapturaComprobanteFragment.RESULTADO_OCR,
            viewLifecycleOwner
        ) { _, bundle ->
            val monto = bundle.getDouble(CapturaComprobanteFragment.KEY_MONTO, 0.0)
            val fecha = bundle.getString(CapturaComprobanteFragment.KEY_FECHA)
            rutaFotoComprobante = bundle.getString(CapturaComprobanteFragment.KEY_RUTA_FOTO)
            textoOcrComprobante = bundle.getString(CapturaComprobanteFragment.KEY_TEXTO_OCR)

            if (monto > 0) binding.inputMonto.setText(monto.toString())
            if (!fecha.isNullOrBlank()) aplicarFechaDetectada(fecha)

            Toast.makeText(requireContext(), "Comprobante adjuntado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun aplicarFechaDetectada(fechaTexto: String) {
        val partes = fechaTexto.split("/", "-")
        if (partes.size == 3) {
            val dia = partes[0].toIntOrNull() ?: return
            val mes = partes[1].toIntOrNull() ?: return
            var anio = partes[2].toIntOrNull() ?: return
            if (anio < 100) anio += 2000
            try {
                fechaSeleccionada.set(Calendar.YEAR, anio)
                fechaSeleccionada.set(Calendar.MONTH, mes - 1)
                fechaSeleccionada.set(Calendar.DAY_OF_MONTH, dia)
                actualizarTextosFechaHora()
            } catch (_: Exception) {}
        }
    }

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

    private fun seleccionarTipo(tipo: String) {
        if (tipoActual == tipo) return
        pintarPestania(tipo)
        obtenerIdUsuarioActual()?.let { viewModel.cargarCategoriasPorTipo(it, tipo) }
    }

    private fun pintarPestania(tipo: String) {
        tipoActual = tipo
        val ctx = requireContext()
        val verde = ContextCompat.getColor(ctx, R.color.verde_ingreso)
        val rojo = ContextCompat.getColor(ctx, R.color.rojo_gasto)
        val gris = ContextCompat.getColor(ctx, R.color.gris_texto)

        if (tipo == "ingreso") {
            binding.tabIngreso.setBackgroundResource(R.drawable.fondo_tab_activa)
            binding.tabIngreso.background.setTint(ContextCompat.getColor(ctx, R.color.tab_activa_ingreso))
            binding.tabGasto.background = null
            binding.lblIngreso.setTextColor(verde)
            binding.lblGasto.setTextColor(gris)

            val fondo = ContextCompat.getColor(ctx, R.color.cabecera_ingreso)
            val texto = ContextCompat.getColor(ctx, R.color.cabecera_ingreso_texto)
            binding.cabeceraMonto.background.setTint(fondo)
            binding.lblMonto.setTextColor(texto)
            binding.inputMonto.setTextColor(texto)
            binding.btnCalculadora.setColorFilter(texto)
            binding.iconoTipo.text = "➕"
            binding.iconoTipo.background.setTint(verde)
        } else {
            binding.tabGasto.setBackgroundResource(R.drawable.fondo_tab_activa)
            binding.tabGasto.background.setTint(ContextCompat.getColor(ctx, R.color.tab_activa_gasto))
            binding.tabIngreso.background = null
            binding.lblGasto.setTextColor(rojo)
            binding.lblIngreso.setTextColor(gris)

            val fondo = ContextCompat.getColor(ctx, R.color.cabecera_gasto)
            val texto = ContextCompat.getColor(ctx, R.color.cabecera_gasto_texto)
            binding.cabeceraMonto.background.setTint(fondo)
            binding.lblMonto.setTextColor(texto)
            binding.inputMonto.setTextColor(texto)
            binding.btnCalculadora.setColorFilter(texto)
            binding.iconoTipo.text = "➖"
            binding.iconoTipo.background.setTint(rojo)
        }
        actualizarVisibilidadAhorro()
    }

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
                agregarFilaAporte()
            }
        }
        binding.btnAgregarPlan.setOnClickListener { agregarFilaAporte() }
    }

    private fun actualizarVisibilidadAhorro() {
        val mostrar = tipoActual == "ingreso" && listaPlanes.isNotEmpty()
        binding.seccionAhorro.visibility = if (mostrar) View.VISIBLE else View.GONE
        if (!mostrar) {
            binding.checkAhorro.isChecked = false
            binding.detalleAhorro.visibility = View.GONE
            limpiarFilas()
        }
    }

    private fun agregarFilaAporte() {
        val fila = ItemAporteAhorroBinding.inflate(
            layoutInflater, binding.contenedorAportes, false
        )

        val nombres = listaPlanes.map {
            val regla = if (it.metodo == "porcentaje") "${it.porcentaje}%" else "$${it.montoFijo}"
            "${it.nombrePlan ?: "Plan sin nombre"} ($regla)"
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nombres)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fila.spinnerPlan.adapter = adapter

        fila.spinnerPlan.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    autocompletarFila(fila)
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }

        fila.btnQuitar.setOnClickListener {
            binding.contenedorAportes.removeView(fila.root)
            filasAporte.remove(fila)
            actualizarTotalAhorro()
        }

        fila.inputMonto.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { actualizarTotalAhorro() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.contenedorAportes.addView(fila.root)
        filasAporte.add(fila)
        autocompletarFila(fila)
    }

    private fun autocompletarFila(fila: ItemAporteAhorroBinding) {
        val pos = fila.spinnerPlan.selectedItemPosition
        if (pos < 0 || pos >= listaPlanes.size) return
        val plan = listaPlanes[pos]
        val ingreso = binding.inputMonto.text.toString().toDoubleOrNull() ?: 0.0

        val sugerido = if (plan.metodo == "porcentaje") {
            (ingreso * ((plan.porcentaje ?: 0.0) / 100.0))
        } else {
            plan.montoFijo ?: 0.0
        }

        if (sugerido > 0) fila.inputMonto.setText(String.format(Locale.US, "%.2f", sugerido))

        viewLifecycleOwner.lifecycleScope.launch {
            val faltante = viewModel.obtenerFaltante(plan.idAhorro, plan.montoMeta)
            fila.txtInfo.text = if (plan.montoMeta != null)
                "Falta $%.2f para la meta".format(faltante)
            else
                "Plan sin meta fija"
        }
    }

    private fun actualizarTotalAhorro() {
        val total = filasAporte.sumOf { it.inputMonto.text.toString().toDoubleOrNull() ?: 0.0 }
        binding.txtTotalAhorro.text = "Total a ahorrar: $%.2f".format(total)
    }

    private fun limpiarFilas() {
        binding.contenedorAportes.removeAllViews()
        filasAporte.clear()
        binding.txtTotalAhorro.text = ""
    }

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
        binding.txtFecha.text = "📅 ${formatoFecha.format(fechaSeleccionada.time)}"
        binding.txtHora.text = "🕐 ${formatoHora.format(fechaSeleccionada.time)}"
    }

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
            latitud = ubicacionFinal().first,
            longitud = ubicacionFinal().second,
            creadoEn = fechaSeleccionada.timeInMillis
        )
        viewModel.agregarTransaccion(transaccion, rutaFotoComprobante, textoOcrComprobante)
        Toast.makeText(requireContext(), "Transacción guardada", Toast.LENGTH_SHORT).show()
        finalizarGuardado()
    }

    private fun guardarConAhorro(idUsuario: String, idCategoria: Int, ingreso: Double, descripcion: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val aportes = mutableListOf<Pair<Int, Double>>()
            val planesUsados = mutableSetOf<Int>()
            var totalAhorro = 0.0

            for (fila in filasAporte) {
                val pos = fila.spinnerPlan.selectedItemPosition
                if (pos < 0 || pos >= listaPlanes.size) continue
                val plan = listaPlanes[pos]

                if (!planesUsados.add(plan.idAhorro)) {
                    Toast.makeText(requireContext(), "Repetiste un plan: revisa las filas", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val montoFila = fila.inputMonto.text.toString().toDoubleOrNull()
                if (montoFila == null || montoFila <= 0) {
                    fila.inputMonto.error = "Monto inválido"
                    return@launch
                }

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
                latitud = ubicacionFinal().first,
                longitud = ubicacionFinal().second,
                creadoEn = fechaSeleccionada.timeInMillis
            )

            viewModel.guardarIngresoConAhorro(transaccion, aportes, rutaFotoComprobante, textoOcrComprobante)
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
        rutaFotoComprobante = null
        textoOcrComprobante = null
        fechaSeleccionada.timeInMillis = System.currentTimeMillis()
        actualizarTextosFechaHora()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ESTADO_TIPO, tipoActual)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ESTADO_TIPO = "tipo_actual"

        const val ARG_TIPO_TRANSACCION = "tipo_transaccion"
        const val TIPO_INGRESO = "ingreso"
        const val TIPO_GASTO = "gasto"
    }
}