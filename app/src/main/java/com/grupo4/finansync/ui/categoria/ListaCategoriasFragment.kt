package com.grupo4.finansync.ui.categoria

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
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
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentListaCategoriasBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.ui.auth.SesionLocal
import kotlinx.coroutines.launch

class ListaCategoriasFragment : Fragment() {

    private var _binding: FragmentListaCategoriasBinding? = null
    private val binding get() = _binding!!

    private val vm: CategoriaViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)

        val repoCat = RepositorioCategoria(
            bd.categoriaDao(),
            requireContext().applicationContext
        )

        val repoTrans = RepositorioTransaccion(
            bd.transaccionDao()
        )

        CategoriaViewModel.Factory(
            repoCat,
            repoTrans
        )
    }

    private lateinit var adapterGastos: CategoriaAdapter
    private lateinit var adapterIngresos: CategoriaAdapter
    private lateinit var idUsuario: String

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaCategoriasBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val id = SesionLocal.obtenerIdUsuario(requireContext())

        if (id.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "No se pudo obtener el usuario",
                Toast.LENGTH_SHORT
            ).show()

            parentFragmentManager.popBackStack()
            return
        }

        idUsuario = id

        configurarRecyclerView()
        configurarNavegacion()
        observarCategorias()
        observarConectividad()

        vm.cargarCategorias(idUsuario)
    }

    private fun configurarRecyclerView() {
        adapterGastos = CategoriaAdapter { categoria ->
            confirmarEliminacion(categoria)
        }

        binding.rvCategoriasGastos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListaCategoriasFragment.adapterGastos
        }

        adapterIngresos = CategoriaAdapter { categoria ->
            confirmarEliminacion(categoria)
        }

        binding.rvCategoriasIngresos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListaCategoriasFragment.adapterIngresos
        }
    }

    private fun configurarNavegacion() {
        binding.btnVolverCategorias.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnAgregarCategoria.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.contenedorFragment,
                    FormularioCategoriaFragment()
                )
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categorias.collect { lista ->
                    val gastos = lista.filter {
                        it.tipo == "gasto"
                    }

                    val ingresos = lista.filter {
                        it.tipo == "ingreso"
                    }

                    adapterGastos.actualizarLista(gastos)
                    adapterIngresos.actualizarLista(ingresos)
                }
            }
        }
    }

    private fun confirmarEliminacion(
        categoria: CategoriaEntidad
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar categoría")
            .setMessage(
                "¿Estás seguro de que deseas eliminar la categoría \"${categoria.nombreCategoria}\"?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                vm.eliminarCategoria(categoria) { exito, mensaje ->
                    if (exito) {
                        Toast.makeText(
                            requireContext(),
                            mensaje,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No se puede eliminar")
                            .setMessage(mensaje)
                            .setPositiveButton("Entendido", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun observarConectividad() {
        val appContext = requireContext().applicationContext

        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    vm.sincronizarPendientes(
                        idUsuario,
                        appContext
                    )
                }
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(
                networkCallback!!
            )
        } catch (e: Exception) {
            // No detener la pantalla si no se puede registrar el callback.
        }
    }

    override fun onDestroyView() {
        networkCallback?.let { callback ->
            try {
                val appContext = requireContext().applicationContext

                val connectivityManager =
                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                            as ConnectivityManager

                connectivityManager.unregisterNetworkCallback(callback)

            } catch (e: Exception) {
                // Falla silenciosa si se pierde el contexto.
            }
        }

        networkCallback = null
        _binding = null

        super.onDestroyView()
    }
}