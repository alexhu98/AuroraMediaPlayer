package com.prettygoodcomputing.a4

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

class SelectedFolderAdapter: ListAdapter<String, SelectedFolderAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val TAG = "SelectedFolderAdapter"

    var listener: OnItemClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.selected_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val selectedFolder = getItem(position)
        viewHolder.nameView.text = selectedFolder
        viewHolder.infoView.text = selectedFolder
        viewHolder.handleView.setOnTouchListener(object: View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        listener?.onItemStartDrag(viewHolder)
                    }
                }
                return v?.onTouchEvent(event) ?: true
            }
        })
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
        }

        override fun toString(): String {
            return super.toString() + " '" + nameView.text + "'"
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
