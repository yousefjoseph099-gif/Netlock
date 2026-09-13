package com.netlock.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.netlock.app.data.BlockedLogEntity
import java.text.DateFormat
import java.util.Date

class BlockedLogAdapter(
    private val onWhitelist: (BlockedLogEntity) -> Unit,
    private val onSelectionChanged: (checkedCount: Int) -> Unit
) : RecyclerView.Adapter<BlockedLogAdapter.VH>() {

    private val items = mutableListOf<BlockedLogEntity>()
    // Which domains (by name) are currently checked for bulk whitelisting.
    // New items default to checked - the common case is "everything that
    // just broke", so a single tap should cover all of it.
    private val checked = mutableSetOf<String>()
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

    fun submitList(newItems: List<BlockedLogEntity>) {
        val newDomains = newItems.map { it.domain }.toSet()
        checked.retainAll(newDomains)
        newDomains.forEach { if (it !in checked) checked.add(it) }
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onSelectionChanged(checked.size)
    }

    fun selectAll() {
        checked.clear()
        checked.addAll(items.map { it.domain })
        notifyDataSetChanged()
        onSelectionChanged(checked.size)
    }

    fun selectNone() {
        checked.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    /** Distinct domain names currently checked, for a single bulk-insert call. */
    fun checkedDomains(): List<String> = items.map { it.domain }.distinct().filter { it in checked }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cbSelect)
        val domain: TextView = view.findViewById(R.id.tvBlockedDomain)
        val time: TextView = view.findViewById(R.id.tvBlockedTime)
        val whitelist: Button = view.findViewById(R.id.btnWhitelist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_blocked_domain, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.domain.text = item.domain
        holder.time.text = timeFormat.format(Date(item.timestampMillis))
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.domain in checked
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checked.add(item.domain) else checked.remove(item.domain)
            onSelectionChanged(checked.size)
        }
        holder.whitelist.setOnClickListener { onWhitelist(item) }
    }

    override fun getItemCount() = items.size
}
