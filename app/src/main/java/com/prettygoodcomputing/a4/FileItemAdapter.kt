package com.prettygoodcomputing.a4

import android.arch.lifecycle.Observer
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.file_item.view.*

class FileItemAdapter(val activity: AppCompatActivity, val viewModel: MainViewModel): ListAdapter<FileItem, FileItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val TAG = "FileItemAdapter"

    var listener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val fileItem = getItem(position)
        Logger.v(TAG, "onBindViewHolder ${fileItem.name}")
        viewHolder.nameView.text = fileItem.name
        viewHolder.infoView.text = Formatter.formatFileInfo(fileItem)
        viewHolder.progressView.text = Formatter.formatProgress(fileItem)

        val id = if (fileItem.selected) R.color.selection_color else R.color.app_background_color
        viewHolder.view.setBackgroundColor(activity.resources.getColor(id, activity.theme))
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.name
        val infoView: TextView = view.info
        val progressView: TextView = view.progress

        init {
            view.setOnClickListener{
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener?.onItemClick(getItem(position))
                }
            }
            view.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener?.onItemLongClick(getItem(position))
                }
                true
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(fileItem: FileItem)
        fun onItemLongClick(fileItem: FileItem)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    companion object {
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}
