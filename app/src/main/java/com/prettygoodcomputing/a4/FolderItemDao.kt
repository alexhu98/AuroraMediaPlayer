package com.prettygoodcomputing.a4

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*

@Dao
interface FolderItemDao {

    @Insert
    fun insert(selectedFolder: FolderItem)

    @Update
    fun update(selectedFolder: FolderItem)

    @Delete
    fun delete(selectedFolder: FolderItem)

    @Query("SELECT * FROM FolderItem ORDER BY position")
    fun getAll(): LiveData<List<FolderItem>>
}
