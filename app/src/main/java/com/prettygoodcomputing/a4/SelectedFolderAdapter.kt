package com.prettygoodcomputing.a4

import android.arch.lifecycle.Observer
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.prettygoodcomputing.a4.databinding.SelectedFolderBinding
import kotlinx.android.synthetic.main.selected_folder.view.*

class SelectedFolderAdapter(val activity: AppCompatActivity, val viewModel: SelectedFoldersViewModel): ListAdapter<FolderItem, SelectedFolderAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val TAG = "SelectedFolderAdapter"

    var listener: OnItemClickListener? = null

    init {
        viewModel.selectedFolders.observe(activity, Observer {
            notifyDataSetChanged()
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.selected_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val folder = getItem(position).url
        val folderItem = viewModel.selectedFolders.value?.find { it.url == folder }
        if (folderItem != null) {
            viewHolder.bindFolderItem(folderItem)
        }
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        private val binding = SelectedFolderBinding.bind(view)
        private val handleView: ImageView = view.handle

        init {
            view.setOnClickListener{
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener?.onItemClick(getItem(position).url)
                }
            }

            handleView.setOnTouchListener { v, event ->
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        listener?.onItemStartDrag(this)
                    }
                }
                v?.onTouchEvent(event)
                true
            }
        }

        fun bindFolderItem(folderItem: FolderItem) {
            binding.folderItem = folderItem
        }
    }

    interface OnItemClickListener {
        fun onItemClick(selectedFolder: String)
        fun onItemStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    companion object {
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<FolderItem>() {
            override fun areItemsTheSame(oldItem: FolderItem, newItem: FolderItem): Boolean {
                return oldItem.equals(newItem)
            }

            override fun areContentsTheSame(oldItem: FolderItem, newItem: FolderItem): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}
