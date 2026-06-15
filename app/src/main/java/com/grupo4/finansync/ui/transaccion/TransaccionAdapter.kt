package com.grupo4.finansync.ui.transaccion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.grupo4.finansync.R
import com.grupo4.finansync.databinding.ItemTransaccionBinding
import com.grupo4.finansync.modelo.TransaccionEntidad
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter del RecyclerView de transacciones.
 * Toma una lista de TransaccionEntidad y la convierte en filas visibles.
 *
 * Recibe un mapa idCategoria -> nombre para mostrar el nombre de la categoría
 * (la transacción solo guarda el id, no el nombre).
 */
class TransaccionAdapter(
    private var lista: List<TransaccionEntidad> = emptyList(),
    private var nombresCategorias: Map<Int, String> = emptyMap()
) : RecyclerView.Adapter<TransaccionAdapter.TransaccionViewHolder>() {

    /**
     * El ViewHolder guarda una fila ya "inflada" para poder reciclarla.
     * Usamos ViewBinding para acceder a los elementos de item_transaccion.xml.
     */
    inner class TransaccionViewHolder(
        val binding: ItemTransaccionBinding
    ) : RecyclerView.ViewHolder(binding.root)

    // 1. CREA una fila vacía (se llama pocas veces, solo para las que caben en pantalla)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransaccionViewHolder {
        val binding = ItemTransaccionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TransaccionViewHolder(binding)
    }

    // 2. RELLENA una fila con los datos de la posición dada (se llama muchas veces al hacer scroll)
    override fun onBindViewHolder(holder: TransaccionViewHolder, position: Int) {
        val transaccion = lista[position]
        val contexto = holder.itemView.context

        // Colores según el tipo
        val esIngreso = transaccion.tipo == "ingreso"
        val color = if (esIngreso)
            ContextCompat.getColor(contexto, R.color.verde_ingreso)
        else
            ContextCompat.getColor(contexto, R.color.rojo_gasto)

        // Ícono circular: símbolo y color
        holder.binding.iconoFila.text = if (esIngreso) "➕" else "➖"
        holder.binding.iconoFila.background.setTint(color)

        // Descripción (si está vacía, mostramos un texto por defecto)
        holder.binding.txtDescripcionFila.text =
            transaccion.descripcion.ifEmpty { "Sin descripción" }

        // Línea secundaria: nombre de categoría + fecha
        val nombreCategoria = nombresCategorias[transaccion.idCategoria] ?: "Categoría"
        val fecha = SimpleDateFormat("dd/MMM/yyyy", Locale("es")).format(Date(transaccion.creadoEn))
        holder.binding.txtFechaFila.text = "$nombreCategoria · $fecha"

        // Monto con signo y color
        val signo = if (esIngreso) "+" else "-"
        holder.binding.txtMontoFila.text = String.format(Locale.US, "%s$%.2f", signo, transaccion.monto)
        holder.binding.txtMontoFila.setTextColor(color)
    }

    // 3. Cuántas filas hay en total
    override fun getItemCount(): Int = lista.size

    /** Reemplaza la lista y refresca el RecyclerView. */
    fun actualizarLista(nueva: List<TransaccionEntidad>, nombres: Map<Int, String>) {
        lista = nueva
        nombresCategorias = nombres
        notifyDataSetChanged()  // avisa al RecyclerView que los datos cambiaron
    }
}
