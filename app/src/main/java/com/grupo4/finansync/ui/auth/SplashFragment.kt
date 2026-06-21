package com.grupo4.finansync.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.grupo4.finansync.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_splash,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)


        if (vieneDeLinkRecuperacion()) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500)

            if (!isAdded) return@launch

            if (viewModel.haySesionActiva()) {
                // Sesión activa normal → saltar directo a MainActivity
                (requireActivity() as AuthActivity).irAMain()
            } else {
                // Sin sesión → ir al login
                findNavController().navigate(
                    R.id.action_splash_to_login
                )
            }
        }
    }

    private fun vieneDeLinkRecuperacion(): Boolean {
        val uri = requireActivity().intent?.data ?: return false

        return uri.scheme == "finansync" &&
                uri.host == "auth"
    }
}