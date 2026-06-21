package com.grupo4.finansync.ui.dashboards

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentDashboardBinding
import com.grupo4.finansync.ui.transaccion.TransaccionAdapter
import com.grupo4.finansync.ui.dashboards.fragmentGraficosReportes
import java.util.Locale

class dashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = BaseDatos.obtenerInstancia(requireContext())
                val repoPlanes = RepositorioPlanAhorro(database.planAhorroDao())
                val repoTransacciones = RepositorioTransaccion(database.transaccionDao())
                val repoCategorias = RepositorioCategoria(database.categoriaDao())
                return DashboardViewModel(repoPlanes, repoTransacciones, repoCategorias) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVerReportesDetallados.setOnClickListener {
            irA(fragmentGraficosReportes())
        }

        binding.fabCrearPlan.setOnClickListener {
            irA(listaPlanesFragment())
        }

        viewModel.balanceDisponibleReal.observe(viewLifecycleOwner) { balance ->
            binding.txtBalanceDisponible.text = String.format(Locale.US, "$%.2f", balance ?: 0.0)
        }

        viewModel.totalIngresosLiveData.observe(viewLifecycleOwner) { ingresos ->
            val ing = (ingresos ?: 0.0).toFloat()
            binding.txtIngresosMensuales.text = String.format(Locale.US, "$%.2f", ing)
            actualizarGraficoBarras()
        }

        viewModel.totalGastosLiveData.observe(viewLifecycleOwner) { gastos ->
            val gas = (gastos ?: 0.0).toFloat()
            binding.txtGastosMensuales.text = String.format(Locale.US, "$%.2f", gas)
            actualizarGraficoBarras()
        }

        viewModel.gastosPorCategoriaReal.observe(viewLifecycleOwner) { listaCategorias ->
            if (listaCategorias != null) {
                val entries = listaCategorias.map { PieEntry(it.monto, it.nombreCategoria) }
                val dataSet = PieDataSet(entries.ifEmpty { listOf(PieEntry(0f, "Sin Gastos")) }, "").apply {
                    colors = ColorTemplate.COLORFUL_COLORS.toList()
                    valueTextSize = 10f
                    valueTextColor = Color.WHITE
                }
                binding.pieChartDashboard.apply {
                    data = PieData(dataSet)
                    description.isEnabled = false
                    isDrawHoleEnabled = true
                    holeRadius = 50f
                    transparentCircleRadius = 55f
                    setDrawEntryLabels(false)

                    legend.apply {
                        isEnabled = true
                        verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                        horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                        orientation = Legend.LegendOrientation.HORIZONTAL
                        setDrawInside(false)
                        textSize = 10f
                        textColor = Color.parseColor("#64748B")
                        isWordWrapEnabled = true
                    }
                    invalidate()
                }
            }
        }

        viewModel.transaccionesRecientes.observe(viewLifecycleOwner) { transacciones ->
            if (transacciones != null) {
                val adapter = TransaccionAdapter(transacciones)
                binding.rvTransaccionesDashboard.adapter = adapter
            }
        }
    }

    private fun irA(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun actualizarGraficoBarras() {
        val ingresos = viewModel.totalIngresosLiveData.value?.toFloat() ?: 0f
        val gastos = viewModel.totalGastosLiveData.value?.toFloat() ?: 0f

        val entradaIngreso = BarEntry(1f, ingresos)
        val entradaGasto = BarEntry(2f, gastos)

        val dsIngresos = BarDataSet(listOf(entradaIngreso), "Ingresos").apply { color = Color.parseColor("#16A34A") }
        val dsGastos = BarDataSet(listOf(entradaGasto), "Gastos").apply { color = Color.parseColor("#DC2626") }

        binding.barChartDashboard.apply {
            data = BarData(dsIngresos, dsGastos)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}