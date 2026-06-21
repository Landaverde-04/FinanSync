package com.grupo4.finansync.ui.dashboards



import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlanesViewModel(private val repositorio: RepositorioPlanAhorro, idUsuario: String) : ViewModel() {

    val planesActivosLiveData = repositorio.obtenerPlanesAhorroPorUsuario(idUsuario).asLiveData()

    @Suppress("UNCHECKED_CAST")
    class Factory(private val repositorio: RepositorioPlanAhorro, private val idUsuario: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlanesViewModel(repositorio, idUsuario) as T
        }
    }
    private val _mapaProgreso = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val mapaProgreso: StateFlow<Map<Int, Double>> = _mapaProgreso

    fun cargarProgresos(listaPlanes: List<PlanAhorroEntidad>, daoProgreso: com.grupo4.finansync.bd.dao.ProgresoAhorroDao) {
        viewModelScope.launch {
            val mapaTemporal = mutableMapOf<Int, Double>()
            listaPlanes.forEach { plan ->
                val monto = daoProgreso.sumarMontoAhorradoPorPlan(plan.idAhorro)
                mapaTemporal[plan.idAhorro] = monto
            }
            _mapaProgreso.value = mapaTemporal
        }
    }



    fun eliminarPlan(plan: PlanAhorroEntidad) {
        viewModelScope.launch {

            repositorio.eliminarPlanAhorro(plan)
        }
    }
}