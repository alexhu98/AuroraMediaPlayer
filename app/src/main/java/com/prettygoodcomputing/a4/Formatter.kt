package com.prettygoodcomputing.a4

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import com.google.android.exoplayer2.C
import java.net.URLDecoder


class Formatter {

    private val TAG = "Formatter"

    companion object {

        @JvmStatic
        fun formatFileSize(fileSize: Long): String {
            if (fileSize == 0L) {
                return ""
            }
            return "%dM".format(fileSize / 1024 / 1024)
        }

        @JvmStatic
        fun formatTime(time: Long): String {
            if (time == 0L || time == C.TIME_UNSET) {
                return ""
            }
            val seconds = time / 1000L % 60
            val minutes = (time / 60000L) % 60
            val hours = time / 3600000
            if (hours > 0) {
                return "%d:%02d:%02d".format(hours, minutes, seconds)
            }
            return "%d:%02d".format(minutes, seconds)
        }

        @JvmStatic
        fun formatFolderSize(folderSize: Long): String {
            return "%.1fG".format(folderSize / 1024.0 / 1024.0 / 1024.0)
        }

        @JvmStatic
        fun formatFolderName(url: String): String {
            var name = URLDecoder.decode(url, "UTF-8").substringAfterLast("/")
            val tokens = name.split(":")
            if (tokens.size > 1) {
                name = tokens[1]
            }
            return name
        }

        @JvmStatic
        fun formatFileName(url: String): String {
            val name = URLDecoder.decode(url, "UTF-8")
            return name.substringAfterLast("/").substringBeforeLast(".")
        }

        @JvmStatic
        fun formatFileInfo(fileItem: FileItem): String {
            return formatFileSize(fileItem.fileSize) + "\n" + formatTime(fileItem.position) + " " + formatTime(fileItem.duration)
        }

        @JvmStatic
        fun formatProgress(fileItem: FileItem): String {
            var id = R.string.progress_none
            with(fileItem) {
                if (finished) {
                    id = R.string.progress_100
                } else if (position > 0 && duration > 0) {
                    val progress = position * 100 / duration
                    id = when {
                        progress >= 75 -> R.string.progress_75
                        progress >= 50 -> R.string.progress_50
                        progress >= 25 -> R.string.progress_25
                        position > 0 -> R.string.progress_1
                        else -> R.string.progress_0
                    }
                } else {
                    id = R.string.progress_none
                }
            }
            return App.getContext().resources.getString(id)
        }
    }
}
