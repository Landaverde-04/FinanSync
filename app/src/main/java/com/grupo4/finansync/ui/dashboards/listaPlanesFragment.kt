package com.grupo4.finansync.ui.dashboards

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.databinding.FragmentListaPlanesBinding
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import com.grupo4.finansync.ui.auth.SesionLocal
class listaPlanesFragment : Fragment() {

    private var _binding: FragmentListaPlanesBinding? = null
    private val binding get() = _binding!!

    private var textoBusqueda: String = ""
    private var filtroEstado: String = "Todos"
    private val REQUEST_CODE_VOZ = 101
    private var textToSpeech: TextToSpeech? = null

    private val viewModel: PlanesViewModel by viewModels {
        val database = BaseDatos.obtenerInstancia(requireContext())
        val repo = RepositorioPlanAhorro(database.planAhorroDao())

        val idUsuario = SesionLocal.obtenerIdUsuario(requireContext())
            ?: ""

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

        val database = BaseDatos.obtenerInstancia(requireContext())

        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale("es", "ES"))
            }
        }

        binding.btnVolverDeLista.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.fabAgregarPlan.setOnClickListener {
            irA(crearPlanFragment())
        }

        binding.etBuscarPlan.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textoBusqueda = s?.toString() ?: ""
                aplicarFiltrosYActualizar()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFiltros.setOnCheckedChangeListener { _, checkedId ->
            filtroEstado = when (checkedId) {
                R.id.chipActivos -> "Activos"
                R.id.chipInactivos -> "Inactivos"
                R.id.chipCompletadas -> "Completadas"
                else -> "Todos"
            }
            aplicarFiltrosYActualizar()
        }

        binding.tilBuscadorPlanes.setEndIconOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el nombre de la alcancía...")
            }
            try {
                startActivityForResult(intent, REQUEST_CODE_VOZ)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "El reconocimiento de voz no está soportado.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.planesActivosLiveData.observe(viewLifecycleOwner) { listaPlanes ->
            if (listaPlanes != null) {
                viewModel.cargarProgresos(listaPlanes, database.progresoAhorroDao())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.mapaProgreso.collectLatest { _ ->
                aplicarFiltrosYActualizar()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val database = BaseDatos.obtenerInstancia(requireContext())
        viewModel.planesActivosLiveData.value?.let {
            viewModel.cargarProgresos(it, database.progresoAhorroDao())
        }
    }

    private fun hablar(texto: String) {
        textToSpeech?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun aplicarFiltrosYActualizar() {
        val planesOriginales = viewModel.planesActivosLiveData.value ?: emptyList()
        val progresos = viewModel.mapaProgreso.value

        var listaFiltrada = if (textoBusqueda.isEmpty()) {
            planesOriginales
        } else {
            planesOriginales.filter {
                (it.nombrePlan ?: "").contains(textoBusqueda, ignoreCase = true)
            }
        }

        listaFiltrada = listaFiltrada.filter { plan ->
            val dineroActual = progresos[plan.idAhorro] ?: 0.0
            val metaTotal = plan.montoMeta ?: 1.0
            val metaCompletada = dineroActual >= metaTotal && (plan.montoMeta ?: 0.0) > 0.0

            when (filtroEstado) {
                "Activos" -> plan.activo && !metaCompletada
                "Inactivos" -> !plan.activo && !metaCompletada
                "Completadas" -> metaCompletada
                else -> true
            }
        }

        configurarListaAlcancias(listaFiltrada, progresos)
    }

    private fun configurarListaAlcancias(planes: List<PlanAhorroEntidad>, progresos: Map<Int, Double>) {
        val database = BaseDatos.obtenerInstancia(requireContext())
        val repoPlan = RepositorioPlanAhorro(database.planAhorroDao())

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

                val dineroActual = progresos[plan.idAhorro] ?: 0.0
                val metaTotal = plan.montoMeta ?: 1.0

                val metaCompletada = dineroActual >= metaTotal && (plan.montoMeta ?: 0.0) > 0.0

                if (metaCompletada) {
                    txtEstado.text = "¡Meta Completada!"
                    txtEstado.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    holder.itemView.setOnClickListener(null)
                    holder.itemView.alpha = 0.7f

                    if (plan.activo) {
                        hablar("¡Felicidades! Has completado tu meta de ahorro para ${plan.nombrePlan}")

                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val planDesactivado = plan.copy(activo = false)
                            repoPlan.actualizarPlanAhorro(planDesactivado)
                        }
                    }
                } else {
                    holder.itemView.alpha = 1.0f
                    if (plan.activo) {
                        txtEstado.text = "Activo"
                        txtEstado.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    } else {
                        txtEstado.text = "Inactivo"
                        txtEstado.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    }
                    holder.itemView.setOnClickListener {
                        mostrarOpcionesDialogo(plan)
                    }
                }

                txtProgresoTexto.text = "$%.2f / $%.2f".format(dineroActual, metaTotal)

                val porcentajeCompletado = ((dineroActual / metaTotal) * 100).toInt()
                progresoBarra.progress = if (porcentajeCompletado > 100) 100 else porcentajeCompletado
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_VOZ && resultCode == Activity.RESULT_OK && data != null) {
            val resultado = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val textoEscuchado = resultado?.get(0) ?: ""

            if (textoEscuchado.isNotEmpty()) {
                binding.etBuscarPlan.setText(textoEscuchado)
                textoBusqueda = textoEscuchado
                aplicarFiltrosYActualizar()
            }
        }
    }

    override fun onDestroyView() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroyView()
        _binding = null
    }
}