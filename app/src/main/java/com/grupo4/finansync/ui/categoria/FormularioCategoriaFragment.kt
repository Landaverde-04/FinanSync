package com.grupo4.finansync.ui.categoria

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grupo4.finansync.R
import com.grupo4.finansync.bd.BaseDatos
import com.grupo4.finansync.data.remote.SupabaseCliente
import com.grupo4.finansync.data.repositorio.RepositorioCategoria
import com.grupo4.finansync.data.repositorio.RepositorioTransaccion
import com.grupo4.finansync.databinding.FragmentFormularioCategoriaBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import io.github.jan.supabase.gotrue.auth

import android.util.TypedValue

class FormularioCategoriaFragment : Fragment() {

    private var _binding: FragmentFormularioCategoriaBinding? = null
    private val binding get() = _binding!!

    private val vm: CategoriaViewModel by viewModels {
        val bd = BaseDatos.obtenerInstancia(requireContext().applicationContext)
        val repoCat = RepositorioCategoria(bd.categoriaDao())
        val repoTrans = RepositorioTransaccion(bd.transaccionDao())
        CategoriaViewModel.Factory(repoCat, repoTrans)
    }

    private var emojiSeleccionado: String = "🍔"
    private var emojiPersonalizado: String? = null
    private lateinit var listaCards: List<MaterialCardView>
    private val defaultEmojis = listOf("🍔", "🚌", "🎬", "💰", "🏥")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormularioCategoriaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listaCards = listOf(
            binding.cardEmoji1,
            binding.cardEmoji2,
            binding.cardEmoji3,
            binding.cardEmoji4,
            binding.cardEmoji5,
            binding.cardEmojiMas
        )

        configurarSelectorEmojis()
        configurarBotones()
    }

    private fun configurarSelectorEmojis() {
        seleccionarCard(0)

        for (i in 0 until 5) {
            listaCards[i].setOnClickListener {
                seleccionarCard(i)
            }
        }

        binding.cardEmojiMas.setOnClickListener {
            mostrarDialogoEmojis()
        }
    }

    private fun seleccionarCard(indice: Int) {
        if (indice < 5) {
            emojiSeleccionado = defaultEmojis[indice]
        } else {
            emojiSeleccionado = emojiPersonalizado ?: "🍔"
        }

        val colorPrimario = obtenerColorDeTema(com.google.android.material.R.attr.colorPrimary)
        val colorNormal = obtenerColorDeTema(com.google.android.material.R.attr.colorOutline)
        val colorFondoSeleccionado = obtenerColorDeTema(com.google.android.material.R.attr.colorPrimaryContainer)

        for (i in listaCards.indices) {
            if (i == indice) {
                listaCards[i].strokeColor = colorPrimario
                listaCards[i].strokeWidth = dpToPx(3)
                listaCards[i].setCardBackgroundColor(ColorStateList.valueOf(colorFondoSeleccionado))
            } else {
                listaCards[i].strokeColor = colorNormal
                listaCards[i].strokeWidth = dpToPx(1)
                listaCards[i].setCardBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.transparent)))
            }
        }
    }

    private fun obtenerColorDeTema(attr: Int): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun mostrarDialogoEmojis() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_selector_emojis, null)
        val gridView = dialogView.findViewById<GridView>(R.id.gridViewEmojis)

        val emojis = listOf(
            "🍔", "🍕", "🌮", "☕", "🍺", "🍎",
            "🚌", "🚗", "✈️", "🏍️", "⛽", "🚲",
            "🎬", "🎮", "⚽", "🎸", "🎨", "📚",
            "💰", "💵", "💳", "🏦", "📈", "🛍️",
            "🏥", "💊", "🏋️", "💇", "👕", "🏠",
            "💼", "📱", "🔧", "🎁", "🎓", "🐾"
        )

        val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_emoji_grid, emojis) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.text = getItem(position)
                return tv
            }
        }
        gridView.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Selecciona un ícono")
            .setView(dialogView)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            val emojiElegido = emojis[position]
            emojiPersonalizado = emojiElegido
            binding.txtEmojiMas.text = emojiElegido
            seleccionarCard(5)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun configurarBotones() {
        binding.btnVolverFormCategoria.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCancelarCategoria.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnGuardarCategoria.setOnClickListener {
            guardarCategoria()
        }
    }

    private fun guardarCategoria() {
        val nombre = binding.etNombreCategoria.text.toString().trim()

        if (nombre.isEmpty()) {
            binding.tilNombreCategoria.error = "Ingresa el nombre de la categoría"
            return
        } else {
            binding.tilNombreCategoria.error = null
        }

        val idUsuario = SupabaseCliente.cliente.auth.currentUserOrNull()?.id ?: "usuario_prueba"
        val tipo = if (binding.chipGasto.isChecked) "gasto" else "ingreso"
        val nombreConEmoji = "$emojiSeleccionado $nombre"

        val nuevaCategoria = CategoriaEntidad(
            idCategoria = 0,
            idUsuario = idUsuario,
            nombreCategoria = nombreConEmoji,
            tipo = tipo
        )

        vm.insertarCategoria(nuevaCategoria)
        Toast.makeText(requireContext(), "Categoría guardada con éxito", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
