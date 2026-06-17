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
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.grupo4.finansync.bd.BaseDatos // Importación de su base de datos real
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.databinding.FragmentDashboardBinding

class dashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Inicialización del ViewModel usando la fábrica conectada a su BaseDatos real
    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Usamos el método exacto de sus compañeros: obtenerInstancia
                val database = BaseDatos.obtenerInstancia(requireContext())
                // Obtenemos el DAO correspondiente de la base de datos
                val repo = RepositorioPlanAhorro(database.planAhorroDao())
                return DashboardViewModel(repo) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Datos estáticos del Dashboard (Temporales mientras se conectan los montos globales)
        binding.txtSaludo.text = "Hola, Kevin 👋"
        binding.txtBalanceTotal.text = "$ 1,250.00"
        binding.txtIngresosMonto.text = "$ 2,000.00"
        binding.txtGastosMonto.text = "$ 750.00"

        // 2. ESCUCHA DE DATOS EN TIEMPO REAL: Observamos la manguera de datos de los planes de ahorro
        viewModel.listaPlanesActivos.observe(viewLifecycleOwner) { planes ->
            if (!planes.isNullOrEmpty()) {
                val planVigente = planes.first()
                // Cuando agregues más campos a la UI, aquí capturas los datos del plan real de Room:
                // Ejemplo: binding.txtProgresoAhorro.text = "Estrategia: ${planVigente.metodo}"
            }
        }

        // 3. Gráfica de pastel: Simulamos las entradas de gastos por categoría por ahora
        configurarGraficoPastel()
    }

    private fun configurarGraficoPastel() {
        // Datos de prueba mapeados a las categorías del M3
        val entradasGastos = listOf(
            PieEntry(400f, "Comida"),
            PieEntry(200f, "Transporte"),
            PieEntry(150f, "Shopping")
        )

        val dataSet = PieDataSet(entradasGastos, "").apply {
            colors = ColorTemplate.COLORFUL_COLORS.toList() // Paleta de colores vivos
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        binding.pieChartGastos.apply {
            data = PieData(dataSet)
            description.isEnabled = false // Oculta texto de descripción por defecto
            isDrawHoleEnabled = true      // Lo convierte en estilo dona moderno
            holeRadius = 60f
            centerText = "Gastos por\nCategoría"
            setCenterTextSize(16f)
            animateY(1000)                // Animación fluida de 1 segundo
            invalidate()                  // Redibuja los cambios en la pantalla
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Evita fugas de memoria al destruir la vista del fragmento
    }
}