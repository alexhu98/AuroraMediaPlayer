package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.os.AsyncTask

class AppRepository(val application: Application) {

    private val TAG = "AppRepository"

    private val ARG_DATA_STORE = "data-store"
    private val ARG_SELECTED_FOLDERS = "selected-folders"
    private val ARG_SELECTED_FOLDER_OPTIONS = "selected-folder-options"
    private val ARG_FILE_ITEMS = "file-items"
    private val ARG_LAST_SELECTED_FOLDER = "last-selected-folder"
    private val ARG_LAST_SELECTED_ITEM_MAP = "last-selected-item-map"
    private val ARG_LAST_PLAY_ITEM = "last-play-item"
    private val ARG_STORAGE_VOLUME = "storage-volume"
    private val ARG_DATABASE_LAST_UPDATE_TIME = "database-last-update"

    val selectedFolders = MediatorLiveData<List<String>>()
    val currentFolder = MutableLiveData<String>().apply { value = "" }
    val currentFolderInfo = MutableLiveData<String>().apply { value = "" }
    val currentOrderBy = MutableLiveData<String>().apply { value = FileItem.FIELD_NAME }
    val currentFileItems = MediatorLiveData<List<FileItem>>().apply { value = listOf() }

    private val fileItemDao = AppDatabase.getInstance(application).fileItemDao()
    private val allFileItems = fileItemDao.getFileItems()

    init {
        currentFileItems.addSource(allFileItems) {
            rearrangeAndCalculate()
        }
        initSelectedFolders()
        if (getSelectedFolders().isNotEmpty()) {
            currentFolder.value = getSelectedFolders()[0]
        }
        queryFileItems(getCurrentFolder(), getCurrentOrderBy())
    }

    @Synchronized
    fun getCurrentFolder(): String {
        return currentFolder.value ?: ""
    }

    @Synchronized
    fun setCurrentFolder(folder: String) {
        currentFolder.value = folder
    }

    @Synchronized
    fun getCurrentFolderInfo(): String {
        return currentFolderInfo.value ?: ""
    }

    @Synchronized
    fun setCurrentFolderInfo(folderInfo: String) {
        Logger.enter(TAG, "setCurrentFolderInfo = $folderInfo")
        currentFolderInfo.value = folderInfo
        Logger.exit(TAG, "setCurrentFolderInfo = $folderInfo")
    }

    @Synchronized
    fun getCurrentOrderBy(): String {
        return currentOrderBy.value ?: ""
    }

    @Synchronized
    fun setCurrentOrderBy(orderBy: String) {
        currentOrderBy.value = orderBy
    }

    @Synchronized
    fun queryFileItems(folder: String, orderBy: String) {
        if (getCurrentFolder() != folder || getCurrentOrderBy() != orderBy) {
            setCurrentFolder(folder)
            setCurrentOrderBy(orderBy)
            rearrangeAndCalculate()
        }
    }

    private fun rearrangeAndCalculate() {
        currentFileItems.value = rearrangeFileItems(allFileItems.value)
        calculateFolderInfo()
    }

    private fun rearrangeFileItems(all: List<FileItem>?): List<FileItem> {
        val comparator: Comparator<FileItem> = when (getCurrentOrderBy()) {
            FileItem.FIELD_NAME -> compareBy({ it.name }, { it.fileSize })
            FileItem.FIELD_FILE_SIZE -> compareBy({ -it.fileSize }, { it.name })
            FileItem.FIELD_LAST_MODIFIED -> compareBy({ -it.lastModified }, { it.name })
            else -> compareBy({ it.name }, { it.fileSize })
        }
        if (all == null) {
            Logger.v(TAG, "rearrangeFileItems() allFileItems.value == null")
        }
        val result = all
            ?.filter{ it.folder == getCurrentFolder() }
            ?.sortedWith(comparator)
            ?: listOf()
        return result
    }

    private fun calculateFolderInfo() {
        val fileItems = currentFileItems.value
        var folderInfo = when (fileItems == null) {
            true -> ""
            false -> {
                var count = 0
                var totalSize = 0L
                fileItems.forEach {
                    count++
                    totalSize += it.fileSize
                }
                "$count files, $totalSize bytes"
            }
        }
        setCurrentFolderInfo(folderInfo)
    }

    @Synchronized
    fun getAllFileItems(): LiveData<List<FileItem>> {
        return allFileItems
    }

    @Synchronized
    fun getCurrentFileItems(): LiveData<List<FileItem>> {
        return currentFileItems
    }

    @Synchronized
    fun insert(fileItem: FileItem) {
        InsertFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    @Synchronized
    fun update(fileItem: FileItem) {
        UpdateFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    @Synchronized
    fun delete(fileItem: FileItem) {
        DeleteFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    @Synchronized
    fun deleteAllFinished(folder: String) {
        DeleteAllFinishedFileItemAsyncTask(fileItemDao).execute(folder)
    }

    private fun initSelectedFolders() {
        val sharedPreferences = App.getContext().getSharedPreferences(ARG_DATA_STORE, Context.MODE_PRIVATE)
        val value = sharedPreferences.getString(ARG_SELECTED_FOLDERS, "")
        selectedFolders.value = if (value == null || value.isEmpty()) emptyList<String>() else value.split("\n")
    }

    @Synchronized
    fun getSelectedFolders(): List<String> {
        return selectedFolders.value ?: listOf()
    }

    @Synchronized
    fun updateSelectedFolders(folders: List<String>) {
        // need to update preference
        val editor = App.getContext().getSharedPreferences(ARG_DATA_STORE, Context.MODE_PRIVATE).edit()
        val value = folders.joinToString("\n")
        editor.putString(ARG_SELECTED_FOLDERS, value)
        editor.apply()
        selectedFolders.value = folders
    }

    companion object {

        class InsertFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.insert(fileItem)
                return null
            }
        }

        class UpdateFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.update(fileItem)
                return null
            }
        }

        class DeleteFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.delete(fileItem)
                return null
            }
        }

        class DeleteAllFinishedFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<String, Void?, Void?>() {
            override fun doInBackground(vararg params: String?): Void? {
                val folder = params[0]!!
                fileItemDao.deleteAllFinished(folder)
                return null
            }
        }
    }
}
