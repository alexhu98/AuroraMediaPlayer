package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    private val repository by lazy { App.getAppRepository() }

    val currentFolder by lazy { repository.currentFolder }
    val currentFolderInfo by lazy { repository.currentFolderInfo }
    val currentOrderBy by lazy { repository.currentOrderBy }
    val currentFileItems by lazy { repository.currentFileItems }

    fun getCurrentFolder(): String {
        return repository.getCurrentFolder()
    }

    fun setCurrentFolder(folder: String) {
        return repository.setCurrentFolder(folder)
    }

    fun getCurrentFolderInfo(): String {
        return repository.getCurrentFolderInfo()
    }

    fun setCurrentFolderInfo(folderInfo: String) {
        return repository.setCurrentFolder(folderInfo)
    }

    fun getCurrentOrderBy(): String {
        return repository.getCurrentOrderBy()
    }

    fun setCurrentOrderBy(orderBy: String) {
        repository.setCurrentOrderBy(orderBy)
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
        repository.queryFileItems(folder, orderBy)
    }

    fun switchFolder(direction: Int) {
        repository.switchFolder(direction)
    }

    fun calculateFolderInfo() {
        repository.calculateFolderInfo()
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
