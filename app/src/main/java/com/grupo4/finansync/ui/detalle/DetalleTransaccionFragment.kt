package com.grupo4.finansync.ui.detalle

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentDetalleTransaccionBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import android.content.Context
import java.util.Date
import java.util.Locale

/**
 * Pantalla 2 — Detalle de transacción.
 *
 * Recibe el ID de la transacción por Safe Args y muestra:
 *  - Tipo, monto, descripción, categoría y fecha
 *  - Botón "Ver en mapa" (visible solo si hay coordenadas GPS)
 *  - Comprobante cargado con Glide (si existe en COMPROBANTES)
 *  - Botón "Leer en voz" (TextToSpeech en español)
 *  - Botón "Eliminar" con confirmación
 */
class DetalleTransaccionFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentDetalleTransaccionBinding? = null
    private val binding get() = _binding!!

    private val idTransaccion: Int by lazy {
        arguments?.getInt("idTransaccion") ?: 0
    }

    private val vm: DetalleTransaccionViewModel by viewModels {
        DetalleTransaccionViewModel.Factory(requireActivity().application, idTransaccion)
    }

    // Safe Args: recibe idTransaccion (Int)
    //private val args: DetalleTransaccionFragmentArgs by navArgs()

    // Motor de voz
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    private val fmtFecha = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es"))

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetalleTransaccionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar TTS
        tts = TextToSpeech(requireContext(), this)

        configurarToolbar()
        observarViewModel()
        configurarAcciones()
    }

    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroyView()
        _binding = null
    }

    // ── TTS init ───────────────────────────────────────────────────────────
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts?.setLanguage(Locale("es", "SV"))
            ttsListo = resultado != TextToSpeech.LANG_MISSING_DATA &&
                    resultado != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsListo) {
                // Fallback a español genérico
                tts?.setLanguage(Locale("es"))
                ttsListo = true
            }
        }
    }

    // ── Configuración ──────────────────────────────────────────────────────
    private fun configurarToolbar() {
        binding.toolbarDetalle.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun configurarAcciones() {
        // Leer en voz alta
        binding.btnLeerVoz.setOnClickListener {

            if (lecturaVozActivada()) {

                leerEnVoz()

            } else {

                Toast.makeText(
                    requireContext(),
                    "Activa la lectura por voz en Ajustes",
                    Toast.LENGTH_SHORT
                ).show()

            }
        }

        // Eliminar transacción con confirmación
        binding.btnEliminarTransaccion.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Eliminar transacción")
                .setMessage("¿Estás seguro? Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar") { _, _ ->
                    vm.eliminarTransaccion()  // ← solo dispara, ya NO navega aquí
                }
                .show()
        }

        // Ver en mapa (Intent a Maps)
        binding.btnVerMapa.setOnClickListener {
            vm.abrirMapa(requireContext())
        }
    }

    // ── Observadores ───────────────────────────────────────────────────────
    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    vm.uiState.collect { state ->
                        state ?: return@collect

                        // Hero
                        val esIngreso = state.transaccion.tipo == "ingreso"
                        binding.iconoTipoDetalle.text = if (esIngreso) "🟢" else "🔴"
                        binding.txtTipoDetalle.text   = if (esIngreso) "Ingreso" else "Gasto"
                        binding.txtMontoDetalle.text  =
                            String.format(Locale.US, "$%.2f", state.transaccion.monto)

                        // Color del hero según tipo
                        val colorContainer = requireContext().getColor(
                            if (esIngreso) R.color.verde_ingreso_claro
                            else R.color.rojo_gasto_claro
                        )
                        binding.cardHero.setCardBackgroundColor(colorContainer)

                        // Datos
                        binding.txtDescripcionDetalle.text =
                            state.transaccion.descripcion.ifEmpty { "Sin descripción" }
                        binding.txtCategoriaDetalle.text = state.nombreCategoria
                        binding.txtFechaDetalle.text =
                            fmtFecha.format(Date(state.transaccion.creadoEn))

                        // Botón mapa: visible solo con coordenadas
                        val tieneMapa = state.transaccion.latitud != null &&
                                state.transaccion.longitud != null
                        binding.btnVerMapa.visibility =
                            if (tieneMapa) View.VISIBLE else View.GONE

                        // Comprobante con Glide
                        if (state.urlComprobante != null) {
                            binding.cardComprobante.visibility = View.VISIBLE
                            Glide.with(this@DetalleTransaccionFragment)
                                .load(state.urlComprobante)
                                .centerCrop()
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(binding.imgComprobante)
                        } else {
                            binding.cardComprobante.visibility = View.GONE
                        }
                    }
                }

                launch {
                    vm.error.collect { msg ->
                        msg?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                            vm.limpiarError()
                        }
                    }
                }

                launch {
                    vm.eliminacionCompletada.collect { completada ->
                        if (completada) {
                            parentFragmentManager.popBackStack()
                        }
                    }
                }

                launch {
                    vm.mensajeEliminacion.collect { mensaje ->
                        mensaje?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            vm.limpiarMensajeEliminacion()
                        }
                    }
                }
            }
        }
    }
    companion object {
        private const val ARG_ID = "idTransaccion"

        fun newInstance(id: Int): DetalleTransaccionFragment {
            return DetalleTransaccionFragment().apply {
                arguments = Bundle().apply { putInt(ARG_ID, id) }
            }
        }
    }

    // ── TextToSpeech ───────────────────────────────────────────────────────
    private fun leerEnVoz() {
        if (!ttsListo) {
            Toast.makeText(requireContext(), "Voz no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val state = vm.uiState.value ?: return
        val t = state.transaccion
        val tipo = if (t.tipo == "ingreso") "ingreso" else "gasto"
        val monto = String.format(Locale("es"), "%.2f", t.monto)
        val texto = "Transacción: $tipo de $monto dólares. " +
                "Categoría: ${state.nombreCategoria}. " +
                "Fecha: ${fmtFecha.format(Date(t.creadoEn))}. " +
                if (t.descripcion.isNotBlank()) "Descripción: ${t.descripcion}." else ""

        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "detalle_tts")
    }
    private fun lecturaVozActivada(): Boolean {

        val prefs = requireContext()
            .getSharedPreferences(
                "finansync_prefs",
                Context.MODE_PRIVATE
            )

        return prefs.getBoolean(
            "lectura_voz",
            true
        )
    }
}