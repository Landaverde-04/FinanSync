package com.grupo4.finansync.ui.reportes

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.Context
import com.grupo4.finansync.ui.ajustes.AjustesFragment
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.grupo4.finansync.databinding.FragmentReportesBinding
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.grupo4.finansync.ui.auth.AuthPrefs

/**
 * Pantalla 3 — Reportes.
 *
 * Funcionalidades:
 *  1. Selector de periodo (mes/año) → Spinner
 *  2. Tarjeta resumen (ingresos / gastos / balance) — cargada del ViewModel
 *  3. [btnExportarPdf]  → genera PDF con iText 7 y lo comparte
 *  4. [btnLeerResumenVoz] → lee el resumen con TextToSpeech en español
 */
class ReportesFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentReportesBinding? = null
    private val binding get() = _binding!!

    private val vm: ReportesViewModel by viewModels()

    private var tts: TextToSpeech? = null
    private var ttsListo = false

    private val fmtFecha = SimpleDateFormat("dd/MM/yyyy", Locale("es"))
    private val fmtFechaHora = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es"))

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tts = TextToSpeech(requireContext(), this)
        configurarToolbar()
        configurarSpinner()
        configurarBotones()
        observarViewModel()
    }

    override fun onDestroyView() {
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroyView()
        _binding = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("es", "SV"))
            ttsListo = res != TextToSpeech.LANG_MISSING_DATA &&
                    res != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsListo) { tts?.setLanguage(Locale("es")); ttsListo = true }
        }
    }

    // ── Configuración ──────────────────────────────────────────────────────
    private fun configurarToolbar() {
        binding.toolbarReportes.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun configurarSpinner() {
        // Genera "Enero 2026", "Febrero 2026", … para los últimos 12 meses
        val periodos = vm.generarPeriodos()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            periodos
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriodo.adapter = adapter
        binding.spinnerPeriodo.setSelection(0) // mes actual primero

        binding.spinnerPeriodo.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) { vm.cambiarPeriodo(position) }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun configurarBotones() {
        binding.btnExportarPdf.setOnClickListener { generarPdf() }
        binding.btnLeerResumenVoz.setOnClickListener { leerResumenEnVoz() }
    }

    // ── Observadores ───────────────────────────────────────────────────────
    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.resumen.collect { r ->
                    binding.txtTituloResumenPeriodo.text = "Resumen — ${r.etiquetaPeriodo}"
                    binding.txtResumenIngresosReporte.text =
                        String.format(Locale.US, "+$%.2f", r.ingresos)
                    binding.txtResumenGastosReporte.text =
                        String.format(Locale.US, "-$%.2f", r.gastos)
                    val signo = if (r.balance >= 0) "+" else ""
                    binding.txtResumenBalanceReporte.text =
                        String.format(Locale.US, "%s$%.2f", signo, r.balance)
                }
            }
        }
    }

    // ── PDF (iText 7) ──────────────────────────────────────────────────────
    private fun generarPdf() {
        binding.btnExportarPdf.isEnabled = false
        binding.layoutProgresoPdf.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            // Esperar a que las categorías estén realmente cargadas (máx. unos segundos)
            val categorias = try {
                withTimeoutOrNull(5000L) {
                    vm.nombresCategorias.first { it.isNotEmpty() }
                } ?: vm.nombresCategorias.value  // si nunca llega, usa lo que haya
            } catch (e: Exception) {
                vm.nombresCategorias.value
            }

            val r = vm.resumen.value
            val transacciones = vm.transaccionesPeriodo.value
            val archivo = withContext(Dispatchers.IO) {
                try {
                    val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    val nombre = "FinanSync_${r.etiquetaPeriodo.replace(" ", "_")}.pdf"
                    val file = File(dir, nombre)

                    val writer = PdfWriter(file)
                    val pdf = PdfDocument(writer)
                    val doc = Document(pdf)

                    // ── Título ──
                    doc.add(
                        Paragraph("FinanSync — Reporte Financiero")
                            .setBold().setFontSize(18f)
                            .setTextAlignment(TextAlignment.CENTER)
                    )
                    doc.add(
                        Paragraph(r.etiquetaPeriodo)
                            .setFontSize(13f)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setFontColor(ColorConstants.GRAY)
                    )
                    doc.add(Paragraph(" "))

                    // ── Resumen ──
                    doc.add(Paragraph("Resumen").setBold().setFontSize(14f))
                    val tblResumen = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                        .useAllAvailableWidth()
                    tblResumen.addCell(Cell().add(Paragraph("Ingresos")))
                    tblResumen.addCell(
                        Cell().add(
                            Paragraph(String.format(Locale.US, "+$%.2f", r.ingresos))
                                .setFontColor(ColorConstants.GREEN)
                        )
                    )
                    tblResumen.addCell(Cell().add(Paragraph("Gastos")))
                    tblResumen.addCell(
                        Cell().add(
                            Paragraph(String.format(Locale.US, "-$%.2f", r.gastos))
                                .setFontColor(ColorConstants.RED)
                        )
                    )
                    tblResumen.addCell(Cell().add(Paragraph("Balance").setBold()))
                    tblResumen.addCell(
                        Cell().add(
                            Paragraph(String.format(Locale.US, "$%.2f", r.balance)).setBold()
                        )
                    )
                    doc.add(tblResumen)
                    doc.add(Paragraph(" "))

                    // ── Detalle de transacciones ──
                    doc.add(Paragraph("Movimientos").setBold().setFontSize(14f))
                    val tblTx = Table(
                        UnitValue.createPercentArray(floatArrayOf(2.5f, 1.5f, 1.5f, 1f))
                    ).useAllAvailableWidth()

                    // Encabezados
                    listOf("Descripción", "Categoría", "Fecha", "Monto").forEach { h ->
                        tblTx.addHeaderCell(
                            Cell().add(Paragraph(h).setBold())
                                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        )
                    }

                    // Filas
                    transacciones.forEach { t ->
                        val signo = if (t.tipo == "ingreso") "+" else "-"
                        tblTx.addCell(Cell().add(Paragraph(t.descripcion.ifEmpty { "—" })))
                        tblTx.addCell(
                            Cell().add(
                                Paragraph(categorias[t.idCategoria] ?: "—")
                            )
                        )
                        tblTx.addCell(
                            Cell().add(Paragraph(fmtFecha.format(Date(t.creadoEn))))
                        )
                        tblTx.addCell(
                            Cell().add(
                                Paragraph(
                                    String.format(Locale.US, "%s$%.2f", signo, t.monto)
                                )
                            )
                        )
                    }
                    doc.add(tblTx)

                    // ── Pie ──
                    doc.add(Paragraph(" "))
                    doc.add(
                        Paragraph("Generado por FinanSync el ${fmtFechaHora.format(Date())}")                            .setFontSize(9f).setFontColor(ColorConstants.GRAY)
                            .setTextAlignment(TextAlignment.RIGHT)
                    )

                    doc.close()
                    file
                } catch (e: Exception) {
                    null
                }
            }

            binding.btnExportarPdf.isEnabled = true
            binding.layoutProgresoPdf.visibility = View.GONE

            if (archivo != null) {
                // Guardar en Descargas usando MediaStore (funciona en Android 10+)
                try {
                    val nombre = archivo.name
                    val resolver = requireContext().contentResolver

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        // Android 10+ → MediaStore
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, nombre)
                            put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        )
                        uri?.let {
                            resolver.openOutputStream(it)?.use { out ->
                                archivo.inputStream().copyTo(out)
                            }
                            values.clear()
                            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(it, values, null, null)
                        }
                    } else {
                        // Android 9 y menor → copia directa
                        val descargas = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        )
                        archivo.copyTo(File(descargas, nombre), overwrite = true)
                    }

                    Toast.makeText(
                        requireContext(),
                        "✅ PDF guardado en Descargas: $nombre",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "No se pudo guardar en Descargas: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Mostrar "Abrir con…" usando el archivo privado (FileProvider)
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    archivo
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Abrir PDF con…"))

            } else {
                Toast.makeText(requireContext(), "Error al generar el PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── TextToSpeech ───────────────────────────────────────────────────────
    private fun leerResumenEnVoz() {
        // Verificar preferencia de accesibilidad del usuario
        val prefs = requireContext().getSharedPreferences(
            AuthPrefs.PREFS, Context.MODE_PRIVATE
        )
        val vozHabilitada = prefs.getBoolean(AjustesFragment.KEY_VOZ, true)

        if (!vozHabilitada) {
            Toast.makeText(
                requireContext(),
                "Lectura por voz desactivada en Ajustes",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!ttsListo) {
            Toast.makeText(requireContext(), "Voz no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val r = vm.resumen.value
        val texto = "Resumen de ${r.etiquetaPeriodo}. " +
                "Ingresos: ${String.format(Locale("es"), "%.2f", r.ingresos)} dólares. " +
                "Gastos: ${String.format(Locale("es"), "%.2f", r.gastos)} dólares. " +
                "Balance: ${String.format(Locale("es"), "%.2f", r.balance)} dólares."

        binding.layoutReproduciendo.visibility = View.VISIBLE
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "resumen_tts")

        tts?.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                requireActivity().runOnUiThread {
                    binding.layoutReproduciendo.visibility = View.GONE
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                requireActivity().runOnUiThread {
                    binding.layoutReproduciendo.visibility = View.GONE
                }
            }
        })
    }
}