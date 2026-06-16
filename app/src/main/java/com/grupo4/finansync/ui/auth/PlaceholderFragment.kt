package com.grupo4.finansync.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

/**
 * Fragment temporal que ocupa las pestañas de la barra inferior
 * mientras los demás módulos implementan sus pantallas.
 *
 * Cuando M3/M4/M5 terminen sus fragments:
 * 1. Cambian el android:name en nav_graph_inner.xml al nombre de su Fragment real
 * 2. Este archivo puede eliminarse
 */
class PlaceholderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Crea un TextView simple — no necesita layout propio
        return TextView(requireContext()).apply {
            val destino = findNavController().currentDestination?.label ?: "Pantalla"
            text = "🚧 $destino\nEn construcción"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
    }
}