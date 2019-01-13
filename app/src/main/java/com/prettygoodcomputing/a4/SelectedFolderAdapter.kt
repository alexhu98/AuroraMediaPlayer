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
import kotlinx.android.synthetic.main.selected_folder.view.*

class SelectedFolderAdapter(val activity: AppCompatActivity, val viewModel: SelectedFoldersViewModel): ListAdapter<String, SelectedFolderAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val TAG = "SelectedFolderAdapter"

    var listener: OnItemClickListener? = null

    init {
        viewModel.selectedItem.observe(activity, Observer {
            val list = viewModel.selectedFolders.value ?: listOf()
            val lastIndex = list.indexOf(viewModel.lastSelectedItem)
            if (lastIndex >= 0) {
                notifyItemChanged(lastIndex)
            }
            val index = list.indexOf(it)
            if (index >= 0) {
                notifyItemChanged(index)
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.selected_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val selectedFolder = getItem(position)
        viewHolder.nameView.text = selectedFolder
        viewHolder.infoView.text = selectedFolder

        val selected = selectedFolder == viewModel.selectedItem.value
        val id = if (selected) R.color.selection_color else R.color.app_background_color
        viewHolder.view.setBackgroundColor(activity.getResources().getColor(id, activity.theme))

//        viewHolder.handleView.setOnTouchListener { v, event ->
//            when (event?.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    listener?.onItemStartDrag(viewHolder)
//                }
//            }
//            v?.onTouchEvent(event)
//            true
//        }
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.name
        val infoView: TextView = view.info
        val handleView: ImageView = view.handle

        init {
            view.setOnClickListener{
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener?.onItemClick(getItem(position))
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

        override fun toString(): String {
            return infoView.text.toString()
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
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem.equals(newItem)
            }

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}
