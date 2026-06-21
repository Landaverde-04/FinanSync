package com.grupo4.finansync.ui.categoria

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.grupo4.finansync.databinding.ItemCategoriaBinding
import com.grupo4.finansync.modelo.CategoriaEntidad
import com.grupo4.finansync.modelo.obtenerEmoji
import com.grupo4.finansync.modelo.obtenerNombreLimpio

class CategoriaAdapter(
    private var lista: List<CategoriaEntidad> = emptyList(),
    private val onEliminarClick: (CategoriaEntidad) -> Unit
) : RecyclerView.Adapter<CategoriaAdapter.CategoriaViewHolder>() {

    inner class CategoriaViewHolder(
        val binding: ItemCategoriaBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriaViewHolder {
        val binding = ItemCategoriaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoriaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoriaViewHolder, position: Int) {
        val categoria = lista[position]

        holder.binding.iconoFila.text = categoria.obtenerEmoji()
        holder.binding.txtNombreCategoria.text = categoria.obtenerNombreLimpio()
        holder.binding.txtTipoCategoria.text = "(${categoria.tipo})"

        holder.binding.btnEliminarCategoria.setOnClickListener {
            onEliminarClick(categoria)
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<CategoriaEntidad>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = lista.size
            override fun getNewListSize(): Int = nuevaLista.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return lista[oldItemPosition].idCategoria == nuevaLista[newItemPosition].idCategoria
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return lista[oldItemPosition] == nuevaLista[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        lista = nuevaLista
        diffResult.dispatchUpdatesTo(this)
    }
}
