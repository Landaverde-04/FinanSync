package com.grupo4.finansync.ui.historial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentHistorialBinding
import com.grupo4.finansync.ui.detalle.DetalleTransaccionFragment
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla 1 — Historial de movimientos.
 *
 * Muestra la lista de transacciones del usuario agrupadas por mes,
 * con chips de filtro rápido (todos / ingresos / gastos / este mes)
 * y barra de búsqueda opcional.
 *
 * Al pulsar una fila navega a DetalleTransaccionFragment pasando el ID.
 */
class HistorialFragment : Fragment() {

    private var _binding: FragmentHistorialBinding? = null
    private val binding get() = _binding!!

    private val vm: HistorialViewModel by viewModels()

    private lateinit var adapter: TransaccionDiffAdapter

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistorialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarChips()
        configurarBusqueda()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Configuración ──────────────────────────────────────────────────────
    private fun configurarRecyclerView() {
        adapter = TransaccionDiffAdapter(
            onItemClick = { transaccion ->
                // Navega al detalle pasando el ID de la transacción
                val detalle = DetalleTransaccionFragment.newInstance(transaccion.idTransaccion)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.contenedorFragment, detalle)
                    .addToBackStack(null)
                    .commit()
            }
        )
        binding.rvHistorial.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistorialFragment.adapter
            setHasFixedSize(false)
        }
    }

    private fun configurarChips() {
        binding.chipGroupFiltro.setOnCheckedStateChangeListener { _, checkedIds ->
            val filtro = when {
                checkedIds.contains(R.id.chipIngresos) -> "ingreso"
                checkedIds.contains(R.id.chipGastos)   -> "gasto"
                else                                   -> "todos"
            }
            vm.cambiarFiltroTipo(filtro)
        }
    }

    private fun configurarBusqueda() {
        binding.btnBuscar.setOnClickListener {
            val visible = binding.layoutBusqueda.visibility == View.VISIBLE
            binding.layoutBusqueda.visibility = if (visible) View.GONE else View.VISIBLE
            if (!visible) binding.etBusqueda.requestFocus()
        }

        binding.etBusqueda.addTextChangedListener { editable ->
            vm.cambiarBusqueda(editable?.toString().orEmpty())
        }
    }

    // ── Observadores ───────────────────────────────────────────────────────
    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Lista de transacciones filtradas → RecyclerView
                launch {
                    vm.transaccionesFiltradas.collect { lista ->
                        binding.layoutVacio.visibility =
                            if (lista.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvHistorial.visibility =
                            if (lista.isEmpty()) View.GONE else View.VISIBLE
                        adapter.submitTransacciones(lista)
                    }
                }

                // Nombres de categorías → adapter
                launch {
                    vm.nombresCategorias.collect { mapa ->
                        adapter.actualizarCategorias(mapa)
                    }
                }

                // Resumen del periodo → pie de pantalla
                launch {
                    vm.resumen.collect { r ->
                        binding.txtTituloPeriodo.text = r.etiquetaPeriodo
                        binding.txtResumenIngresos.text =
                            String.format(Locale.US, "+$%.2f", r.ingresos)
                        binding.txtResumenGastos.text =
                            String.format(Locale.US, "-$%.2f", r.gastos)
                        val signo = if (r.balance >= 0) "+" else ""
                        binding.txtResumenBalance.text =
                            String.format(Locale.US, "%s$%.2f", signo, r.balance)
                        binding.txtResumenBalance.setTextColor(
                            requireContext().getColor(
                                if (r.balance >= 0) R.color.verde_ingreso
                                else R.color.rojo_gasto
                            )
                        )
                    }
                }
            }
        }
    }
}