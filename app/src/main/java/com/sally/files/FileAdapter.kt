package com.sally.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private val onClick: (File) -> Unit,
    private val onLong: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private var items: List<File> = emptyList()
    private var selected: Set<File> = emptySet()

    fun submit(list: List<File>, sel: Set<File>) {
        items = list
        selected = sel.toSet()
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = items[position]
        h.name.text = f.name
        h.sub.text = if (f.isDirectory) Fmt.date(f.lastModified())
        else "${Fmt.size(f.length())}  •  ${Fmt.date(f.lastModified())}"
        h.icon.setImageResource(
            when {
                f.isDirectory -> R.drawable.ic_folder
                Archive.isArchive(f) -> R.drawable.ic_archive
                Fmt.isImage(f) -> R.drawable.ic_image
                else -> R.drawable.ic_file
            }
        )
        h.itemView.isActivated = f in selected
        h.itemView.setOnClickListener { onClick(f) }
        h.itemView.setOnLongClickListener { onLong(f); true }
    }
}
