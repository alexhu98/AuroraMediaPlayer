package com.prettygoodcomputing.a4

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import kotlinx.android.synthetic.main.file_item.view.*

class FileItemAdapter: ListAdapter<FileItem, FileItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    var listener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val fileItem = getItem(position)
        viewHolder.nameView.text = fileItem.name
        viewHolder.infoView.text = fileItem.fileSize.toString()
        viewHolder.progressView.text = "*"
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

        override fun toString(): String {
            return super.toString() + " '" + nameView.text + "'"
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
