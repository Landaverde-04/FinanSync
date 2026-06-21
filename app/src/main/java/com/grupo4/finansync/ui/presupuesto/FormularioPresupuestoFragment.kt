package com.grupo4.finansync.ui.presupuesto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioPresupuesto
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentFormularioPresupuestoBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.PresupuestoEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class FormularioPresupuestoFragment : Fragment() {

    private var _binding: FragmentFormularioPresupuestoBinding? = null
    private val binding get() = _binding!!

    private val vm: PresupuestoViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)
        val repoPres = RepositorioPresupuesto(bd.presupuestoDao())
        val repoCat = RepositorioCategoria(bd.categoriaDao())
        val repoTrans = RepositorioTransaccion(bd.transaccionDao())
        PresupuestoViewModel.Factory(repoPres, repoCat, repoTrans)
    }

    private lateinit var idUsuario: String
    private var categoriasList: List<CategoriaEntidad> = emptyList()
    private val periodosList = listOf("Semanal", "Mensual", "Anual")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormularioPresupuestoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario_prueba"

        configurarSpinners()
        configurarBotones()
        observarCategorias()

        vm.cargarCategoriasGastos(idUsuario)
    }

    private fun configurarSpinners() {
        val adapterPeriodo = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            periodosList
        )
        adapterPeriodo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPeriodoPresupuesto.adapter = adapterPeriodo
        binding.spinnerPeriodoPresupuesto.setSelection(1)
    }

    private fun configurarBotones() {
        binding.btnVolverFormPresupuesto.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCancelarPresupuesto.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnGuardarPresupuesto.setOnClickListener {
            guardarPresupuesto()
        }
    }

    private fun observarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categoriasDisponibles.collect { lista ->
                    categoriasList = lista
                    val nombres = lista.map { it.nombreCategoria }
                    val adapterCat = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        nombres
                    )
                    adapterCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerCategoriaPresupuesto.adapter = adapterCat
                }
            }
        }
    }

    private fun guardarPresupuesto() {
        if (categoriasList.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Crea al menos una categoría de tipo Gasto primero",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val limitStr = binding.etMontoLimitePresupuesto.text.toString().trim()
        val limitDouble = limitStr.toDoubleOrNull()

        if (limitStr.isEmpty() || limitDouble == null || limitDouble <= 0) {
            binding.tilLimoPresupuesto.error = "Ingresa un monto límite numérico mayor a 0"
            return
        } else {
            binding.tilLimoPresupuesto.error = null
        }

        val catIndex = binding.spinnerCategoriaPresupuesto.selectedItemPosition
        if (catIndex < 0 || catIndex >= categoriasList.size) return

        val categoriaSeleccionada = categoriasList[catIndex]
        val periodoSeleccionado = periodosList[binding.spinnerPeriodoPresupuesto.selectedItemPosition]

        val nuevoPresupuesto = PresupuestoEntidad(
            idPresupuesto = 0,
            idUsuario = idUsuario,
            idCategoria = categoriaSeleccionada.idCategoria,
            montoLimite = limitDouble,
            periodo = periodoSeleccionado.lowercase()
        )

        vm.insertarPresupuesto(nuevoPresupuesto)
        Toast.makeText(requireContext(), "Presupuesto guardado con éxito", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
