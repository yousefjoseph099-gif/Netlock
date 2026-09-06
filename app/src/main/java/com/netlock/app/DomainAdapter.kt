package com.netlock.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.netlock.app.data.DomainEntity

class DomainAdapter(
    private val onDelete: (DomainEntity) -> Unit
) : RecyclerView.Adapter<DomainAdapter.VH>() {

    private val items = mutableListOf<DomainEntity>()

    fun submitList(newItems: List<DomainEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvDomain)
        val delete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_domain, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.domain
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size
}
