package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel

class MainViewModel(application: Application): AndroidViewModel(application) {

    val repository by lazy { App.getAppRepository() }
    val singleSelection = false

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
            var skip = false
            val lastSelectionList = repository.currentFileItems.value?.filter { it.selected } ?: listOf()
            lastSelectionList.forEach {
                if (it.id == fileItem.id) {
                    skip = true
                }
                else {
                    it.copy().apply {
                        selected = false
                        repository.update(this)
                    }
                }
            }
            if (!skip) {
                fileItem.copy().apply {
                    selected = true
                    repository.update(this)
                }
            }
        }
        else {
            fileItem.copy().apply {
                selected = !selected
                repository.update(this)
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
