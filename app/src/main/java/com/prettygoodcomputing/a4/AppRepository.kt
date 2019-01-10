package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.os.AsyncTask

class AppRepository(val application: Application) {

    val currentFolder = MutableLiveData<String>()
    val currentFolderInfo = MutableLiveData<String>()
    val currentOrderBy = MutableLiveData<String>()
    val currentFileItems = MediatorLiveData<List<FileItem>>()

    private val fileItemDao = AppDatabase.getInstance(application).fileItemDao()
    private val allFileItems = fileItemDao.getFileItems()

    init {
        setCurrentFolder("")
        setCurrentFolderInfo("")
        setCurrentOrderBy(FileItem.FIELD_NAME)
        queryFileItems("/x", FileItem.FIELD_NAME)
        currentFileItems.addSource(allFileItems) {
            currentFileItems.value = rearrangeFileItems(it)
        }
    }

    fun getCurrentFolder(): String {
        return currentFolder.value ?: ""
    }

    fun setCurrentFolder(folder: String) {
        currentFolder.value = folder
    }

    fun getCurrentFolderInfo(): String {
        return currentFolderInfo.value ?: ""
    }

    fun setCurrentFolderInfo(folderInfo: String) {
        currentFolderInfo.value = folderInfo
    }

    fun getCurrentOrderBy(): String {
        return currentOrderBy.value ?: ""
    }

    fun setCurrentOrderBy(orderBy: String) {
        currentOrderBy.value = orderBy
    }

    fun queryFileItems(folder: String, orderBy: String) {
        if (getCurrentFolder() != folder || getCurrentOrderBy() != orderBy) {
            setCurrentFolder(folder)
            setCurrentOrderBy(orderBy)
            allFileItems.value?.let {
                currentFileItems.value = rearrangeFileItems(it)
            }
        }
    }

    private fun rearrangeFileItems(all: List<FileItem>?): List<FileItem> {
        val comparator: Comparator<FileItem> = when (getCurrentOrderBy()) {
            FileItem.FIELD_NAME -> compareBy({ it.name }, { it.fileSize })
            FileItem.FIELD_FILE_SIZE -> compareBy({ -it.fileSize }, { it.name })
            FileItem.FIELD_LAST_MODIFIED -> compareBy({ -it.lastModified }, { it.name })
            else -> compareBy({ it.name }, { it.fileSize })
        }
        val result = all
            ?.filter{ it.folder == getCurrentFolder() }
            ?.sortedWith(comparator)
            ?: listOf()
        return result
    }

    fun switchFolder(direction: Int) {
        val newFolder = if (getCurrentFolder() == "/t") "/x" else "/t"
        setCurrentFolderInfo("")
        queryFileItems(newFolder, FileItem.FIELD_NAME)
    }

    fun calculateFolderInfo() {
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

    fun getFileItems(): LiveData<List<FileItem>> {
        return allFileItems
    }

    fun insert(fileItem: FileItem) {
        InsertFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun update(fileItem: FileItem) {
        UpdateFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun delete(fileItem: FileItem) {
        DeleteFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun deleteAllFinished(folder: String) {
        DeleteAllFinishedFileItemAsyncTask(fileItemDao).execute(folder)
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
