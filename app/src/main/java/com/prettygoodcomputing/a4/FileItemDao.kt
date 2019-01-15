package com.prettygoodcomputing.a4

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*

@Dao
interface FileItemDao {

    @Insert
    fun insert(fileItem: FileItem)

    @Insert
    fun insertAll(vararg fileItem: FileItem)

    @Update
    fun update(fileItem: FileItem)

    @Update
    fun updateAll(vararg fileItem: FileItem)

    @Delete
    fun delete(fileItem: FileItem)

    @Delete
    fun deleteAll(vararg fileItem: FileItem)

    @Query("SELECT * FROM FileItem")
    fun getAll(): LiveData<List<FileItem>>
}
