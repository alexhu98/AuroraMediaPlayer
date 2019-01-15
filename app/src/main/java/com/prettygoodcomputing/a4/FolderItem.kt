package com.prettygoodcomputing.a4

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey

@Entity
data class FolderItem(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var url: String = "",
    var position: Int = 0,
    var sortBy: String = FileItem.FIELD_NAME,
    @Ignore var selected: Boolean = false
) {

}
