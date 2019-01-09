package com.prettygoodcomputing.a4

import android.app.Application
import android.arch.lifecycle.LiveData
import android.os.AsyncTask

class AppRepository(val application: Application) {

    private val fileItemDao = AppDatabase.getInstance(application).fileItemDao()
    private val allFileItems = fileItemDao.getFileItems()

    fun getFileItems(): LiveData<List<FileItem>> {
        return allFileItems
    }

    fun insert(fileItem: FileItem) {
        InsertFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun update(fileItem: FileItem) {
        UpdateFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun delete(fileItem: FileItem) {
        DeleteFileItemAsyncTask(fileItemDao).execute(fileItem)
    }

    fun deleteAllFinished(folder: String) {
        DeleteAllFinishedFileItemAsyncTask(fileItemDao).execute(folder)
    }

    companion object {

        class InsertFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.insert(fileItem)
                return null
            }
        }

        class UpdateFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.update(fileItem)
                return null
            }
        }

        class DeleteFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<FileItem, Void?, Void?>() {
            override fun doInBackground(vararg params: FileItem?): Void? {
                val fileItem = params[0]!!
                fileItemDao.delete(fileItem)
                return null
            }
        }

        class DeleteAllFinishedFileItemAsyncTask(val fileItemDao: FileItemDao): AsyncTask<String, Void?, Void?>() {
            override fun doInBackground(vararg params: String?): Void? {
                val folder = params[0]!!
                fileItemDao.deleteAllFinished(folder)
                return null
            }
        }
    }
}
