package com.grupo4.finansync.ui.categoria

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentMenuM3Binding
import com.grupo4.finansync.ui.presupuesto.ListaPresupuestosFragment

class MenuM3Fragment : Fragment() {

    private var _binding: FragmentMenuM3Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuM3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVolverMenu.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCategorias.setOnClickListener {
            irA(ListaCategoriasFragment())
        }

        binding.btnPresupuestos.setOnClickListener {
            irA(ListaPresupuestosFragment())
        }
    }

    private fun irA(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
