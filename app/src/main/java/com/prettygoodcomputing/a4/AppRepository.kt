package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
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

    val currentFolder = MutableLiveData<String>().apply { value = "" }
    val currentFolderInfo = MutableLiveData<String>().apply { value = "" }
    val currentOrderBy = MutableLiveData<String>().apply { value = FileItem.FIELD_NAME }
    val currentFileItems = MediatorLiveData<List<FileItem>>().apply { value = listOf() }

    private val fileItemDao = AppDatabase.getInstance(application).fileItemDao()
    private val folderItemDao = AppDatabase.getInstance(application).folderItemDao()
    private val allFileItems = fileItemDao.getAll()
    private val allFolderItems = folderItemDao.getAll()

    init {
        currentFileItems.addSource(allFileItems) {
            rearrangeAndCalculate()
        }
        if (getCurrentFolder().isNotEmpty()) {
            queryFileItems(getCurrentFolder(), getCurrentOrderBy())
        }
        allFolderItems.observeForever {
            if (!it.isNullOrEmpty() && currentFolder.value.isNullOrEmpty()) {
                queryFileItems(it[0].url, getCurrentOrderBy())
            }
        }
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
        currentFolderInfo.value = folderInfo
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
//            ?.filter{ it.folder == getCurrentFolder() }
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
                "$count, ${Formatter.formatFolderSize(totalSize)}"
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

    @Synchronized
    fun getAllFolderItems(): LiveData<List<FolderItem>> {
        return allFolderItems
    }

    @Synchronized
    fun updateFolderItems(newFolders: List<FolderItem>) {
        UpdateAllFolderItemAsyncTask(folderItemDao, getAllFolderItems().value, newFolders).execute()
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

        class UpdateAllFolderItemAsyncTask(val folderItemDao: FolderItemDao, val oldFolderItems: List<FolderItem>?, val newFolderItems: List<FolderItem>): AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg params: Void?): Void? {
                val newIds = newFolderItems.map { it.id }
                // delete the ones that are no longer in the list
                oldFolderItems?.forEach {
                    if (!newIds.contains(it.id)) {
                        folderItemDao.delete(it)
                    }
                }
                // then update existing ones or insert new ones
                newFolderItems.forEach {
                    when (it.id) {
                        0 -> folderItemDao.insert(it)
                        else -> folderItemDao.update(it)
                    }
                }
                return null
            }
        }
    }
}
