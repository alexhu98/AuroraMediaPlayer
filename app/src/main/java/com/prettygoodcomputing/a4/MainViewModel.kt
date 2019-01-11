package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData

class MainViewModel(application: Application): AndroidViewModel(application) {

    val repository by lazy { App.getAppRepository() }

    fun switchFolder(direction: Int) {
        val newFolder = if (repository.getCurrentFolder() == "/t") "/x" else "/t"
        repository.queryFileItems(newFolder, FileItem.FIELD_NAME)
    }

    fun onClickFileItem(fileItem: FileItem) {
        val fileItemCopy = fileItem.copy()
        fileItemCopy.fileSize *= 2
        repository.update(fileItemCopy)
    }

    fun onLongClickFileItem(fileItem: FileItem) {
        repository.delete(fileItem)
    }
}
