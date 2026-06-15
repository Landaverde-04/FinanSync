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
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.databinding.FragmentNuevaTransaccionBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.TransaccionEntidad
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pantalla para registrar una nueva transacción (ingreso o gasto).
 * Diseño tipo app de finanzas: pestañas arriba, monto grande, tarjeta con campos.
 *
 * Usa datos MOCK (usuario y categorías de prueba) para funcionar sin depender de M2/M3.
 */
class NuevaTransaccionFragment : Fragment() {

    private var _binding: FragmentNuevaTransaccionBinding? = null
    private val binding get() = _binding!!

    private val idUsuarioMock = "usuario-prueba-001"

    // Pestaña activa
    private var tipoActual = "gasto"

    // Lista de categorías que está mostrando el spinner (para saber cuál eligió el usuario)
    private var listaCategorias: List<CategoriaEntidad> = emptyList()

    // Fecha/hora elegidas. Arranca en "ahora" pero el usuario puede cambiarla.
    private val fechaSeleccionada: Calendar = Calendar.getInstance()

    private val viewModel: TransaccionViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext())
        val repoTransaccion = RepositorioTransaccion(bd.transaccionDao())
        val repoUsuario = RepositorioUsuario(bd.usuarioDao())
        val repoCategoria = RepositorioCategoria(bd.categoriaDao())
        TransaccionViewModel.Factory(repoTransaccion, repoUsuario, repoCategoria)
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

        // Mostrar fecha/hora actuales al inicio
        actualizarTextosFechaHora()

        // Pintar la pestaña inicial (gasto) sin recargar todavía
        pintarPestania("gasto")
        binding.tabIngreso.setOnClickListener { seleccionarTipo("ingreso") }
        binding.tabGasto.setOnClickListener { seleccionarTipo("gasto") }

        // Tocar la fecha abre el calendario; tocar la hora abre el reloj
        binding.txtFecha.setOnClickListener { abrirSelectorFecha() }
        binding.txtHora.setOnClickListener { abrirSelectorHora() }

        // TEMPORAL (mock): sembrar usuario y categorías de prueba
        viewModel.sembrarDatosDePrueba(idUsuarioMock)

        // Observar las categorías y llenar el spinner cuando lleguen
        observarCategorias()

        // Cargar las categorías del tipo inicial (gasto)
        viewModel.cargarCategoriasPorTipo(idUsuarioMock, tipoActual)

        // Guardar
        binding.btnGuardar.setOnClickListener { guardarTransaccion() }
    }

    /** Escucha la lista de categorías del ViewModel y la vuelca en el spinner. */
    private fun observarCategorias() {
        // repeatOnLifecycle no es necesario aquí para algo simple; usamos lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorias.collect { lista ->
                listaCategorias = lista
                // El spinner muestra texto, así que le pasamos solo los nombres
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

    /**
     * Cambia de pestaña: pinta los colores Y recarga el spinner con las
     * categorías de ese tipo. Se usa cuando el usuario toca una pestaña.
     */
    private fun seleccionarTipo(tipo: String) {
        // Si ya estábamos en ese tipo, no hacemos nada (evita recargar de gusto)
        if (tipoActual == tipo) return
        pintarPestania(tipo)
        viewModel.cargarCategoriasPorTipo(idUsuarioMock, tipo)
    }

    /** Solo cambia los colores/íconos según el tipo (sin tocar las categorías). */
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
    }

    /** Abre el calendario para elegir el día/mes/año. */
    private fun abrirSelectorFecha() {
        DatePickerDialog(
            requireContext(),
            { _, anio, mes, dia ->
                // Guardamos lo que eligió en nuestro Calendar
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

    /** Abre el reloj para elegir la hora/minuto. */
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
            false  // false = formato 12h con am/pm
        ).show()
    }

    /** Refresca los textos de fecha y hora con lo que haya en fechaSeleccionada. */
    private fun actualizarTextosFechaHora() {
        val formatoFecha = SimpleDateFormat("dd/MMM/yyyy", Locale("es"))
        val formatoHora = SimpleDateFormat("hh:mm a", Locale("es"))
        binding.txtFecha.text = formatoFecha.format(fechaSeleccionada.time)
        binding.txtHora.text = formatoHora.format(fechaSeleccionada.time)
    }

    private fun guardarTransaccion() {
        // 1. Monto
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

        // 2. Categoría elegida en el spinner
        val posicion = binding.spinnerCategoria.selectedItemPosition
        if (listaCategorias.isEmpty() || posicion < 0) {
            Toast.makeText(requireContext(), "Aún no hay categorías, espera un momento", Toast.LENGTH_SHORT).show()
            return
        }
        val categoriaElegida = listaCategorias[posicion]

        // 3. Descripción
        val descripcion = binding.inputDescripcion.text.toString().trim()

        // 4. Armar la entidad con la fecha ELEGIDA (no la actual)
        val transaccion = TransaccionEntidad(
            idTransaccion = 0,
            idUsuario = idUsuarioMock,
            idCategoria = categoriaElegida.idCategoria,
            monto = monto,
            tipo = tipoActual,
            descripcion = descripcion,
            latitud = null,
            longitud = null,
            creadoEn = fechaSeleccionada.timeInMillis  // <- fecha/hora elegida por el usuario
        )

        // 5. Guardar
        viewModel.agregarTransaccion(transaccion)

        // 6. Avisar y limpiar
        Toast.makeText(requireContext(), "Transacción guardada", Toast.LENGTH_SHORT).show()
        binding.inputMonto.text?.clear()
        binding.inputDescripcion.text?.clear()
        // Reiniciar la fecha a "ahora" para la próxima
        fechaSeleccionada.timeInMillis = System.currentTimeMillis()
        actualizarTextosFechaHora()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
