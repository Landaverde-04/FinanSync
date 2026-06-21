package com.grupo4.finansync.ui.categoria

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
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentListaCategoriasBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ListaCategoriasFragment : Fragment() {

    private var _binding: FragmentListaCategoriasBinding? = null
    private val binding get() = _binding!!

    private val vm: CategoriaViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)
        val repoCat = RepositorioCategoria(bd.categoriaDao())
        val repoTrans = RepositorioTransaccion(bd.transaccionDao())
        CategoriaViewModel.Factory(repoCat, repoTrans)
    }

    private lateinit var adapter: CategoriaAdapter
    private lateinit var idUsuario: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaCategoriasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario_prueba"

        configurarRecyclerView()
        configurarNavegacion()
        observarCategorias()

        vm.cargarCategorias(idUsuario)
    }

    private fun configurarRecyclerView() {
        adapter = CategoriaAdapter { categoria ->
            confirmarEliminacion(categoria)
        }
        binding.rvCategorias.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListaCategoriasFragment.adapter
        }
    }

    private fun configurarNavegacion() {
        binding.btnVolverCategorias.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnAgregarCategoria.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.contenedorFragment, FormularioCategoriaFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun observarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.categorias.collect { lista ->
                    adapter.actualizarLista(lista)
                }
            }
        }
    }

    private fun confirmarEliminacion(categoria: CategoriaEntidad) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar categoría")
            .setMessage("¿Estás seguro de que deseas eliminar la categoría \"${categoria.nombreCategoria}\"?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                vm.eliminarCategoria(categoria) { exito, mensaje ->
                    if (exito) {
                        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
