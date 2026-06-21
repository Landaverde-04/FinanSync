package com.grupo4.finansync.ui.transaccion

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.databinding.DialogCalculadoraBinding

/**
 * Calculadora integrada (diálogo). Hace operaciones básicas (+ − × ÷) y
 * devuelve el resultado al formulario vía setFragmentResult.
 *
 * Funciona "paso a paso" como la calculadora del teléfono:
 *  - acumulado: el resultado parcial guardado
 *  - operadorPendiente: la operación a aplicar cuando se ingrese el siguiente número
 *  - entradaActual: el número que se está escribiendo ahora
 */
class CalculadoraDialog : DialogFragment() {

    private var _binding: DialogCalculadoraBinding? = null
    private val binding get() = _binding!!

    private var acumulado: Double = 0.0
    private var operadorPendiente: Char? = null
    private var entradaActual: String = ""
    private var recienCalculado = false

    companion object {
        const val RESULTADO_CALC = "resultado_calculadora"
        const val KEY_RESULTADO = "valor"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCalculadoraBinding.inflate(layoutInflater)
        configurarBotones()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun configurarBotones() = with(binding) {
        // Dígitos
        val digitos = listOf(btn0 to "0", btn1 to "1", btn2 to "2", btn3 to "3", btn4 to "4",
            btn5 to "5", btn6 to "6", btn7 to "7", btn8 to "8", btn9 to "9")
        for ((boton, digito) in digitos) {
            boton.setOnClickListener { agregarDigito(digito) }
        }
        btnPunto.setOnClickListener { agregarPunto() }

        // Operadores
        btnSum.setOnClickListener { aplicarOperador('+') }
        btnRes.setOnClickListener { aplicarOperador('-') }
        btnMult.setOnClickListener { aplicarOperador('*') }
        btnDiv.setOnClickListener { aplicarOperador('/') }

        // Igual, limpiar, borrar
        btnIgual.setOnClickListener { calcularResultado() }
        btnC.setOnClickListener { limpiarTodo() }
        btnBorrar.setOnClickListener { borrarUltimo() }

        // Usar el resultado en el formulario
        btnUsar.setOnClickListener {
            calcularResultado()  // por si quedó una operación pendiente
            val valor = binding.display.text.toString().toDoubleOrNull() ?: 0.0
            setFragmentResult(RESULTADO_CALC, bundleOf(KEY_RESULTADO to valor))
            dismiss()
        }
    }

    private fun agregarDigito(d: String) {
        if (recienCalculado) { entradaActual = ""; recienCalculado = false }
        entradaActual += d
        binding.display.text = entradaActual
    }

    private fun agregarPunto() {
        if (recienCalculado) { entradaActual = ""; recienCalculado = false }
        if (!entradaActual.contains(".")) {
            entradaActual = if (entradaActual.isEmpty()) "0." else "$entradaActual."
            binding.display.text = entradaActual
        }
    }

    /** Guarda el operador y calcula lo anterior si ya había uno pendiente. */
    private fun aplicarOperador(op: Char) {
        if (entradaActual.isNotEmpty()) {
            val numero = entradaActual.toDouble()
            acumulado = if (operadorPendiente == null) numero else operar(acumulado, numero, operadorPendiente!!)
            binding.display.text = formatear(acumulado)
            entradaActual = ""
        }
        operadorPendiente = op
        recienCalculado = false
    }

    /** Calcula el resultado final con la operación pendiente. */
    private fun calcularResultado() {
        if (operadorPendiente != null && entradaActual.isNotEmpty()) {
            val numero = entradaActual.toDouble()
            acumulado = operar(acumulado, numero, operadorPendiente!!)
            binding.display.text = formatear(acumulado)
            entradaActual = formatear(acumulado)
            operadorPendiente = null
            recienCalculado = true
        }
    }

    /** Hace la operación entre dos números. */
    private fun operar(a: Double, b: Double, op: Char): Double = when (op) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b != 0.0) a / b else 0.0  // evitar dividir entre cero
        else -> b
    }

    /** Quita el ".0" cuando el resultado es entero. */
    private fun formatear(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

    private fun limpiarTodo() {
        acumulado = 0.0
        operadorPendiente = null
        entradaActual = ""
        recienCalculado = false
        binding.display.text = "0"
    }

    private fun borrarUltimo() {
        if (entradaActual.isNotEmpty()) {
            entradaActual = entradaActual.dropLast(1)
            binding.display.text = entradaActual.ifEmpty { "0" }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
