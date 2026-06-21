package com.grupo4.finansync.ui.dashboards

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
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
import com.github.mikephil.charting.formatter.ValueFormatter
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentDashboardBinding
import com.grupo4.finansync.ui.transaccion.TransaccionAdapter
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
            actualizarGraficas()
        }

        viewModel.totalGastosLiveData.observe(viewLifecycleOwner) { gastos ->
            val gas = (gastos ?: 0.0).toFloat()
            binding.txtGastosMensuales.text = String.format(Locale.US, "$%.2f", gas)
            actualizarGraficas()
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

    private fun actualizarGraficas() {
        val ingresos = viewModel.totalIngresosLiveData.value?.toFloat() ?: 0f
        val gastos = viewModel.totalGastosLiveData.value?.toFloat() ?: 0f

        val colorTextoTema = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.parseColor("#64748B")
        }

        val entradaIngreso = BarEntry(1f, ingresos)
        val entradaGasto = BarEntry(2f, gastos)

        val dsIngresos = BarDataSet(listOf(entradaIngreso), "Ingresos").apply {
            color = Color.parseColor("#16A34A")
            setDrawValues(false)
        }
        val dsGastos = BarDataSet(listOf(entradaGasto), "Gastos").apply {
            color = Color.parseColor("#DC2626")
            setDrawValues(false)
        }

        binding.barChartDashboard.apply {
            data = BarData(dsIngresos, dsGastos)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.isEnabled = false
            axisLeft.apply {
                axisMinimum = 0f
                textColor = colorTextoTema
                setDrawGridLines(true)
            }
            axisRight.isEnabled = false
            invalidate()
        }

        val pieEntries = mutableListOf<PieEntry>()
        val listaColores = mutableListOf<Int>()

        if (ingresos == 0f && gastos == 0f) {
            pieEntries.add(PieEntry(1f, "Sin Movimientos"))
            listaColores.add(Color.parseColor("#E2E8F0"))
        } else {
            if (ingresos > 0f) {
                pieEntries.add(PieEntry(ingresos, "Ingresos"))
                listaColores.add(Color.parseColor("#16A34A"))
            }
            if (gastos > 0f) {
                pieEntries.add(PieEntry(gastos, "Gastos"))
                listaColores.add(Color.parseColor("#DC2626"))
            }
        }

        val pieDataSet = PieDataSet(pieEntries, "").apply {
            colors = listaColores
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            setDrawValues(ingresos > 0f || gastos > 0f)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format(Locale.US, "$%.0f", value)
                }
            }
        }

        binding.pieChartDashboard.apply {
            data = PieData(pieDataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 45f
            transparentCircleRadius = 50f
            setDrawEntryLabels(false)

            setExtraOffsets(2f, 2f, 2f, 2f)

            legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                textSize = 9f
                textColor = colorTextoTema
                isWordWrapEnabled = true
            }
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}