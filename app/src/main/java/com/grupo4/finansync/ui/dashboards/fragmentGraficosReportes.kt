package com.grupo4.finansync.ui.dashboards

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.tabs.TabLayout
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentGraficosReportesBinding
import com.grupo4.finansync.modelo.TransaccionEntidad
import com.grupo4.finansync.modelo.CategoriaEntidad
import java.util.Calendar
import java.util.Locale

class fragmentGraficosReportes : Fragment() {

    private var _binding: FragmentGraficosReportesBinding? = null
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

    private var todasLasTransacciones: List<TransaccionEntidad> = emptyList()
    private var todasLasCategorias: List<CategoriaEntidad> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGraficosReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVolverDashboard.setOnClickListener { parentFragmentManager.popBackStack() }

        configurarSpinnersFiltro()

        val database = BaseDatos.obtenerInstancia(requireContext())

        database.transaccionDao().obtenerTransaccionesPorUsuario(viewModel.idUsuarioReal)
            .asLiveData()
            .observe(viewLifecycleOwner) { lista: List<TransaccionEntidad>? ->
                if (lista != null) {
                    todasLasTransacciones = lista
                    procesarYFiltrarGraficos()
                }
            }

        database.categoriaDao().obtenerCategoriasPorUsuario(viewModel.idUsuarioReal)
            .asLiveData()
            .observe(viewLifecycleOwner) { categories: List<CategoriaEntidad>? ->
                if (categories != null) {
                    todasLasCategorias = categories
                    procesarYFiltrarGraficos()
                }
            }

        binding.tabLayoutGraficos.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { procesarYFiltrarGraficos() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun configurarSpinnersFiltro() {
        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val listaAnios = listOf(anioActual.toString(), (anioActual - 1).toString(), (anioActual - 2).toString())
        binding.spinnerAnio.adapter = ArrayAdapter(requireContext(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, listaAnios)

        val listaMeses = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        binding.spinnerMes.adapter = ArrayAdapter(requireContext(), com.google.android.material.R.layout.support_simple_spinner_dropdown_item, listaMeses)

        val mesActual = Calendar.getInstance().get(Calendar.MONTH)
        binding.spinnerMes.setSelection(mesActual)

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { procesarYFiltrarGraficos() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        binding.spinnerAnio.onItemSelectedListener = itemSelectedListener
        binding.spinnerMes.onItemSelectedListener = itemSelectedListener
    }

    private fun procesarYFiltrarGraficos() {
        if (todasLasTransacciones.isEmpty()) return

        val anioSeleccionado = binding.spinnerAnio.selectedItem.toString().toInt()
        val mesSeleccionado = binding.spinnerMes.selectedItemPosition

        val calStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, anioSeleccionado)
            set(Calendar.MONTH, mesSeleccionado)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestampInicio = calStart.timeInMillis

        val calEnd = Calendar.getInstance().apply {
            timeInMillis = timestampInicio
            add(Calendar.MONTH, 1)
        }
        val timestampFin = calEnd.timeInMillis

        val transaccionesDelMes = todasLasTransacciones.filter { it.creadoEn in timestampInicio until timestampFin }

        val ingresos = transaccionesDelMes.filter { it.tipo.lowercase(Locale.ROOT) == "ingreso" }.sumOf { it.monto }.toFloat()
        val gastos = transaccionesDelMes.filter { it.tipo.lowercase(Locale.ROOT) == "gasto" }.sumOf { it.monto }.toFloat()

        val colorTextoGrafico = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.parseColor("#64748B")
        }

        binding.pieChartReportes.visibility = View.GONE
        binding.barChartReportes.visibility = View.GONE
        binding.lineChartReportes.visibility = View.GONE

        when (binding.tabLayoutGraficos.selectedTabPosition) {
            0 -> {
                binding.txtTituloGrafico.text = "Gastos por Categoría - ${binding.spinnerMes.selectedItem} $anioSeleccionado"
                binding.pieChartReportes.visibility = View.VISIBLE

                val gastosAgrupados = transaccionesDelMes.filter { it.tipo.lowercase(Locale.ROOT) == "gasto" }
                    .groupBy { it.idCategoria }
                    .map { (idCat, lista) ->
                        val nombreReal = todasLasCategorias.find { it.idCategoria == idCat }?.nombreCategoria ?: "Categoría $idCat"
                        PieEntry(lista.sumOf { it.monto }.toFloat(), nombreReal)
                    }

                val dataSet = PieDataSet(gastosAgrupados.ifEmpty { listOf(PieEntry(0f, "Sin Datos")) }, "").apply {
                    colors = ColorTemplate.COLORFUL_COLORS.toList()
                    valueTextSize = 12f
                    valueTextColor = Color.WHITE
                }
                binding.pieChartReportes.apply {
                    data = PieData(dataSet)
                    description.isEnabled = false
                    setDrawEntryLabels(false)
                    legend.textColor = colorTextoGrafico
                    invalidate()
                }
            }
            1 -> {
                binding.txtTituloGrafico.text = "Ingresos vs Gastos - ${binding.spinnerMes.selectedItem} $anioSeleccionado"
                binding.barChartReportes.visibility = View.VISIBLE

                val dsIng = BarDataSet(listOf(BarEntry(1f, ingresos)), "Ingresos").apply {
                    color = Color.parseColor("#2E7D32")
                    valueTextColor = colorTextoGrafico
                }
                val dsGas = BarDataSet(listOf(BarEntry(2f, gastos)), "Gastos").apply {
                    color = Color.parseColor("#C62828")
                    valueTextColor = colorTextoGrafico
                }

                binding.barChartReportes.apply {
                    data = BarData(dsIng, dsGas)
                    description.isEnabled = false
                    xAxis.isEnabled = false
                    axisLeft.textColor = colorTextoGrafico
                    axisRight.textColor = colorTextoGrafico
                    legend.textColor = colorTextoGrafico
                    invalidate()
                }
            }
            2 -> {
                binding.txtTituloGrafico.text = "Evolución de Saldo por Transacción"
                binding.lineChartReportes.visibility = View.VISIBLE

                val transaccionesOrdenadas = transaccionesDelMes.sortedBy { it.creadoEn }
                val entries = mutableListOf<Entry>()

                var saldoAcumulado = 0f

                if (transaccionesOrdenadas.isEmpty()) {
                    entries.add(Entry(0f, 0f))
                } else {
                    transaccionesOrdenadas.forEachIndexed { indice, transaccion ->
                        if (transaccion.tipo.lowercase(Locale.ROOT) == "ingreso") {
                            saldoAcumulado += transaccion.monto.toFloat()
                        } else {
                            saldoAcumulado -= transaccion.monto.toFloat()
                        }
                        entries.add(Entry((indice + 1).toFloat(), saldoAcumulado))
                    }
                }

                val dataSet = LineDataSet(entries, "Saldo ($)").apply {
                    color = Color.parseColor("#2563EB")
                    lineWidth = 3f
                    valueTextSize = 10f
                    valueTextColor = colorTextoGrafico
                    setCircleColor(Color.parseColor("#2563EB"))
                    circleRadius = 5f
                    setDrawCircleHole(true)
                    circleHoleColor = Color.WHITE
                    circleHoleRadius = 2.5f
                    setDrawFilled(true)
                    fillColor = Color.parseColor("#2563EB")
                    fillAlpha = 25
                }

                binding.lineChartReportes.apply {
                    data = LineData(dataSet)
                    description.isEnabled = false

                    xAxis.isEnabled = true
                    xAxis.textColor = colorTextoGrafico
                    xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.granularity = 1f
                    xAxis.spaceMin = 0.5f
                    xAxis.spaceMax = 0.5f

                    axisLeft.textColor = colorTextoGrafico
                    axisLeft.setDrawGridLines(true)
                    axisLeft.resetAxisMinimum()
                    axisLeft.resetAxisMaximum()

                    axisRight.isEnabled = false
                    legend.textColor = colorTextoGrafico
                    animateX(600)
                    invalidate()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}