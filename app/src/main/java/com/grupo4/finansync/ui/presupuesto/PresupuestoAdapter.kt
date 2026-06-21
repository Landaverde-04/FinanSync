package com.grupo4.finansync.ui.presupuesto

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.ItemPresupuestoBinding
import com.grupo4.finansync.modelo.PresupuestoEntidad
import java.util.Locale

class PresupuestoAdapter(
    private var lista: List<PresupuestoUI> = emptyList(),
    private val onEliminarClick: (PresupuestoEntidad) -> Unit
) : RecyclerView.Adapter<PresupuestoAdapter.PresupuestoViewHolder>() {

    inner class PresupuestoViewHolder(
        val binding: ItemPresupuestoBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresupuestoViewHolder {
        val binding = ItemPresupuestoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PresupuestoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PresupuestoViewHolder, position: Int) {
        val ui = lista[position]
        val context = holder.itemView.context

        holder.binding.iconoFila.text = ui.categoriaEmoji
        holder.binding.txtNombrePresupuesto.text = ui.categoriaNombre
        holder.binding.txtPeriodoPresupuesto.text = "(${ui.entidad.periodo})"

        val gastadoStr = String.format(Locale.US, "$%.2f", ui.montoGastado)
        val limiteStr = String.format(Locale.US, "$%.2f", ui.entidad.montoLimite)
        holder.binding.txtValoresPresupuesto.text = "$gastadoStr / $limiteStr"

        val porcentaje = ui.porcentajeConsumo
        val esExcedido = porcentaje > 100

        if (esExcedido) {
            holder.binding.txtPorcentajePresupuesto.text = "$porcentaje% ⚠️"
            holder.binding.txtPorcentajePresupuesto.setTextColor(ContextCompat.getColor(context, R.color.rojo_gasto))
            holder.binding.progressPresupuesto.setIndicatorColor(ContextCompat.getColor(context, R.color.rojo_gasto))
            holder.binding.progressPresupuesto.progress = 100
        } else {
            holder.binding.txtPorcentajePresupuesto.text = "$porcentaje%"
            holder.binding.txtPorcentajePresupuesto.setTextColor(ContextCompat.getColor(context, R.color.colorOnSurface))
            holder.binding.progressPresupuesto.setIndicatorColor(ContextCompat.getColor(context, R.color.colorPrimary))
            holder.binding.progressPresupuesto.progress = porcentaje
        }

        holder.binding.btnEliminarPresupuesto.setOnClickListener {
            onEliminarClick(ui.entidad)
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<PresupuestoUI>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = lista.size
            override fun getNewListSize(): Int = nuevaLista.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return lista[oldItemPosition].entidad.idPresupuesto == nuevaLista[newItemPosition].entidad.idPresupuesto
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
