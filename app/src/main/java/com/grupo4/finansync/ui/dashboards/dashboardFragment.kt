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
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.databinding.FragmentDashboardBinding
import com.grupo4.finansync.data.remote.SupabaseCliente
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// IMPORTANTE: Recuerda importar los fragmentos correspondientes a tus secciones reales
import com.grupo4.finansync.ui.dashboards.crearPlanFragment
import com.grupo4.finansync.ui.dashboards.fragmentGraficosReportes
import com.grupo4.finansync.ui.dashboards.listaPlanesFragment

class dashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Inicialización del ViewModel conectada a la Base de Datos Room
    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = BaseDatos.obtenerInstancia(requireContext())
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

        // ── 1. NAVEGACIÓN DE LOS BOTONES DE ACCIÓN ───────────────────────────

        // Clic para ir a Crear Plan
        binding.btnCrearPlan.setOnClickListener {
            irA(crearPlanFragment()) // Descomenta cuando esté creada la clase
        }

        // Clic para ir a Gráfico Reportes
        binding.btnGraficoReportes.setOnClickListener {
            irA(fragmentGraficosReportes()) // Descomenta cuando esté creada la clase
        }

        // Clic para ir a Lista Planes
        binding.btnListaPlanes.setOnClickListener {
            irA(listaPlanesFragment()) // Descomenta cuando esté creada la clase
        }

        // ── 2. CARGA DE MONTOS Y NOMBRE DE USUARIO ──────────────────────────
        binding.txtSaludo.text = "Hola 👋"
        binding.txtBalanceTotal.text = "$ 1,250.00"

        // Busca el usuario asíncronamente en Room usando el ID de Supabase
        obtenerNombreUsuarioLogueado()

        // ── 3. COMPONENTES VISUALES Y OBSERVADORES ──────────────────────────
        configurarGraficoPastel()
    }

    /**
     * Reemplaza de forma segura el fragmento actual en el contenedor oficial de la Activity.
     */
    private fun irA(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(com.grupo4.finansync.R.id.contenedorFragment, fragment)
            .addToBackStack(null) // Permite regresar al Dashboard presionando 'Atrás'
            .commit()
    }

    /**
     * Recupera el UUID desde Supabase y busca el nombre correspondiente en Room.
     */
    private fun obtenerNombreUsuarioLogueado() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uuidActual = SupabaseCliente.cliente.auth.currentUserOrNull()?.id

                if (uuidActual != null) {
                    val usuario = withContext(Dispatchers.IO) {
                        val db = BaseDatos.obtenerInstancia(requireContext())
                        db.usuarioDao().obtenerUsuarioPorId(uuidActual)
                    }

                    if (usuario != null && _binding != null) {
                        binding.txtSaludo.text = "Hola, ${usuario.nombreUsuario} 👋"
                    }
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.txtSaludo.text = "Hola, Usuario 👋"
                }
            }
        }
    }

    /**
     * Configura y genera el gráfico de pastel.
     */
    private fun configurarGraficoPastel() {
        val entradasGastos = listOf(
            PieEntry(400f, "Comida"),
            PieEntry(200f, "Transporte"),
            PieEntry(150f, "Shopping")
        )

        val dataSet = PieDataSet(entradasGastos, "").apply {
            colors = ColorTemplate.COLORFUL_COLORS.toList()
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }

        binding.pieChartGastos.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 60f
            centerText = "Gastos por\nCategoría"
            setCenterTextSize(16f)
            animateY(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}