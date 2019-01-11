package com.prettygoodcomputing.a4

import android.app.Activity
import android.arch.lifecycle.LiveData
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_selected_folders.*

class SelectedFoldersActivity : AppCompatActivity() {

    private val TAG = "SelectedFoldersActivity"

    private val REQUEST_OPEN_DOCUMENT_TREE = 110

    private val selectedFolders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED, intent)
        setContentView(R.layout.activity_selected_folders)
        setSupportActionBar(toolbar)

//        selectedFolders.addAll()

        fab.setOnClickListener { view ->
            val openDocumentTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(openDocumentTreeIntent, REQUEST_OPEN_DOCUMENT_TREE)
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun notifyDataSetChanged() {
//
//        val selectedFoldersFragment = supportFragmentManager.findFragmentById(R.id.fragment_selected_folders)
//        if (selectedFoldersFragment is SelectedFoldersFragment) {
//            selectedFoldersFragment.notifyDataSetChanged()
//        }
        setActivityResult()
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
                    if (!selectedFolders.contains(selectedFolder)) {
                        selectedFolders.add(selectedFolder)
                        notifyDataSetChanged()
                    }

                    // Persist access permissions.
                    val takeFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uriTree, takeFlags)
                }
            }
        }
        Logger.exit(TAG, "onActivityResult()")
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
//        if (item != null) {
//            val id = item.itemId
//            if (id == android.R.id.home) {
//                super.onBackPressed()
//                return true
//            }
//        }
        return super.onOptionsItemSelected(item)
    }
}
