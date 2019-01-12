package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData

class SelectedFoldersViewModel(application: Application): AndroidViewModel(application) {

    private val repository by lazy { App.getAppRepository() }

    val selectedFolders = MutableLiveData<List<String>>()

    init {
        selectedFolders.value = repository.selectedFolders.value
    }

    fun insert(folder: String) {
        val selectedFolderList = selectedFolders.value
        if (selectedFolderList != null && !selectedFolderList.contains(folder)) {
            val newSelectedFolders = selectedFolderList.toMutableList()
            newSelectedFolders.add(folder)
            selectedFolders.value = newSelectedFolders
        }
    }

    fun swap(fromItem: String, fromPosition: Int, toItem: String, toPosition: Int) {
        val selectedFolderList = selectedFolders.value
        if (selectedFolderList != null) {
            val newSelectedFolders = selectedFolderList.toMutableList()
            newSelectedFolders[fromPosition] = toItem
            newSelectedFolders[toPosition] = fromItem
            selectedFolders.value = newSelectedFolders
        }
    }

    fun onClickSelectedFolder(folder: String) {
    }
}
