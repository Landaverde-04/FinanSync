package com.grupo4.finansync.ui.dashboards

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.databinding.FragmentCrearPlanBinding
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class crearPlanFragment : Fragment() {

    private var _binding: FragmentCrearPlanBinding? = null
    private val binding get() = _binding!!

    private lateinit var repositorio: RepositorioPlanAhorro
    private var idAhorroEditar: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCrearPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = BaseDatos.obtenerInstancia(requireContext())
        repositorio = RepositorioPlanAhorro(database.planAhorroDao())

        binding.btnVolverACrudar.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnCancelarPlan.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.tabLayoutMetodos.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> binding.tilInputDinamico.hint = "Porcentaje de cada ingreso (%)"
                    1 -> binding.tilInputDinamico.hint = "Monto fijo por ingreso ($)"
                }
                binding.etMontoDinamico.setText("")
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.switchActivo.visibility = View.GONE

        arguments?.let { args ->
            if (args.containsKey("idAhorroEditar")) {
                idAhorroEditar = args.getInt("idAhorroEditar")
                binding.switchActivo.visibility = View.VISIBLE

                lifecycleScope.launch {
                    val plan = repositorio.obtenerPlanAhorroPorId(idAhorroEditar!!)
                    plan?.let {
                        binding.etNombrePlan.setText(it.nombrePlan)
                        binding.etMontoMeta.setText(it.montoMeta?.toString() ?: "")
                        binding.switchActivo.isChecked = it.activo

                        if (it.metodo == "porcentaje") {
                            binding.tabLayoutMetodos.getTabAt(0)?.select()
                            binding.etMontoDinamico.setText(it.porcentaje?.toString() ?: "")
                        } else {
                            binding.tabLayoutMetodos.getTabAt(1)?.select()
                            binding.etMontoDinamico.setText(it.montoFijo?.toString() ?: "")
                        }
                        binding.btnGuardarPlan.text = "Actualizar Plan"
                    }
                }
            }
        }

        binding.btnGuardarPlan.setOnClickListener {
            val nombre = binding.etNombrePlan.text.toString().trim()
            val montoMetaVal = binding.etMontoMeta.text.toString().trim().toDoubleOrNull() ?: 0.0
            val inputDinamico = binding.etMontoDinamico.text.toString().trim().toDoubleOrNull() ?: 0.0
            val estaActivo = if (idAhorroEditar != null) binding.switchActivo.isChecked else true

            if (nombre.isEmpty()) {
                Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (montoMetaVal <= 0.0) {
                Toast.makeText(requireContext(), "Ingrese una meta válida mayor a 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var porcentajeVal = 0.0
            var montoFijoVal = 0.0

            val metodoSeleccionado = when (binding.tabLayoutMetodos.selectedTabPosition) {
                0 -> {
                    porcentajeVal = inputDinamico
                    "porcentaje"
                }
                else -> {
                    montoFijoVal = inputDinamico
                    "fijo"
                }
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario-prueba-001"

                    if (idAhorroEditar != null) {
                        val planEditado = PlanAhorroEntidad(
                            idAhorro = idAhorroEditar!!,
                            idUsuario = idUsuario,
                            metodo = metodoSeleccionado,
                            porcentaje = porcentajeVal,
                            montoFijo = montoFijoVal,
                            montoMeta = montoMetaVal,
                            nombrePlan = nombre,
                            activo = estaActivo
                        )
                        repositorio.actualizarPlanAhorro(planEditado)
                    } else {
                        val nuevoPlan = PlanAhorroEntidad(
                            idUsuario = idUsuario,
                            metodo = metodoSeleccionado,
                            porcentaje = porcentajeVal,
                            montoFijo = montoFijoVal,
                            montoMeta = montoMetaVal,
                            nombrePlan = nombre,
                            activo = estaActivo
                        )
                        repositorio.insertarPlanAhorro(nuevoPlan)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "¡Plan guardado exitosamente!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                } catch (e: Exception) {
                    Log.e("ERROR_GUARDAR_PLAN", "Causa del fallo: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error crítico al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

