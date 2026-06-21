package com.grupo4.finansync.ui.dashboards



import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro
import com.grupo4.finansync.modelo.PlanAhorroEntidad
import kotlinx.coroutines.launch

class PlanesViewModel(private val repositorio: RepositorioPlanAhorro, idUsuario: String) : ViewModel() {

    val planesActivosLiveData = repositorio.obtenerPlanesAhorroPorUsuario(idUsuario).asLiveData()

    @Suppress("UNCHECKED_CAST")
    class Factory(private val repositorio: RepositorioPlanAhorro, private val idUsuario: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlanesViewModel(repositorio, idUsuario) as T
        }
    }
    fun eliminarPlan(plan: PlanAhorroEntidad) {
        viewModelScope.launch {

            repositorio.eliminarPlanAhorro(plan)
        }
    }
}