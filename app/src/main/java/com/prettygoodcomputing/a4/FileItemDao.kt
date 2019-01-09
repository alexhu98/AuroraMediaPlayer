package com.prettygoodcomputing.a4

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*

@Dao
interface FileItemDao {

    @Insert
    fun insert(fileItem: FileItem)

    @Update
    fun update(fileItem: FileItem)

    @Delete
    fun delete(fileItem: FileItem)

    @Query("DELETE FROM FileItem WHERE folder = :folder AND finished")
    fun deleteAllFinished(folder: String)

    @Query("SELECT * FROM FileItem")
    fun getFileItems(): LiveData<List<FileItem>>
}
