package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.view.View

class MainViewModel(application: Application): AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    val repository by lazy { App.getAppRepository() }
    var singleSelection = true

    private lateinit var currentFileItem: FileItem
    val playerInfoVisibility = MutableLiveData<Int>().apply { value = View.VISIBLE }
    val playerInfoFile = MutableLiveData<String>()
    val playerInfoTime = MutableLiveData<String>()
    val playerProgressBarValue = MutableLiveData<Int>().apply { value = 0}
    val playerProgressBarMax = MutableLiveData<Int>().apply { value = 0}
    val playerInfoBar = MutableLiveData<String>()
    val playerInfoClock = MutableLiveData<String>()
    var videoWidth = 0
    var videoHeight = 0

    fun switchFolder(direction: Int) {
        repository.getAllFolderItems().value?.let { folderItems ->
            if (folderItems.size > 1) {
                val currentFolder = repository.getCurrentFolder()
                val oldIndex = folderItems.indexOfFirst { it.url == currentFolder }
                var newIndex = oldIndex + direction
                when {
                    newIndex < 0 -> newIndex = folderItems.size - 1
                    newIndex >= folderItems.size -> newIndex = 0
                }
                with (folderItems[newIndex]) {
                    repository.queryFileItems(url, sortBy)
                }
            }
        }
    }

    fun select(fileItem: FileItem): Int {
        var count = 1
        val updatedFileItems = mutableListOf<FileItem>()
        if (singleSelection) {
            var skip = false
            val lastSelectionList = getSelectedFileItems()
            lastSelectionList.forEach {
                if (it.id == fileItem.id) {
                    skip = true
                }
                else {
                    it.copy().apply {
                        selected = false
                        updatedFileItems.add(this)
                    }
                }
            }
            if (!skip) {
                fileItem.copy().apply {
                    selected = true
                    updatedFileItems.add(this)
                }
            }
        }
        else {
            count = getSelectedFileItems().size
            fileItem.copy().apply {
                selected = !selected
                count += if (selected) 1 else -1
                updatedFileItems.add(this)
            }
        }
        repository.update(updatedFileItems)
        return count
    }

    fun resetSelection() {
        // keep the first one selected, but reset the others
        if (!singleSelection) {
            val updatedFileItems = getSelectedFileItems().drop(1).map {
                it.copy().apply {
                    selected = false
                }
            }
            repository.update(updatedFileItems)
            singleSelection = true
        }
    }

    fun deleteSelected() {
        repository.delete(getSelectedFileItems())
        singleSelection = true
    }

    fun flagSelected() {
        getSelectedFileItems().forEach {
//            repository.delete(it)
        }
    }

    fun markSelectedAsFinished() {
        var first = true
        val updatedFileItems = getSelectedFileItems().map {
            it.copy().apply {
                selected = first
                finished = !finished
            }.also {
                first = false
            }
        }
        repository.update(updatedFileItems)
        singleSelection = true
    }

    private fun getSelectedFileItems(): List<FileItem> {
        return repository.currentFileItems.value?.filter { it.selected } ?: listOf()
    }

    fun updateSelectedFolders(folders: List<String>) {
        val oldFolderItems = repository.getAllFolderItems().value ?: listOf()
        val newFolderItems = mutableListOf<FolderItem>()
        var newPosition = 0
        folders.forEach {
            var oldFolder = oldFolderItems.find { folderItem -> folderItem.url == it }
            val newFolder = when (oldFolder) {
                null -> FolderItem(0, it, newPosition)
                else -> oldFolder.copy().apply { position = newPosition }
            }
            newFolderItems.add(newFolder)
            newPosition++
        }
        repository.updateFolderItems(newFolderItems)
    }

    fun refresh() {
        repository.refreshCurrentFolder()
    }

    fun deleteAllFinished() {
        val finishedFileItems = repository.currentFileItems.value?.filter { it.finished } ?: listOf()
        repository.delete(finishedFileItems)
    }

    fun sortBy(fieldName: String) {
        repository.getCurrentFolderItem()?.copy()?.let {
            it.sortBy = fieldName
            repository.update(it)
            repository.queryFileItems(it.url, fieldName)
        }
    }

    fun setCurrentFileItem(fileItem: FileItem) {
        currentFileItem = fileItem
        fileItem?.let {
            setPlayerInfoFile(it.name)
        }
        setVideoSize(0, 0)
    }

    fun getCurrentFileItem(): FileItem {
        return currentFileItem
    }

    fun setPlayerInfoVisibility(visibility: Int) {
        playerInfoVisibility.value = visibility
    }

    fun setPlayerInfoFile(info: String) {
        playerInfoFile.value = info
    }

    fun setPlayerInfoTime(info: String) {
        playerInfoTime.value = info
    }

    fun setProgressBarInfo(progress: Int, max: Int) {
        playerProgressBarValue.value = progress
        playerProgressBarMax.value = max
    }

    fun setPlayerInfoBar(info: String) {
        playerInfoBar.value = info
    }

    fun setPlayerInfoClock(info: String) {
        playerInfoClock.value = info
    }

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
    }
}
