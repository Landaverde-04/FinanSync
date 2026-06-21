package com.grupo4.finansync.ui.historial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.ItemTransaccionBinding
import com.grupo4.finansync.modelo.TransaccionEntidad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter de historial con DiffUtil para actualizaciones eficientes.
 *
 * Soporta dos tipos de ítem:
 *  - TIPO_HEADER  → encabezado de mes (ej. "Junio 2026")
 *  - TIPO_ITEM    → fila de TransaccionEntidad
 *
 * Recibe un mapa idCategoria → nombre para mostrar la categoría.
 * Recibe un callback onItemClick para navegar al detalle.
 */
class TransaccionDiffAdapter(
    private var nombresCategorias: Map<Int, String> = emptyMap(),
    private val onItemClick: (TransaccionEntidad) -> Unit
) : ListAdapter<TransaccionDiffAdapter.ListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    // ── Tipos de ítem ──────────────────────────────────────────────────────
    companion object {
        private const val TIPO_HEADER = 0
        private const val TIPO_ITEM = 1

        private val FMT_MES = SimpleDateFormat("MMMM yyyy", Locale("es"))
        private val FMT_FECHA = SimpleDateFormat("dd/MMM/yyyy", Locale("es"))
    }

    // ── Modelos sellados ───────────────────────────────────────────────────
    sealed class ListItem {
        data class Header(val etiqueta: String) : ListItem()
        data class Item(val transaccion: TransaccionEntidad) : ListItem()
    }

    // ── DiffUtil ───────────────────────────────────────────────────────────
    class DiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean =
            when {
                oldItem is ListItem.Header && newItem is ListItem.Header ->
                    oldItem.etiqueta == newItem.etiqueta
                oldItem is ListItem.Item && newItem is ListItem.Item ->
                    oldItem.transaccion.idTransaccion == newItem.transaccion.idTransaccion
                else -> false
            }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean =
            oldItem == newItem
    }

    // ── ViewHolders ────────────────────────────────────────────────────────
    inner class HeaderViewHolder(val textView: TextView) :
        RecyclerView.ViewHolder(textView)

    inner class TransaccionViewHolder(val binding: ItemTransaccionBinding) :
        RecyclerView.ViewHolder(binding.root)

    // ── Creación de vistas ─────────────────────────────────────────────────
    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is ListItem.Header -> TIPO_HEADER
            is ListItem.Item -> TIPO_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TIPO_HEADER -> {
                // Encabezado de mes — usamos un TextView sencillo con estilo de Material
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(48, 28, 48, 8) }
                    setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_LabelLarge
                    )
                    setTextColor(
                        ContextCompat.getColor(context, R.color.gris_texto)
                    )
                    isAllCaps = true
                }
                HeaderViewHolder(tv)
            }
            else -> {
                val binding = ItemTransaccionBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                TransaccionViewHolder(binding)
            }
        }
    }

    // ── Binding ────────────────────────────────────────────────────────────
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val listItem = getItem(position)) {
            is ListItem.Header -> (holder as HeaderViewHolder).textView.text = listItem.etiqueta
            is ListItem.Item -> bindTransaccion(
                holder as TransaccionViewHolder,
                listItem.transaccion
            )
        }
    }

    private fun bindTransaccion(holder: TransaccionViewHolder, t: TransaccionEntidad) {
        val ctx = holder.itemView.context
        val esIngreso = t.tipo == "ingreso"

        val colorPrincipal = if (esIngreso)
            ContextCompat.getColor(ctx, R.color.verde_ingreso)
        else
            ContextCompat.getColor(ctx, R.color.rojo_gasto)

        // Ícono y color del círculo
        holder.binding.iconoFila.text = if (esIngreso) "➕" else "➖"
        holder.binding.iconoFila.background.setTint(colorPrincipal)

        // Descripción
        holder.binding.txtDescripcionFila.text =
            t.descripcion.ifEmpty { "Sin descripción" }

        // Categoría + fecha
        val cat = nombresCategorias[t.idCategoria] ?: "Categoría"
        val fecha = FMT_FECHA.format(Date(t.creadoEn))
        holder.binding.txtFechaFila.text = "$cat · $fecha"

        // Monto con signo y color
        val signo = if (esIngreso) "+" else "-"
        holder.binding.txtMontoFila.text =
            String.format(Locale.US, "%s$%.2f", signo, t.monto)
        holder.binding.txtMontoFila.setTextColor(colorPrincipal)

        // Click → detalle
        holder.itemView.setOnClickListener { onItemClick(t) }
    }

    // ── API pública ────────────────────────────────────────────────────────

    /** Actualiza el mapa de nombres de categoría sin re-enviar la lista. */
    fun actualizarCategorias(nombres: Map<Int, String>) {
        nombresCategorias = nombres
        notifyDataSetChanged()
    }

    /**
     * Convierte una lista plana de transacciones en una lista con headers de mes,
     * agrupando por "MMMM yyyy" y las envía a ListAdapter (usa DiffUtil internamente).
     *
     * La lista debe llegar ya ordenada por fecha DESC (como devuelve el DAO).
     */
    fun submitTransacciones(transacciones: List<TransaccionEntidad>) {
        val items = mutableListOf<ListItem>()
        var mesActual: String? = null

        for (t in transacciones) {
            val mes = FMT_MES.format(Date(t.creadoEn))
                .replaceFirstChar { it.uppercase() }
            if (mes != mesActual) {
                items.add(ListItem.Header(mes))
                mesActual = mes
            }
            items.add(ListItem.Item(t))
        }
        submitList(items)
    }
}