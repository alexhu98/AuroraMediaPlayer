package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    val repository by lazy { App.getAppRepository() }
    val selectedItems = MutableLiveData<List<Int>>().apply { value = listOf() }
    val singleSelection = true

    fun switchFolder(direction: Int) {
        val selectedFolders = repository.getSelectedFolders()
        if (selectedFolders.size > 1) {
            val currentFolder = repository.getCurrentFolder()
            val oldIndex = selectedFolders.indexOf(currentFolder)
            var newIndex = oldIndex + direction
            when {
                newIndex < 0 -> newIndex = selectedFolders.size - 1
                newIndex >= selectedFolders.size -> newIndex = 0
            }
            val newFolder = selectedFolders[newIndex]
            repository.queryFileItems(newFolder, FileItem.FIELD_NAME)
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

    fun onLongClickFileItem(fileItem: FileItem) {
        repository.delete(fileItem)
    }
}
