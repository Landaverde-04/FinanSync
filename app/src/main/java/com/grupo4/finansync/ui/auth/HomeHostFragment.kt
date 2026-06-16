package com.grupo4.finansync.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class HomeHostFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = View(requireContext()) // vista vacía, solo es un trampolín

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Login ya fue validado en LoginFragment/RegisterFragment.
        // Este fragment solo existe como destino final del grafo de auth.
        (requireActivity() as AuthActivity).irAMain()
    }
}