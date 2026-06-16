package com.grupo4.finansync.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.FragmentMenuBinding
import com.grupo4.finansync.ui.transaccion.ListaTransaccionesFragment
import com.grupo4.finansync.ui.transaccion.NuevaTransaccionFragment
import com.grupo4.finansync.ui.ajustes.AjustesFragment

/**
 * Pantalla de inicio (menú) del módulo de Transacciones.
 * Desde aquí se navega a las distintas pantallas.
 *
 * Usamos FragmentManager para navegar de forma manual mientras M2
 * no tenga listo el Navigation Component. Cuando lo tenga, esto se
 * reemplaza por navegación con nav_graph.
 */
class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Agregar transacción -> abre el formulario
        binding.btnAgregar.setOnClickListener {
            irA(NuevaTransaccionFragment())
        }

        // Ver movimientos -> abre la lista de transacciones
        binding.btnMovimientos.setOnClickListener {
            irA(ListaTransaccionesFragment())
        }
        binding.btnReportes.setOnClickListener {
            Toast.makeText(requireContext(), "Reportes: próximamente", Toast.LENGTH_SHORT).show()
        }

        // Boton Ajustes -> abre pantalla de ajustes (tema oscuro)
        binding.btnAjustes.setOnClickListener {
            irA(AjustesFragment())
        }
    }

    /**
     * Reemplaza el menú por el fragment destino y lo agrega al "back stack"
     * para que la flecha atrás del teléfono regrese al menú.
     */
    private fun irA(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.contenedorFragment, fragment)
            .addToBackStack(null)   // permite volver atrás
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
