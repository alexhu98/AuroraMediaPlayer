package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class SelectedFoldersViewModel(application: Application): AndroidViewModel(application) {

    private val repository by lazy { App.getAppRepository() }
    val selectedFolders = MutableLiveData<List<FolderItem>>().apply {
        value = repository.getAllFolderItems().value?.map { it.copy() }
                ?: listOf()
    }

    fun insert(folder: String) {
        (selectedFolders.value ?: listOf()).toMutableList().apply {
            val folderItem = find {it.url == folder }
            if (folderItem == null) {
                add(FolderItem(0, folder))
                selectedFolders.value = this
            }
        }
    }

    fun delete(folder: String) {
        (selectedFolders.value ?: listOf()).toMutableList().apply {
            val folderItem = find {it.url == folder }
            if (folderItem != null) {
                remove(folderItem)
                selectedFolders.value = this
            }
        }
    }

    fun select(folder: String) {
        (selectedFolders.value ?: listOf()).toMutableList().apply {
            val oldFolderItem = find { it.selected }
            val newFolderItem = find { it.url == folder }
            if (oldFolderItem != newFolderItem) {
                if (oldFolderItem != null) {
                    this[indexOf(oldFolderItem)] = oldFolderItem.copy().apply { selected = false }
                }
                if (newFolderItem != null) {
                    this[indexOf(newFolderItem)] = newFolderItem.copy().apply { selected = true }
                }
                selectedFolders.value = this
            }
        }
    }

    fun swap(fromPosition: Int, toPosition: Int) {
        val selectedFolderList = selectedFolders.value
        if (selectedFolderList != null) {
            val newList = selectedFolderList.toMutableList()
            val fromItem = newList[fromPosition]
            val toItem = newList[toPosition]
            newList[fromPosition] = toItem
            newList[toPosition] = fromItem
            selectedFolders.value = newList
        }
    }
}
