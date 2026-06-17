package com.grupo4.finansync.ui.dashboards
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import kotlinx.coroutines.launch

class DashboardViewModel(private val repositorio: RepositorioPlanAhorro) : ViewModel() {

    private val idUsuarioActual = "AU22002"
    val listaPlanesActivos: LiveData<List<PlanAhorroEntidad>> =
        repositorio.obtenerPlanesAhorroActivosPorUsuario(idUsuarioActual).asLiveData()

    fun crearPlanDeAhorro(
        metodoSeleccionado: String,
        meta: Double?,
        porcentaje: Double?,
        fijo: Double?
    ) {
        viewModelScope.launch {
            val nuevoPlan = PlanAhorroEntidad(
                idUsuario = idUsuarioActual,
                metodo = metodoSeleccionado, // "meta fija", "porcentaje" o "monto fijo"
                montoMeta = meta,
                porcentaje = porcentaje,
                montoFijo = fijo,
                activo = true
            )
            repositorio.insertarPlanAhorro(nuevoPlan)
        }
    }
}