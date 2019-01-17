package com.prettygoodcomputing.a4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

class PlayerService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private val context by lazy { this }

    private val PLAYER_SERVICE_NOTIFICATION_ID = 101
    private val REQUEST_LAUNCH_APP = 201
    private val CHANNEL_PLAYER_SERVICE_NAME = "A4"
    private val CHANNEL_PLAYER_SERVICE = "com.prettygoodcomputing.a4.player_service"
    private val MEDIA_ID_ROOT = "com.prettygoodcomputing.a4.PlayerService.Root"
    private val MEDIA_ID_EMPTY_ROOT = "com.prettygoodcomputing.a4.PlayerService.EmptyRoot"

    private val playerController by lazy { PlayerController(context, "PS.PlayerController") }
    private val player by lazy { playerController.getPlayer() }
    private var localBroadcastReceiver: BroadcastReceiver? = null
    private var mediaPlaybackState = PlaybackStateCompat.STATE_NONE

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        createMediaSession()
        createPlayer()
        registerLocalBroadcastReceiver()
    }

    override fun onDestroy() {
        releasePlayer()
        playerController.release()
        unregisterLocalBroadcastReceiver()
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

    private fun localBroadcast(callbackName: String, broadcastIntent: Intent): Boolean {
        broadcastIntent.action = PlayerService.ACTION_MEDIA_SESSION_CALLBACK
        broadcastIntent.putExtra(PlayerService.PARAM_CALLBACK, callbackName)
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

    private fun registerLocalBroadcastReceiver() {
        localBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    when (intent.action) {
                        PlayerService.ACTION_PLAYER_CALLBACK -> {
                            val callbackName = intent.extras?.getString(PlayerService.PARAM_CALLBACK) ?: ""
                            val playbackState = intent.extras?.getInt(PlayerService.PARAM_PLAYBACK_STATE, PlaybackStateCompat.STATE_NONE) ?: PlaybackStateCompat.STATE_NONE
                            val position = intent.extras?.getLong(PlayerService.PARAM_POSITION, 0L) ?: 0L

//                            Toast.makeText(context, "PS  $playbackState", Toast.LENGTH_LONG).show()
                            when (callbackName) {
                                "onPlaybackState" -> setMediaPlaybackState(playbackState, position)
                            }
                        }
                    }
                }
            }
        }
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).registerReceiver(it, IntentFilter(ACTION_PLAYER_CALLBACK))
        }
    }

    private fun unregisterLocalBroadcastReceiver() {
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
        }
        localBroadcastReceiver = null
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(context, context.resources.getString(R.string.app_name))
        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(MediaSessionCallback())
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                    MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
        )
        mediaSession.isActive = true
    }

    private fun setMediaPlaybackState(state: Int, position: Long = 0) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(state, CHANNEL_PLAYER_SERVICE)
        notificationManager.notify(PLAYER_SERVICE_NOTIFICATION_ID, notification)

        mediaPlaybackState = state
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
        when (state) {
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {}
            PlaybackStateCompat.STATE_PLAYING -> stateBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE or commonActions)
            PlaybackStateCompat.STATE_PAUSED -> stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or commonActions)
        }
        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) 1.0f else 0f
        stateBuilder.setState(state, position, playbackSpeed)
        mediaSession.setPlaybackState(stateBuilder.build())
/*
        val fileItem = DataStore.getFileItem(mUrl)
        mStateBuilder.setState(state, fileItem.position, playbackSpeed)
        val queueItem = mQueueItems.find {
            mUrl.equals(it.description?.mediaUri?.toString())
        }
        val itemId = if (queueItem == null) MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong() else queueItem.queueId
        mStateBuilder.setActiveQueueItemId(itemId)
        mMediaSession.setPlaybackState(mStateBuilder.build())
*/
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var channel = NotificationChannel(CHANNEL_PLAYER_SERVICE, CHANNEL_PLAYER_SERVICE_NAME, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel);
    }

    private fun buildNotification(state: Int, channelID: String): Notification {
//        val builder = NotificationCompat.Builder(context, CHANNEL_PLAYER_SERVICE)
//        builder.setContentTitle(context.resources.getString(R.string.app_name))
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setColor(resources.getColor(R.color.colorPrimaryDark, theme))
//            .setStyle(MediaStyle()
//                .setMediaSession(mediaSession.sessionToken)
//            )
//        val notification = builder.build()


//        val fileItem = DataStore.getFileItem(mUrl)
//        val contentText = Formatter.formatTime(fileItem.position) + " " + Formatter.formatTime(fileItem.duration)
        val builder = NotificationCompat.Builder(this, channelID)

        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, REQUEST_LAUNCH_APP, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        if (state == PlaybackStateCompat.STATE_NONE || state == PlaybackStateCompat.STATE_STOPPED) {
            builder.setContentTitle("")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(resources.getColor( R.color.color_primary_dark, theme))
                // Take advantage of MediaStyle features
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                )
        }
        else {
            // Get the session's metadata
//            val controller = mController.getMediaSession().controller
//            val mediaDescription = controller.metadata?.description
            var title = "Dummy Title"
            val playOrPauseAction = if (state == PlaybackStateCompat.STATE_PLAYING) {
                NotificationCompat.Action(
                    R.drawable.ic_media_pause, getString(R.string.media_pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE))
            }
            else {
                NotificationCompat.Action(
                    R.drawable.ic_media_play, getString(R.string.media_play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY))
            }
            val stopAction = NotificationCompat.Action(
                R.drawable.ic_media_stop, getString(R.string.media_stop),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

            // Add the metadata for the currently playing track
            builder.setContentTitle(title)
                // Enable launching the player by clicking the notification
                .setContentIntent(pendingIntent)

                // Stop the service when the notification is swiped away
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

                // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // Add an app icon and set its accent color
                // Be careful about the color
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(resources.getColor( R.color.color_primary_dark, theme))

                // Add a play or pause button
                .addAction(playOrPauseAction)

                // Add a stop button
                .addAction(stopAction)

                // Take advantage of MediaStyle features
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
                )
        }
        return builder.build()
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
        val notification = buildNotification(PlaybackStateCompat.STATE_NONE , CHANNEL_PLAYER_SERVICE)
        startForeground(PLAYER_SERVICE_NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createPlayer() {
        setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED)
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
//        }
    }

    private fun startPlayer() {
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.resources.getString(R.string.app_name)))
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
        playerController.release()
//            playerNotificationManager.setPlayer(null)
//            mediaSessionConnector.setPlayer(null, null)
//            mediaSession.isActive = false
//            mediaSession.release()
    }

    companion object {
        const val ACTION_MEDIA_SESSION_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.ACTION_MEDIA_SESSION_CALLBACK"
        const val ACTION_PLAYER_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.ACTION_PLAYER_CALLBACK"
        const val PARAM_CALLBACK = "com.prettygoodcomputing.a4.PlayerService.PARAM_CALLBACK"
        const val PARAM_MEDIA_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_MEDIA_ID"
        const val PARAM_POSITION = "com.prettygoodcomputing.a4.PlayerService.PARAM_POSITION"
        const val PARAM_QUERY = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUERY"
        const val PARAM_QUEUE_ID = "com.prettygoodcomputing.a4.PlayerService.PARAM_QUEUE_ID"
        const val PARAM_PLAYBACK_STATE = "com.prettygoodcomputing.a4.PlayerService.PARAM_PLAYBACK_STATE"
    }
}
