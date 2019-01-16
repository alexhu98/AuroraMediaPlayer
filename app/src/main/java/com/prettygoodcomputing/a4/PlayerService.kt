package com.prettygoodcomputing.a4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

class PlayerService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private val context by lazy { this }

    private val APPLICATION_NAME = "A4"
    private val PLAYER_SERVICE_NOTIFICATION_ID = 101
    private val CHANNEL_PLAYER_SERVICE_NAME = "A4"
    private val CHANNEL_PLAYER_SERVICE = "com.prettygoodcomputing.a4.player_service"
    private val MEDIA_ID_ROOT = "com.prettygoodcomputing.a4.PlayerService.Root"
    private val MEDIA_ID_EMPTY_ROOT = "com.prettygoodcomputing.a4.PlayerService.EmptyRoot"

    private lateinit var player: SimpleExoPlayer
    private var playerCreated = false
//    private lateinit var playerNotificationManager: PlayerNotificationManager
//    private lateinit var mediaSessionConnector: MediaSessionConnector

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, APPLICATION_NAME)
        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(MediaSessionCallback())
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
            MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
        )

        createPlayer()
    }

    override fun onDestroy() {
        releasePlayer()
    }

    private fun allowBrowsing(clientPackageName: String, clientUid: Int): Boolean {
        return true
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
//        Toast.makeText(applicationContext, "MBS: onGetRoot $clientPackageName", Toast.LENGTH_LONG).show()

        // (Optional) Control the level of access for the specified package name.
        // You'll need to write your own logic to do this.
        return if (allowBrowsing(clientPackageName, clientUid)) {
            // Returns a root ID that clients can use with onLoadChildren() to retrieve
            // the content hierarchy.
            MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null)
        } else {
            // Clients can connect, but this BrowserRoot is an empty hierachy
            // so onLoadChildren returns nothing. This disables the ability to browse for content.
            MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null)
        }
    }

    override fun onLoadChildren(parentMediaId: String, result: Result<MutableList<MediaItem>>) {
        // use result.detach to async load the children
//        result.detach()

        val mediaItems = arrayListOf<MediaBrowserCompat.MediaItem>()
        if (TextUtils.equals(MEDIA_ID_EMPTY_ROOT, parentMediaId)) {
        }
        else {
            val repository = App.getAppRepository()
            val fileItems = repository.getCurrentFileItems().value ?: listOf()
            fileItems.forEachIndexed { index, fileItem ->
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, fileItem.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, fileItem.url)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, fileItem.url)
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (index + 1).toLong())
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, fileItems.size.toLong())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, fileItem.duration)
                    .build()
                mediaItems.add(MediaBrowserCompat.MediaItem(metadata.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
        }
        result.sendResult(mediaItems)
    }

    private fun localBroadcast(callBackName: String, broadcastIntent: Intent): Boolean {
        broadcastIntent.action = PlayerService.ACTION_MEDIA_SESSION_CALL_BACK
        broadcastIntent.putExtra(PlayerService.PARAM_CALL_BACK, callBackName)
        val broadcasted = LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
        return broadcasted
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val broadcastIntent = Intent()
            broadcastIntent.putExtra(PlayerService.PARAM_MEDIA_ID, mediaId)
            localBroadcast("onPlayFromMediaId", broadcastIntent)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val broadcastIntent = Intent()
            if (query != null) {
                broadcastIntent.putExtra(PlayerService.PARAM_QUERY, query)
            }
            localBroadcast("onPlayFromSearch", broadcastIntent)
        }

        override fun onPlay() {
            localBroadcast("onPlay", Intent())
        }

        override fun onPause() {
            localBroadcast("onPause", Intent())
        }

        override fun onStop() {
            localBroadcast("onStop", Intent())
        }

        override fun onSeekTo(position: Long) {
            val broadcastIntent = Intent()
            broadcastIntent.putExtra(PlayerService.PARAM_POSITION, position)
            localBroadcast("onSeekTo", broadcastIntent)
        }

        override fun onSkipToQueueItem(queueId: Long) {
            val broadcastIntent = Intent()
            broadcastIntent.putExtra(PlayerService.PARAM_QUEUE_ID, queueId)
            localBroadcast("onSkipToQueueItem", broadcastIntent)
        }

        override fun onSkipToNext() {
            localBroadcast("onSkipToNext", Intent())
        }

        override fun onSkipToPrevious() {
            localBroadcast("onSkipToPrevious", Intent())
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {}
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var channel = NotificationChannel(CHANNEL_PLAYER_SERVICE, CHANNEL_PLAYER_SERVICE_NAME, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel);
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            when (intent.action) {
                "Hello" -> {}
                else -> {
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
            }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_PLAYER_SERVICE)
        builder.setContentTitle(APPLICATION_NAME)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(resources.getColor(R.color.colorPrimaryDark, theme))
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
            )
        val notification = builder.build()
        startForeground(PLAYER_SERVICE_NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createPlayer() {
        if (!playerCreated) {
            playerCreated = true
            player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector())
            mediaSession.isActive = true

            val commonActions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_PLAY_FROM_URI or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
            val stateBuilder = PlaybackStateCompat.Builder()
            stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE or commonActions)
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 123, 1.0f)
            mediaSession.setPlaybackState(stateBuilder.build())

/*
                    playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(context, CHANNEL_PLAYER_SERVICE, R.string.app_name, PLAYER_SERVICE_NOTIFICATION_ID,
                object: PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun createCurrentContentIntent(player: Player?): PendingIntent? {
                        val intent = Intent(context, MainActivity::class.java)
                        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                    }

                    override fun getCurrentContentTitle(player: Player?): String {
                        val windowIndex = player?.currentWindowIndex ?: -1
                        val title = when (windowIndex) {
                            -1 -> "Player is null"
                            0, 2, 4, 6 -> "mpthreetest.mp3 " + windowIndex.toString()
                            else -> "crowd-cheering.mp3 " + windowIndex.toString()
                        }
                        return title
                    }

                    override fun getCurrentContentText(player: Player?): String? {
                        return null
                    }

                    override fun getCurrentLargeIcon(player: Player?, callback: PlayerNotificationManager.BitmapCallback?): Bitmap? {
                        return null
                    }
                })

            playerNotificationManager.setNotificationListener(object: PlayerNotificationManager.NotificationListener {
                override fun onNotificationStarted(notificationId: Int, notification: Notification?) {
                    startForeground(notificationId, notification)
                }

                override fun onNotificationCancelled(notificationId: Int) {
                    stopSelf()
                }
            })
            playerNotificationManager.setColor(R.color.colorPrimaryDark)
            playerNotificationManager.setSmallIcon(R.mipmap.ic_launcher)
*/
//            playerNotificationManager.setPlayer(player)

//            mediaSessionConnector = MediaSessionConnector(mediaSession)

//            mediaSessionConnector.setQueueNavigator(object: TimelineQueueNavigator(mediaSession) {
//
//                override fun getMediaDescription(player: Player?, windowIndex: Int): MediaDescriptionCompat {
//                    val title = when (windowIndex) {
//                        0, 2, 4, 6 -> "mpthreetest.mp3 " + windowIndex.toString()
//                        else -> "crowd-cheering.mp3 " + windowIndex.toString()
//                    }
//                    val mediaDescription = MediaDescriptionCompat.Builder()
//                        .setMediaId(title)
//                        .setTitle(title)
//                        .setDescription(title)
//                        .build()
//                    return mediaDescription
//
//                }
//            })
//            mediaSessionConnector.setPlayer(player, null)
        }
    }

    private fun startPlayer() {
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, APPLICATION_NAME))
        val concatenatingMediaSource = ConcatenatingMediaSource()
        val SAMPLES = arrayOf(
            "https://archive.org/download/testmp3testfile/mpthreetest.mp3",
            "https://sample-videos.com/audio/mp3/crowd-cheering.mp3",
            "https://archive.org/download/testmp3testfile/mpthreetest.mp3",
            "https://sample-videos.com/audio/mp3/crowd-cheering.mp3",
            "https://archive.org/download/testmp3testfile/mpthreetest.mp3",
            "https://sample-videos.com/audio/mp3/crowd-cheering.mp3",
            "https://archive.org/download/testmp3testfile/mpthreetest.mp3",
            "https://sample-videos.com/audio/mp3/crowd-cheering.mp3"
        )
        SAMPLES.forEach { url ->
            val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url))
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        player.prepare(concatenatingMediaSource)
//        player.playWhenReady = true
        player.playWhenReady = false
    }

    private fun releasePlayer() {
        if (playerCreated) {
//            playerNotificationManager.setPlayer(null)
            player.playWhenReady = false
            player.stop()
            player.release()
            playerCreated = false
//            mediaSessionConnector.setPlayer(null, null)
//            mediaSession.isActive = false
//            mediaSession.release()
        }

    }

    companion object {
        const val ACTION_MEDIA_SESSION_CALL_BACK = "com.prettygoodcomputing.a4.PlayerService.ACTION_MEDIA_SESSION_CALL_BACK"
        const val PARAM_CALL_BACK = "com.prettygoodcomputing.a4.PlayerService.PARAM_CALL_BACK"
        const val PARAM_MEDIA_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_MEDIA_ID"
        const val PARAM_POSITION = "com.prettygoodcomputing.a4.PlayerService.PARAM_POSITION"
        const val PARAM_QUERY = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUERY"
        const val PARAM_QUEUE_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUEUE_ID"
    }
}
