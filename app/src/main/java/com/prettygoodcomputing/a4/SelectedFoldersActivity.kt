package com.prettygoodcomputing.a4

import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.MenuItem
import com.prettygoodcomputing.a4.databinding.ActivitySelectedFoldersBinding

import kotlinx.android.synthetic.main.activity_selected_folders.*

class SelectedFoldersActivity : AppCompatActivity() {

    private val TAG = "SelectedFoldersActivity"

    private val REQUEST_OPEN_DOCUMENT_TREE = 110

    private val context = this
    private val binding by lazy { DataBindingUtil.setContentView<ActivitySelectedFoldersBinding>(this, R.layout.activity_selected_folders) }
    private val viewModel by lazy { SelectedFoldersViewModel(application) }
    private val selectedFolderTouchCallback = SelectedFolderTouchCallback()
    private val itemTouchHelper = ItemTouchHelper(selectedFolderTouchCallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED, intent)
        setUpDataBinding()
        setUpRecyclerView()

        fab.setOnClickListener { view ->
            val openDocumentTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(openDocumentTreeIntent, REQUEST_OPEN_DOCUMENT_TREE)
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setUpDataBinding() {
        setSupportActionBar(binding.toolbar)
        binding.setLifecycleOwner(this)
        binding.viewModel = viewModel
    }

    private fun setUpRecyclerView() {

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        // setup the content of the recycler view
        val selectedFolderAdapter = SelectedFolderAdapter()
        binding.recyclerView.adapter = selectedFolderAdapter
        selectedFolderAdapter.setOnItemClickListener(object : SelectedFolderAdapter.OnItemClickListener {
            override fun onItemClick(folder: String) {
                viewModel.onClickSelectedFolder(folder)
            }

            override fun onItemStartDrag(viewHolder: RecyclerView.ViewHolder) {
                startDrag(viewHolder)
            }
        })

        viewModel.selectedFolders.observe(this, Observer<List<String>> {
            selectedFolderAdapter.submitList(it)
        })
    }

    fun startDrag(viewHolder: RecyclerView.ViewHolder) {
        Logger.v(TAG, "startDrag()")
        itemTouchHelper.startDrag(viewHolder)
    }

    private fun setActivityResult() {
//        intent.putExtra("selected-folders", mSelectedFolders.toTypedArray())
        setResult(RESULT_OK, intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        Logger.enter(TAG, "onActivityResult() requestCode = " + requestCode + ", resultCode = " + resultCode + ", intent = " + intent)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OPEN_DOCUMENT_TREE && intent != null) {
                val uriTree = intent.data
                if (uriTree != null) {
                    Logger.v(TAG, "onActivityResult() REQUEST_OPEN_DOCUMENT_TREE uriTree = $uriTree")
                    val selectedFolder = uriTree.toString()
                    viewModel.insert(selectedFolder)
                    setActivityResult()

                    // Persist access permissions.
                    val takeFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uriTree, takeFlags)
                }
            }
        }
        Logger.exit(TAG, "onActivityResult()")
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            val id = item.itemId
            if (id == android.R.id.home) {
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private inner class SelectedFolderTouchCallback: ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

        private val TAG = "SelectedFolderTouchCallback"

        override fun isLongPressDragEnabled(): Boolean {
            Logger.v(TAG, "isLongPressDragEnabled()")
            return false
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (context != null) {
                viewHolder?.itemView?.setBackgroundColor((context as Context).getResources().getColor(R.color.selection_color, (context as Context).theme))
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (context != null) {
                viewHolder?.itemView?.setBackgroundColor((context as Context).getResources().getColor(R.color.app_background_color, (context as Context).theme))
            }
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            Logger.enter(TAG, "onMove()")
            if (recyclerView != null && viewHolder != null && target != null) {
                val fromItem = viewHolder.toString()
                val fromPosition = viewHolder.adapterPosition
                val toItem = target.toString()
                val toPosition = target.adapterPosition
                Logger.v(TAG, "onMove() fromPosition = $fromPosition, toPosition = $toPosition")
                viewModel.swap(fromItem, fromPosition, toItem, toPosition)
                setActivityResult()
            }
            Logger.exit(TAG, "onMove()")
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            Logger.enter(TAG, "onSwiped()")
            if (viewHolder != null) {
                val selectedFolder = viewHolder.toString()
                Logger.v(TAG, "onSwiped() selectedFolder = $selectedFolder")
//                mListener?.onListFragmentDeleted(selectedFolder)
            }
            Logger.exit(TAG, "onSwiped()")
        }
    }
}
