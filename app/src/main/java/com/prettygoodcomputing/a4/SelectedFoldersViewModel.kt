package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class SelectedFoldersViewModel(application: Application): AndroidViewModel(application) {

    private val repository by lazy { App.getAppRepository() }

    val selectedFolders = MutableLiveData<List<String>>().apply { value = repository.selectedFolders.value }
    var selectedItem = MutableLiveData<String>().apply { value = "" }
    var lastSelectedItem = ""

    fun insert(folder: String) {
        (selectedFolders.value ?: listOf()).toMutableList().apply {
            if (!contains(folder)) {
                add(folder)
                selectedFolders.value = this
            }
        }
    }

    fun delete(folder: String) {
        (selectedFolders.value ?: listOf()).toMutableList().apply {
            if (contains(folder)) {
                remove(folder)
                selectedFolders.value = this
            }
        }
    }

    fun select(folder: String) {
        lastSelectedItem = selectedItem.value ?: ""
        selectedItem.value = if (selectedItem.value != folder) folder else ""
    }

    fun swap(fromItem: String, fromPosition: Int, toItem: String, toPosition: Int) {
        val selectedFolderList = selectedFolders.value
        if (selectedFolderList != null) {
            val newList = selectedFolderList.toMutableList()
            newList[fromPosition] = toItem
            newList[toPosition] = fromItem
            selectedFolders.value = newList
        }
    }
}
