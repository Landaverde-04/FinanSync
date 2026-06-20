package com.grupo4.finansync.ui.dashboards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentListaPlanesBinding
import com.grupo4.finansync.ui.dashboards.crearPlanFragment

class listaPlanesFragment : Fragment() {

    private var _binding: FragmentListaPlanesBinding? = null
    private val binding get() = _binding!!

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