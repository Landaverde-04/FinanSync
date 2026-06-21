package com.grupo4.finansync.ui.dashboards



import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.grupo4.finansync.data.repositorio.RepositorioPlanAhorro

class PlanesViewModel(private val repositorio: RepositorioPlanAhorro, idUsuario: String) : ViewModel() {

    val planesActivosLiveData = repositorio.obtenerPlanesAhorroActivosPorUsuario(idUsuario).asLiveData()

    @Suppress("UNCHECKED_CAST")
    class Factory(private val repositorio: RepositorioPlanAhorro, private val idUsuario: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlanesViewModel(repositorio, idUsuario) as T
        }
    }
}