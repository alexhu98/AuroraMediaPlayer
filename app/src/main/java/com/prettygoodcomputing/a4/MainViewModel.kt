package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val allFileItems = repository.getFileItems()
    private val currentFileItems = MediatorLiveData<List<FileItem>>()

    val currentFolder = MutableLiveData<String>()
    val currentFolderInfo = MutableLiveData<String>()
    val currentOrderBy = MutableLiveData<String>()

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

    fun insert(fileItem: FileItem) {
        repository.insert(fileItem)
    }

    fun update(fileItem: FileItem) {
        repository.update(fileItem)
    }

    fun delete(fileItem: FileItem) {
        repository.delete(fileItem)
    }

    fun deleteAllFinished(folder: String) {
        repository.deleteAllFinished(folder)
    }

    fun getFileItems(): LiveData<List<FileItem>> {
        return currentFileItems
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

    fun onClickFileItem(fileItem: FileItem) {
        val fileItemCopy = fileItem.copy()
        fileItemCopy.fileSize *= 2
        update(fileItemCopy)
    }

    fun onLongClickFileItem(fileItem: FileItem) {
        delete(fileItem)
    }
}
