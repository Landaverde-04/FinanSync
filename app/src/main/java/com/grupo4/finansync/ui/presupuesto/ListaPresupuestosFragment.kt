package com.grupo4.finansync.ui.presupuesto

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
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
import java.util.Locale
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

class ListaPresupuestosFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentListaPresupuestosBinding? = null
    private val binding get() = _binding!!

    private val vm: PresupuestoViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)
        val repoPres = RepositorioPresupuesto(bd.presupuestoDao(), requireContext().applicationContext)
        val repoCat = RepositorioCategoria(bd.categoriaDao(), requireContext().applicationContext)
        val repoTrans = RepositorioTransaccion(bd.transaccionDao())
        PresupuestoViewModel.Factory(repoPres, repoCat, repoTrans)
    }

    private lateinit var adapter: PresupuestoAdapter
    private lateinit var idUsuario: String
    private var tts: TextToSpeech? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        tts = TextToSpeech(requireContext(), this)

        configurarRecyclerView()
        configurarNavegacion()
        observarPresupuestos()
        observarConectividad()
 
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

        binding.btnHablarPresupuestos.setOnClickListener {
            hablarPresupuestos()
        }
    }

    private fun observarPresupuestos() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.presupuestosUI.collect { lista ->
                    adapter.actualizarLista(lista)
                    actualizarGrafico(lista)
                    if (lista.isEmpty()) {
                        binding.vistaVaciaPresupuestos.visibility = View.VISIBLE
                        binding.scrollPresupuestos.visibility = View.GONE
                    } else {
                        binding.vistaVaciaPresupuestos.visibility = View.GONE
                        binding.scrollPresupuestos.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun actualizarGrafico(lista: List<PresupuestoUI>) {
        if (lista.isEmpty()) {
            binding.cardGraficoPresupuestos.visibility = View.GONE
            return
        }

        binding.cardGraficoPresupuestos.visibility = View.VISIBLE

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val colors = ArrayList<Int>()

        val primaryColor = obtenerColorDeTema(com.google.android.material.R.attr.colorPrimary)
        val errorColor = ContextCompat.getColor(requireContext(), R.color.rojo_gasto)

        for (i in lista.indices) {
            val ui = lista[i]
            entries.add(BarEntry(i.toFloat(), ui.porcentajeConsumo.toFloat()))
            labels.add(ui.categoriaNombre)

            if (ui.porcentajeConsumo > 100) {
                colors.add(errorColor)
            } else {
                colors.add(primaryColor)
            }
        }

        val dataSet = BarDataSet(entries, "Consumo (%)")
        dataSet.colors = colors
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = obtenerColorDeTema(com.google.android.material.R.attr.colorOnSurface)

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        binding.chartPresupuestos.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                setDrawGridLines(false)
                granularity = 1f
                textColor = obtenerColorDeTema(com.google.android.material.R.attr.colorOnSurface)
            }

            axisLeft.apply {
                axisMinimum = 0f
                textColor = obtenerColorDeTema(com.google.android.material.R.attr.colorOnSurface)
            }
            axisRight.isEnabled = false

            animateY(800)
            invalidate()
        }
    }

    private fun hablarPresupuestos() {
        val lista = vm.presupuestosUI.value
        if (lista.isEmpty()) {
            tts?.speak("No hay presupuestos registrados.", TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            val sb = StringBuilder()
            sb.append("Tienes ${lista.size} presupuestos. ")
            for (ui in lista) {
                sb.append("Presupuesto para ${ui.categoriaNombre}. ")
                sb.append("Límite de ${ui.entidad.montoLimite.toInt()} dólares. ")
                sb.append("Has gastado ${ui.montoGastado.toInt()} dólares. ")
                sb.append("Llevas un ${ui.porcentajeConsumo} por ciento de progreso. ")
                if (ui.porcentajeConsumo > 100) {
                    sb.append("Advertencia, has excedido este presupuesto. ")
                }
                sb.append(" ")
            }
            tts?.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
        }
    }

    private fun obtenerColorDeTema(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun observarConectividad() {
        val connectivityManager = requireContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
 
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    vm.sincronizarPendientes(idUsuario, requireContext().applicationContext)
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
    }
 
    override fun onDestroyView() {
        super.onDestroyView()
        networkCallback?.let { callback ->
            try {
                val connectivityManager = requireContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Falla silenciosa si se pierde el contexto
            }
        }
        _binding = null
    }
}
