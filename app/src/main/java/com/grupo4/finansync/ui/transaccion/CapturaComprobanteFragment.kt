package com.grupo4.finansync.ui.transaccion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.core.os.bundleOf
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.grupo4.finansync.databinding.FragmentCapturaComprobanteBinding
import java.io.File
import java.util.concurrent.Executors

/**
 * Pantalla de captura del comprobante con CameraX + OCR (ML Kit).
 *
 * Flujo: pide permiso de cámara → muestra preview → al capturar, guarda la foto,
 * la pasa al OCR, detecta monto y fecha, y devuelve esos datos al formulario.
 */
class CapturaComprobanteFragment : Fragment() {

    private var _binding: FragmentCapturaComprobanteBinding? = null
    private val binding get() = _binding!!

    // Objeto de CameraX que dispara la foto
    private var imageCapture: ImageCapture? = null

    // Hilo aparte para las operaciones de cámara (no bloquear la UI)
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        // Claves para devolver el resultado al formulario
        const val RESULTADO_OCR = "resultado_ocr"
        const val KEY_MONTO = "monto_detectado"
        const val KEY_FECHA = "fecha_detectada"
        const val KEY_RUTA_FOTO = "ruta_foto"
        const val KEY_TEXTO_OCR = "texto_ocr"
    }

    // Lanzador del permiso de cámara (forma moderna)
    private val permisoCamara = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) iniciarCamara()
        else {
            Toast.makeText(requireContext(), "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()  // volver atrás si no hay permiso
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCapturaComprobanteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCerrar.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnCapturar.setOnClickListener { tomarFoto() }

        // ¿Ya tenemos permiso? Si no, lo pedimos.
        if (tienePermisoCamara()) {
            iniciarCamara()
        } else {
            permisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun tienePermisoCamara(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    /** Arranca CameraX: muestra el preview y prepara la captura. */
    private fun iniciarCamara() {
        val futuroProveedor = ProcessCameraProvider.getInstance(requireContext())
        futuroProveedor.addListener({
            val proveedor = futuroProveedor.get()

            // 1. Preview: lo que se ve en pantalla
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewCamara.surfaceProvider)
            }

            // 2. ImageCapture: para tomar la foto
            imageCapture = ImageCapture.Builder().build()

            // 3. Usamos la cámara trasera
            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                proveedor.unbindAll()  // por si había algo enlazado
                proveedor.bindToLifecycle(viewLifecycleOwner, selector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Toma la foto y la guarda en un archivo temporal. */
    private fun tomarFoto() {
        val captura = imageCapture ?: return

        // Archivo temporal en el almacenamiento interno de la app
        val archivo = File(requireContext().cacheDir, "comprobante_${System.currentTimeMillis()}.jpg")
        val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()

        binding.progresoOcr.visibility = View.VISIBLE

        captura.takePicture(
            opciones,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(salida: ImageCapture.OutputFileResults) {
                    // La foto se guardó; ahora corremos el OCR
                    procesarOcr(archivo)
                }
                override fun onError(exc: ImageCaptureException) {
                    requireActivity().runOnUiThread {
                        binding.progresoOcr.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error al capturar: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    /** Pasa la foto al OCR de ML Kit, extrae monto y fecha, y devuelve el resultado. */
    private fun procesarOcr(archivo: File) {
        val imagen = InputImage.fromFilePath(requireContext(), android.net.Uri.fromFile(archivo))
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        reconocedor.process(imagen)
            .addOnSuccessListener { resultado ->
                val texto = resultado.text
                val monto = detectarMonto(texto)
                val fecha = detectarFecha(texto)

                // Devolver los datos al formulario
                setFragmentResult(
                    RESULTADO_OCR,
                    bundleOf(
                        KEY_MONTO to monto,
                        KEY_FECHA to fecha,
                        KEY_RUTA_FOTO to archivo.absolutePath,
                        KEY_TEXTO_OCR to texto
                    )
                )
                binding.progresoOcr.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    if (monto != null) "Monto detectado: $$monto" else "No se detectó un monto, escríbelo manual",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                binding.progresoOcr.visibility = View.GONE
                Toast.makeText(requireContext(), "No se pudo leer el texto", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Busca un monto en el texto. Estrategia:
     *  - Prioriza líneas que contengan "TOTAL".
     *  - Si no, toma el número con formato de dinero más grande.
     */
    private fun detectarMonto(texto: String): Double? {
        // Regex de números tipo 1234.56 o 1,234.56
        val regexDinero = Regex("""\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{2})|\d+[.,]\d{2}""")

        // 1. Buscar primero en líneas con "TOTAL"
        val lineaTotal = texto.lines().firstOrNull { it.contains("TOTAL", ignoreCase = true) }
        if (lineaTotal != null) {
            val match = regexDinero.find(lineaTotal)
            if (match != null) return normalizarNumero(match.value)
        }

        // 2. Si no, tomar el monto más grande del texto
        val montos = regexDinero.findAll(texto).mapNotNull { normalizarNumero(it.value) }.toList()
        return montos.maxOrNull()
    }

    /** Convierte "1,234.56" o "1.234,56" a Double. */
    private fun normalizarNumero(valor: String): Double? {
        // Quitamos separadores de miles y dejamos el punto decimal
        val limpio = valor.replace(",", "").let {
            // Si el original usaba coma como decimal (ej "12,50"), lo manejamos:
            if (valor.matches(Regex(""".*,\d{2}$"""))) valor.replace(".", "").replace(",", ".")
            else it
        }
        return limpio.toDoubleOrNull()
    }

    /** Busca una fecha tipo dd/mm/yyyy o dd-mm-yyyy en el texto. */
    private fun detectarFecha(texto: String): String? {
        val regexFecha = Regex("""\b(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})\b""")
        return regexFecha.find(texto)?.value
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor.shutdown()
        _binding = null
    }
}
