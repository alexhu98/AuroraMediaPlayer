package com.prettygoodcomputing.a4

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.crashlytics.android.Crashlytics
import java.util.HashMap


class MediaInfoRetriever() {

    private val TAG = "MediaInfoRetriever"

    private val mediaMetadataRetriever by lazy { MediaMetadataRetriever() }
    private var url: String = ""

    private val metadata: Map<String, String>
        get() {
            val metadata = HashMap<String, String>()
            try {
                val title = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val album = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val albumArtist = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val artist = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val author = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                val compilation = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                val trackNumber = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                val duration = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)

                metadata["title"] = title
                metadata["album"] = album
                metadata["artist"] = albumArtist
                metadata["author"] = author
                metadata["compilation"] = compilation
                metadata["trackNumber"] = trackNumber
                metadata["duration"] = duration

                Logger.v(TAG, "MediaInfoRetriever album = $album")
                Logger.v(TAG, "MediaInfoRetriever albumArtist = $albumArtist")
                Logger.v(TAG, "MediaInfoRetriever artist = $artist")
                Logger.v(TAG, "MediaInfoRetriever author = $author")
                Logger.v(TAG, "MediaInfoRetriever trackNumber = $trackNumber")
                Logger.v(TAG, "MediaInfoRetriever compilation = $compilation")
                Logger.v(TAG, "MediaInfoRetriever title = $title")
            }
            catch (ex: Exception) {
            }

            return metadata
        }

    private val duration: Long
        get() {
            var duration = 0L
            try {
                duration = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            }
            catch (ex: Exception) {
                Crashlytics.logException(ex)
            }
            return duration
        }

    private fun setDataSource(url: String) {
        this.url = url
        val uri = Uri.parse(url)
        try {
            mediaMetadataRetriever.setDataSource(App.getContext(), uri)
        }
        catch (ex: Exception) {
            Crashlytics.logException(ex)

            try {
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = uri
                App.getContext().sendBroadcast(mediaScanIntent)
            }
            catch (e: Exception) {
                Crashlytics.logException(e)
            }
        }
    }

    fun getDuration(url: String): Long {
        setDataSource(url)
        return duration
    }
}
