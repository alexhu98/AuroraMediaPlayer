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

    val currentFileName = MutableLiveData<String>().apply { value = "" }
    val currentContentUrl = MutableLiveData<String>().apply { value = "" }
    val currentFolder = MutableLiveData<String>().apply { value = "" }
    val currentFolderInfo = MutableLiveData<String>().apply { value = "" }
    val currentSortBy = MutableLiveData<String>().apply { value = FileItem.FIELD_NAME }
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
            queryFileItems(getCurrentFolder(), getCurrentSortBy())
        }
        allFolderItems.observeForever {
            if (!it.isNullOrEmpty() && currentFolder.value.isNullOrEmpty()) {
                queryFileItems(it[0].url, getCurrentSortBy())
            }
        }
    }

    @Synchronized
    fun getCurrentFileName(): String {
        return currentFileName.value ?: ""
    }

    @Synchronized
    fun setCurrentFileName(name: String) {
        currentFileName.value = name
    }

    @Synchronized
    fun getCurrentContentUrl(): String {
        return currentContentUrl.value ?: ""
    }

    @Synchronized
    fun setCurrentContentUrl(url: String) {
        currentContentUrl.value = url
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
    fun refreshCurrentFolder() {
        currentFolder.value?.let {
            RefreshFolderAsyncTask().execute(it)
        }
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
    fun getCurrentSortBy(): String {
        return currentSortBy.value ?: ""
    }

    @Synchronized
    fun setCurrentSortBy(orderBy: String) {
        currentSortBy.value = orderBy
    }

    @Synchronized
    fun queryFileItems(folder: String, sortBy: String) {
        if (getCurrentFolder() != folder || getCurrentSortBy() != sortBy) {
            setCurrentFolder(folder)
            setCurrentSortBy(sortBy)
            rearrangeAndCalculate()
        }
    }

    @Synchronized
    fun getFileItem(url: String): FileItem {
        return allFileItems.value?.find { it.url == url }
            ?: FileItem(0, url, urlDecodedName(url), urlDecodeFolder(url))
    }

    @Synchronized
    fun getCurrentFileItem(): FileItem {
        return getFileItem(getCurrentContentUrl())
    }


    private fun urlDecodedName(url: String): String {
        val name: String = URLDecoder.decode(url, "UTF-8")
        return name.substringAfterLast("/").substringBeforeLast(".")
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
        val comparator: Comparator<FileItem> = when (getCurrentSortBy()) {
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
    fun update(fileItem: FileItem) {
        update(listOf(fileItem))
    }

    @Synchronized
    fun update(fileItems: List<FileItem>) {
        if (fileItems.isNotEmpty()) {
            InsertOrUpdateFileItemAsyncTask().execute(fileItems)
        }
    }

//    @Synchronized
//    fun delete(fileItem: FileItem) {
//        delete(listOf(fileItem))
//    }

    @Synchronized
    fun delete(fileItems: List<FileItem>) {
        if (fileItems.isNotEmpty()) {
            DeleteFileItemAsyncTask().execute(fileItems)
        }
    }

    @Synchronized
    fun getFolderItem(folder: String): FolderItem? {
        return allFolderItems.value?.find { it.url == folder }
    }

    @Synchronized
    fun getCurrentFolderItem(): FolderItem? {
        return getFolderItem(getCurrentFolder())
    }


    @Synchronized
    fun getAllFolderItems(): LiveData<List<FolderItem>> {
        return allFolderItems
    }

    @Synchronized
    fun update(folderItem: FolderItem) {
        UpdateFolderItemAsyncTask().execute(folderItem)
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
                // first look for new files
                val mediaInfoRetriever = MediaInfoRetriever()
                val urlList = mutableListOf<String>()
                val updatedFileItems = mutableListOf<FileItem>()
                listFiles.forEach { file ->
                    val url = file.uri.toString()
                    val name = URLDecoder.decode(url, "UTF-8")
                    val ext = name.substringAfterLast(".").toLowerCase()
                    if (acceptExtension(ext)) {
                        urlList.add(url)
                        val fileItem = getFileItem(url)
                        if (fileItem.fileSize == 0L || fileItem.lastModified == 0L || fileItem.deleted || fileItem.duration == 0L) {
                            fileItem.copy().apply {
                                fileSize = file.length()
                                lastModified = file.lastModified()
                                deleted = false
                                finished = false
                                if (duration == 0L) {
                                    duration = mediaInfoRetriever.getDuration(url)
                                    if (duration == 0L) {
                                        error = true
                                    }
                                }
                                updatedFileItems.add(this)
                            }
                        }
                    }
                }
                update(updatedFileItems)

                // remove the obsolete ones that no longer exists
                val deletedFileItems = mutableListOf<FileItem>()
                allFileItems.value?.forEach {
                    if (it.folder == folder && !urlList.contains(it.url)) {
                        deletedFileItems.add(it)
                    }
                }
                delete(deletedFileItems)
            }
        }
        catch (ex: Exception) {
            ex.printStackTrace()
            Crashlytics.logException(ex)
        }
    }

    inner class InsertOrUpdateFileItemAsyncTask(): AsyncTask<List<FileItem>, Void?, Void?>() {
        override fun doInBackground(vararg params: List<FileItem>): Void? {
            var fileItems = params[0]
            val insertList = fileItems.filter { it.id == 0 }
            val updateList = fileItems.filter { it.id != 0 }
            if (insertList.isNotEmpty()) {
                fileItemDao.insertAll(*insertList.toTypedArray())
            }
            if (updateList.isNotEmpty()) {
                fileItemDao.updateAll(*updateList.toTypedArray())
            }
            return null
        }
    }

    inner class DeleteFileItemAsyncTask(): AsyncTask<List<FileItem>, Void?, Void?>() {
        override fun doInBackground(vararg params: List<FileItem>): Void? {
            val fileItems = params[0]
            fileItemDao.deleteAll(*fileItems.toTypedArray())
            return null
        }
    }

    inner class UpdateFolderItemAsyncTask(): AsyncTask<FolderItem, Void?, Void?>() {
        override fun doInBackground(vararg params: FolderItem): Void? {
            folderItemDao.update(params[0])
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

    inner class RefreshFolderAsyncTask(): AsyncTask<String, Void?, Void?>() {
        override fun doInBackground(vararg params: String): Void? {
            scanFolder(params[0])
            return null
        }
    }
}
