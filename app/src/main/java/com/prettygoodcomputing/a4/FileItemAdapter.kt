package com.prettygoodcomputing.a4

import android.arch.lifecycle.Observer
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.prettygoodcomputing.a4.databinding.FileItemBinding

class FileItemAdapter(val activity: AppCompatActivity, val viewModel: MainViewModel): ListAdapter<FileItem, FileItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val TAG = "FileItemAdapter"

    var listener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.file_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val fileItem = getItem(position)
        viewHolder.bindFileItem(fileItem)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val binding = FileItemBinding.bind(view)

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

        fun bindFileItem(fileItem: FileItem) {
            Logger.v(TAG, "bindFileItem ${fileItem.name}")
            binding.fileItem = fileItem
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
