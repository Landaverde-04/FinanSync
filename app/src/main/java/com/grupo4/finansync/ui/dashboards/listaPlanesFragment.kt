package com.grupo4.finansync.ui.dashboards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.databinding.FragmentListaPlanesBinding
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import com.grupo4.finansync.ui.dashboards.crearPlanFragment
import io.github.jan.supabase.gotrue.auth

class listaPlanesFragment : Fragment() {

    private var _binding: FragmentListaPlanesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlanesViewModel by viewModels {
        val database = BaseDatos.obtenerInstancia(requireContext())
        val repo = RepositorioPlanAhorro(database.planAhorroDao())
        val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario-prueba-001"
        PlanesViewModel.Factory(repo, idUsuario)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaPlanesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVolverDeLista.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabAgregarPlan.setOnClickListener {
            irA(crearPlanFragment())
        }

        viewModel.planesActivosLiveData.observe(viewLifecycleOwner) { listaPlanes ->
            if (listaPlanes != null) {
                configurarListaAlcancias(listaPlanes)
            }
        }
    }

    private fun configurarListaAlcancias(planes: List<PlanAhorroEntidad>) {
        binding.rvPlanesAhorro.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alcancia_progreso, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val plan = planes[position]

                val txtNombre = holder.itemView.findViewById<TextView>(R.id.txtNombreAlcancia)
                val txtRegla = holder.itemView.findViewById<TextView>(R.id.txtReglaAhorro)
                val txtProgresoTexto = holder.itemView.findViewById<TextView>(R.id.txtProgresoTexto)
                val progresoBarra = holder.itemView.findViewById<ProgressBar>(R.id.progressAlcancia)
                val txtEstado = holder.itemView.findViewById<TextView>(R.id.txtEstadoAlcancia)

                txtNombre.text = plan.nombrePlan ?: "Plan sin nombre"

                val reglaTexto = when (plan.metodo) {
                    "porcentaje" -> "Descuenta el ${plan.porcentaje}% de cada ingreso"
                    else -> "Descuenta $${plan.montoFijo} de cada ingreso"
                }
                txtRegla.text = reglaTexto

                if (plan.activo) {
                    txtEstado.text = "Activo"
                    txtEstado.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                } else {
                    txtEstado.text = "Inactivo"
                    txtEstado.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                }

                val dineroActual = 0.0
                val metaTotal = plan.montoMeta ?: 1.0

                txtProgresoTexto.text = "$$dineroActual / $$metaTotal"

                val porcentajeCompletado = ((dineroActual / metaTotal) * 100).toInt()
                progresoBarra.progress = porcentajeCompletado

                holder.itemView.setOnClickListener {
                    mostrarOpcionesDialogo(plan)
                }
            }

            override fun getItemCount(): Int = planes.size
        }
    }

    private fun irA(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun mostrarOpcionesDialogo(plan: PlanAhorroEntidad) {
        val opciones = arrayOf(
            "Editar Plan",
            "Eliminar Definitivamente"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Opciones para: ${plan.nombrePlan ?: "Plan sin nombre"}")
            .setItems(opciones) { _, posicion ->
                when (posicion) {
                    0 -> abrirPantallaEditar(plan)
                    1 -> confirmarEliminacion(plan)
                }
            }
            .show()
    }

    private fun confirmarEliminacion(plan: PlanAhorroEntidad) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("¿Eliminar ahorro?")
            .setMessage("Se perderán los registros de esta alcancía.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.eliminarPlan(plan)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirPantallaEditar(plan: PlanAhorroEntidad) {
        val fragmentEditar = crearPlanFragment().apply {
            arguments = Bundle().apply {
                putInt("idAhorroEditar", plan.idAhorro)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragment, fragmentEditar)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}