package com.grupo4.finansync.ui.transaccion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.data.repositorio.RepositorioUsuario
import com.grupo4.finansync.databinding.FragmentListaTransaccionesBinding
import kotlinx.coroutines.launch

/**
 * Pantalla que muestra la lista de transacciones guardadas (RecyclerView).
 * Se actualiza sola cuando se guarda una nueva, gracias a los Flow de Room.
 */
class ListaTransaccionesFragment : Fragment() {

    private var _binding: FragmentListaTransaccionesBinding? = null
    private val binding get() = _binding!!

    private val idUsuarioMock = "usuario-prueba-001"

    // El adaptador empieza vacío y lo llenamos cuando lleguen los datos
    private val adapter = TransaccionAdapter()

    private val viewModel: TransaccionViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext())
        val repoTransaccion = RepositorioTransaccion(bd.transaccionDao())
        val repoUsuario = RepositorioUsuario(bd.usuarioDao())
        val repoCategoria = RepositorioCategoria(bd.categoriaDao())
        TransaccionViewModel.Factory(repoTransaccion, repoUsuario, repoCategoria)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListaTransaccionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Configurar el RecyclerView: en lista vertical y con nuestro adapter
        binding.recyclerMovimientos.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMovimientos.adapter = adapter

        // 2. Pedir al ViewModel que cargue transacciones y nombres de categorías
        viewModel.cargarDatosUsuario(idUsuarioMock)

        // 3. Observar ambos flujos y refrescar la lista cuando cambien
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transacciones.collect { lista ->
                // Mostrar u ocultar el mensaje de "vacío"
                binding.txtVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                // Pasar la lista + el mapa de nombres al adapter
                adapter.actualizarLista(lista, viewModel.mapaCategorias.value)
            }
        }
        // Si cambian los nombres de categorías, también refrescamos
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mapaCategorias.collect { mapa ->
                adapter.actualizarLista(viewModel.transacciones.value, mapa)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
