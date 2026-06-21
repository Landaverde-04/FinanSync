package com.grupo4.finansync.ui.presupuesto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPresupuesto
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentListaPresupuestosBinding
import com.grupo4.finansync.modelo.PresupuestoEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ListaPresupuestosFragment : Fragment() {

    private var _binding: FragmentListaPresupuestosBinding? = null
    private val binding get() = _binding!!

    private val vm: PresupuestoViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)
        val repoPres = RepositorioPresupuesto(bd.presupuestoDao())
        val repoCat = RepositorioCategoria(bd.categoriaDao())
        val repoTrans = RepositorioTransaccion(bd.transaccionDao())
        PresupuestoViewModel.Factory(repoPres, repoCat, repoTrans)
    }

    private lateinit var adapter: PresupuestoAdapter
    private lateinit var idUsuario: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaPresupuestosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario_prueba"

        configurarRecyclerView()
        configurarNavegacion()
        observarPresupuestos()

        vm.cargarPresupuestos(idUsuario)
    }

    private fun configurarRecyclerView() {
        adapter = PresupuestoAdapter { presupuesto ->
            confirmarEliminacion(presupuesto)
        }
        binding.rvPresupuestos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListaPresupuestosFragment.adapter
        }
    }

    private fun configurarNavegacion() {
        binding.btnVolverPresupuestos.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnAgregarPresupuesto.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.contenedorFragment, FormularioPresupuestoFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observarPresupuestos() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.presupuestosUI.collect { lista ->
                    adapter.actualizarLista(lista)
                    if (lista.isEmpty()) {
                        binding.vistaVaciaPresupuestos.visibility = View.VISIBLE
                        binding.rvPresupuestos.visibility = View.GONE
                    } else {
                        binding.vistaVaciaPresupuestos.visibility = View.GONE
                        binding.rvPresupuestos.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun confirmarEliminacion(presupuesto: PresupuestoEntidad) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar presupuesto")
            .setMessage("¿Estás seguro de que deseas eliminar este presupuesto?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                vm.eliminarPresupuesto(presupuesto)
                Toast.makeText(requireContext(), "Presupuesto eliminado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
