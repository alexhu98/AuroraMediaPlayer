package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    val repository by lazy { App.getAppRepository() }
    val selectedItems = MutableLiveData<List<Int>>().apply { value = listOf() }
    val singleSelection = true

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
                val newFolder = folderItems[newIndex].url
                repository.queryFileItems(newFolder, FileItem.FIELD_NAME)
            }
        }
    }

    fun select(fileItem: FileItem) {
        if (singleSelection) {
            selectedItems.value = listOf(fileItem.id)
        }
        else {
            (selectedItems.value ?: listOf()).toMutableList().apply {
                when (contains(fileItem.id)) {
                    true -> remove(fileItem.id)
                    false -> add(fileItem.id)
                }
                selectedItems.value = this
            }
        }
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

    fun onLongClickFileItem(fileItem: FileItem) {
        repository.delete(fileItem)
    }
}
