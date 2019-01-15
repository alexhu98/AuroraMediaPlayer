package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.provider.DocumentFile
import com.crashlytics.android.Crashlytics
import java.net.URLDecoder

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

    @Synchronized
    fun getFileItem(url: String): FileItem {
        return allFileItems.value?.find { it.url == url }
            ?: FileItem(0, url, urlDecodedName(url), urlDecodeFolder(url))
    }

    private fun urlDecodedName(url: String): String {
        var name: String = URLDecoder.decode(url, "UTF-8")
        val tokens = name.split("/")
        return tokens[tokens.size - 1]
    }

    private fun urlDecodeFolder(url: String): String {
        return url.substringBefore("/document/")
    }

    private fun acceptExtension(ext: String): Boolean {
        return ext in setOf("m4", "mp4", "mp3", "m3")
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
        InsertFileItemAsyncTask().execute(fileItem)
    }

    @Synchronized
    fun update(fileItem: FileItem) {
        UpdateFileItemAsyncTask().execute(fileItem)
    }

    @Synchronized
    fun delete(fileItem: FileItem) {
        DeleteFileItemAsyncTask().execute(fileItem)
    }

    @Synchronized
    fun deleteAllFinished(folder: String) {
        DeleteAllFinishedFileItemAsyncTask().execute(folder)
    }

    @Synchronized
    fun getAllFolderItems(): LiveData<List<FolderItem>> {
        return allFolderItems
    }

    @Synchronized
    fun updateFolderItems(newFolders: List<FolderItem>) {
        UpdateAllFolderItemAsyncTask(getAllFolderItems().value, newFolders, getAllFileItems().value).execute()
    }

    private fun scanFolder(folder: String) {
        try {
            val uriTree = Uri.parse(folder)
            val documentFolder = DocumentFile.fromTreeUri(App.getContext(), uriTree)
            var listFiles = documentFolder?.listFiles()
            if (listFiles.isNullOrEmpty()) {
                // TODO: Clean up obsolete file items
            }
            else {
                val urlList = mutableListOf<String>()
                listFiles.forEach { file ->
                    val url = file.uri.toString()
                    val name = URLDecoder.decode(url, "UTF-8")
                    val ext = name.substringAfterLast(".").toLowerCase()
                    if (acceptExtension(ext)) {
                        urlList.add(url)
                        val fileItem = getFileItem(url)
                        if (fileItem.fileSize == 0L || fileItem.lastModified == 0L || fileItem.deleted) {
                            val fileItemCopy = fileItem.copy()
                            fileItemCopy.fileSize = file.length()
                            fileItemCopy.lastModified = file.lastModified()
                            fileItemCopy.deleted = false
                            fileItemCopy.finished = false
                            if (fileItemCopy.id == 0) {
                                fileItemDao.insert(fileItemCopy)
                            }
                            else {
                                fileItemDao.update(fileItemCopy)
                            }
                        }
                    }
                }
                // remove the obsolete ones that no longer exists
                allFileItems.value?.forEach {
                    if (it.folder == folder && !urlList.contains(it.url)) {
                        fileItemDao.delete(it)
                    }
                }
            }
        }
        catch (ex: Exception) {
            ex.printStackTrace()
            Crashlytics.logException(ex)
        }
    }

    inner class InsertFileItemAsyncTask(): AsyncTask<FileItem, Void?, Void?>() {
        override fun doInBackground(vararg params: FileItem?): Void? {
            params[0]?.let {
                fileItemDao.insert(it)
            }
            return null
        }
    }

    inner class UpdateFileItemAsyncTask(): AsyncTask<FileItem, Void?, Void?>() {
        override fun doInBackground(vararg params: FileItem?): Void? {
            params[0]?.let {
                fileItemDao.update(it)
            }
            return null
        }
    }

    inner class DeleteFileItemAsyncTask(): AsyncTask<FileItem, Void?, Void?>() {
        override fun doInBackground(vararg params: FileItem?): Void? {
            params[0]?.let {
                fileItemDao.delete(it)
            }
            return null
        }
    }

    inner class DeleteAllFinishedFileItemAsyncTask(): AsyncTask<String, Void?, Void?>() {
        override fun doInBackground(vararg params: String?): Void? {
            params[0]?.let {
                fileItemDao.deleteAllFinished(it)
            }
            return null
        }
    }

    inner class UpdateAllFolderItemAsyncTask(val oldFolderItems: List<FolderItem>?, val newFolderItems: List<FolderItem>, val oldFileItems: List<FileItem>?): AsyncTask<Void?, Void?, Void?>() {
        private val TAG = "UpdateAllFolderItemAsyncTask"

        override fun doInBackground(vararg params: Void?): Void? {
            val deleteFolders = mutableListOf<String>()
            val newFolders = mutableListOf<String>()
            val newIds = newFolderItems.map { it.id }
            // delete the ones that are no longer in the list
            oldFolderItems?.forEach {
                if (!newIds.contains(it.id)) {
                    deleteFolders.add(it.url)
                    folderItemDao.delete(it)
                }
            }
            // then update existing ones or insert new ones
            newFolderItems.forEach {
                when (it.id) {
                    0 -> {
                        folderItemDao.insert(it)
                        newFolders.add(it.url)
                    }
                    else -> folderItemDao.update(it)
                }
            }
            oldFileItems?.filter {
                deleteFolders.contains(it.folder)
            }?.forEach{
                Logger.v(TAG, "Deleting ${it.name}")
                fileItemDao.delete(it)
            }
            newFolders.forEach {
                scanFolder(it)
            }
            return null
        }
    }
}
