package com.prettygoodcomputing.a4

import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey

@Entity
data class FileItem(
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    var name: String = "",
    var folder: String = "",
    var fileSize: Long = 0L,
    var position: Long = 0L,
    var duration: Long = 0L,
    var lastModified: Long = 0L,
    var finished: Boolean = false,
    var hasSubtitle: Boolean = false,
    var deleted: Boolean = false,
    var error: Boolean = false,
    var bookmarks: String = "",
    @Ignore var selected: Boolean = false
) {

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_FILE_SIZE = "fileSize"
        const val FIELD_LAST_MODIFIED = "lastModified"
    }
}
