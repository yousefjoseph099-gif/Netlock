package com.netlock.app

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.netlock.app.util.InstalledAppInfo

class AppPickerAdapter(
    private val apps: List<InstalledAppInfo>,
    private val pm: PackageManager,
    private val selected: MutableSet<String>
) : RecyclerView.Adapter<AppPickerAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val checkbox: CheckBox = view.findViewById(R.id.cbSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.name.text = app.label
        try {
            holder.icon.setImageDrawable(pm.getApplicationIcon(app.packageName))
        } catch (e: Exception) {
            holder.icon.setImageResource(R.drawable.ic_launcher)
        }
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selected.contains(app.packageName)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(app.packageName) else selected.remove(app.packageName)
        }
        holder.itemView.setOnClickListener { holder.checkbox.toggle() }
    }

    override fun getItemCount() = apps.size
}
