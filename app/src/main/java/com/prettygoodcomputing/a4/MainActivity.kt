package com.prettygoodcomputing.a4

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.SeekBar
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener
import com.prettygoodcomputing.a4.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.EasyPermissions
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    ActionMode.Callback,
    SeekBar.OnSeekBarChangeListener {

    private val APPLICATION_NAME = "A4"
    private val TAG = "MainActivity"

    private val REQUEST_NAVIGATE_FOLDERS = 100
    private val REQUEST_NAVIGATE_SETTINGS = 101
    private val REQUEST_PERMISSIONS = 102

    private val SWIPE_MIN_DISTANCE_DP = 48
    private val SWIPE_THRESHOLD_VELOCITY_DP = 36
    private val SWIPE_PLAY_NEXT_DISTANCE = 64
    private val UPDATE_INFO_SHOW_TIME = 5000L
    private val UPDATE_CONTROLS_SHOW_TIME = 30000L
    private val VOLUME_BAR_SHOW_TIME = 5000L
    private val VOLUME_SWIPE_THRESHOLD_VELOCITY = 500
    private val PROGRESS_NEAR_BEGINNING = 60 * 1000
    private val PROGRESS_NEAR_ENDING = 2 * 60 * 1000

    private val UPDATE_PROGRESS_INTERVAL = 1000L
    private val UPDATE_CLOCK_INTERVAL = 60000L

    private val binding by lazy { DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main) }
    private val viewModel by lazy { MainViewModel(application) }
    private val repository by lazy { viewModel.repository }

    private val context = this
    private var actionMode: ActionMode? = null
    private var actionModeMenu: Menu? = null
    private var localBroadcastReceiver: BroadcastReceiver? = null

    private var updateInfoStartTime = 0L
    private var showVolumeBarStartTime = 0L
    private var showControlStartTime = 0L
    private val handler = Handler()
    private val updateInfoAction = Runnable { updatePlayerInfo() }
    private val updateProgressAction = Runnable { updateProgress() }
    private val updateClockAction = Runnable { updateClock() }
    private var message = ""

    private lateinit var player: SimpleExoPlayer
    private var playerCreated = false
    private val playerEventListener = PlayerEventListener()
    private val playerVideoListener = PlayerVideoListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpDataBinding()
        setUpRecyclerView()
        setUpPlayerView()
        setUpFloatingActionButton()
        setUpNavigationDrawer()
        setUpEasyPermissions()

        startForegroundService(Intent(this, PlayerService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            App.enterImmersiveMode(window)
        }
    }

    private fun setUpDataBinding() {
        setSupportActionBar(binding.toolbar)
        binding.setLifecycleOwner(this)
        binding.viewModel = viewModel
        binding.repository = repository
    }

    private fun setUpRecyclerView() {

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)

        val recyclerViewGestureListener = object: GestureDetector.SimpleOnGestureListener() {

            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                val viewConfiguration = ViewConfiguration.get(this@MainActivity)
                val flingVelocity = viewConfiguration.scaledMinimumFlingVelocity / 2
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX > flingVelocity || velocityX < -flingVelocity) {
                        viewModel.switchFolder(Math.signum(velocityX).toInt())
                        return true
                    }
                }
                return false
            }
        }

        // setup a gesture detector for the recycler view to allow swipe left / right to switch folder
        val recyclerViewGestureDetector = GestureDetector(context, recyclerViewGestureListener)
        binding.recyclerView.setOnTouchListener { _, event ->
            recyclerViewGestureDetector.onTouchEvent(event)
        }

        // setup the content of the recycler view
        val fileItemAdapter = FileItemAdapter(this, viewModel)
        binding.recyclerView.adapter = fileItemAdapter
        fileItemAdapter.setOnItemClickListener(object : FileItemAdapter.OnItemClickListener {
            override fun onItemClick(fileItem: FileItem) {
                val count = viewModel.select(fileItem)
                if (count == 0) {
                    actionMode?.finish()
                }
                else if (actionMode == null){
                    startPlayer(fileItem)
                }
            }

            override fun onItemLongClick(fileItem: FileItem) {
                val count = viewModel.select(fileItem)
                if (actionMode == null) {
                    actionMode = startSupportActionMode(context)
                    viewModel.singleSelection = false
                }
                else if (count == 0) {
                    actionMode?.finish()
                }
            }
        })

        repository.getCurrentFileItems().observe(this, Observer<List<FileItem>> {
            fileItemAdapter.submitList(it)
        })
    }

    private fun setUpFloatingActionButton() {
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }

    private fun setUpNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
    }

    //    @AfterPermissionGranted(123)
    private fun setUpEasyPermissions() {
        val perms = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK, Manifest.permission.WRITE_SETTINGS)
        if (EasyPermissions.hasPermissions(context, *perms)) {

        }
        else {
            EasyPermissions.requestPermissions(this, "We need these permissions for sure", 123, *perms)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onStart() {
        super.onStart()
        registerLocalBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        binding.toolbar.title = Formatter.formatFolderName(repository.getCurrentFolder())
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unregisterLocalBroadcastReceiver()
        releasePlayer()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_bluetooth -> openBluetoothSettings()
            R.id.action_sort -> openSortMenu()
            R.id.action_refresh -> viewModel.refresh()
            R.id.action_delete_all_finished -> viewModel.deleteAllFinished()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        closeDrawer()
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_folders -> startActivityForResult(Intent(context, SelectedFoldersActivity::class.java), REQUEST_NAVIGATE_FOLDERS)
            R.id.nav_settings -> startActivityForResult(Intent(context, SettingsActivity::class.java), REQUEST_NAVIGATE_SETTINGS)
            R.id.nav_exit -> finish()
        }
        return true
    }

    private fun openBluetoothSettings(): Boolean {
//        cancelAutoPlay()
//        if (TimeProfile.autoMaximumBrightness()) {
//            App.setScreenBrightness()
//        }
        App.enableBluetooth()
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        return true
    }

    private fun openSortMenu(): Boolean {
//        cancelAutoPlay()
        val menuItemView = findViewById<View>(R.id.action_sort)
        if (menuItemView != null) {
            val popupMenu = PopupMenu(context, menuItemView)
            popupMenu.inflate(R.menu.sort)
            checkOptionsMenu(popupMenu.menu)
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_sort_by_name -> viewModel.sortBy(FileItem.FIELD_NAME)
                    R.id.action_sort_by_size -> viewModel.sortBy(FileItem.FIELD_FILE_SIZE)
                    R.id.action_sort_by_date -> viewModel.sortBy(FileItem.FIELD_LAST_MODIFIED)
                }
                true
            }
            popupMenu.show()
        }
        return true
    }

    private fun setChecked(menuItem: MenuItem?, checked: Boolean) {
        menuItem?.apply {
            isCheckable = checked
            isChecked = checked
        }
    }

    private fun checkOptionsMenu(menu: Menu?) {
        if (menu != null) {
            when (repository.getCurrentSortBy()) {
                FileItem.FIELD_FILE_SIZE -> {
                    setChecked(menu.findItem(R.id.action_sort_by_name), false)
                    setChecked(menu.findItem(R.id.action_sort_by_size), true)
                    setChecked(menu.findItem(R.id.action_sort_by_date), false)
                }
                FileItem.FIELD_LAST_MODIFIED -> {
                    setChecked(menu.findItem(R.id.action_sort_by_name), false)
                    setChecked(menu.findItem(R.id.action_sort_by_size), false)
                    setChecked(menu.findItem(R.id.action_sort_by_date), true)
                }
                else -> {
                    setChecked(menu.findItem(R.id.action_sort_by_name), true)
                    setChecked(menu.findItem(R.id.action_sort_by_size), false)
                    setChecked(menu.findItem(R.id.action_sort_by_date), false)
                }
            }
        }
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        Logger.enter(TAG, "onCreateActionMode()")
        try {
            mode?.menuInflater?.inflate(R.menu.fileitems, menu)
            actionModeMenu = menu
        }
        catch (e: Exception) {
            Crashlytics.logException(e)
        }
        Logger.exit(TAG, "onCreateActionMode()")
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        actionModeMenu = null
        viewModel.resetSelection()
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_delete -> viewModel.deleteSelected()
            R.id.action_flag -> viewModel.flagSelected()
            R.id.action_mark_as_finished -> viewModel.markSelectedAsFinished()
        }
        mode?.finish()
        return false
    }

    private fun closeDrawer(): Boolean {
        return when {
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                true
            }
            binding.drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.END)
                true
            }
            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Logger.enter(TAG, "onActivityResult() requestCode = $requestCode, resultCode = $resultCode")
        when (requestCode) {
            REQUEST_NAVIGATE_FOLDERS -> {
                if (resultCode == Activity.RESULT_OK) {
                    val selectedFolders = data?.getStringArrayExtra("selected-folders")?.toList()
                    if (selectedFolders != null) {
                        viewModel.updateSelectedFolders(selectedFolders)
                    }
                }
            }
        }
        Logger.exit(TAG, "onActivityResult() requestCode = $requestCode, resultCode = $resultCode")
    }

    private fun setUpPlayerView() {
        createPlayer()
        binding.playerView.useController = false
        binding.playerView.player = player

        val middlePanelGestureDetector = GestureDetector(context, MiddlePanelGestureListener())
        binding.middlePanel.setOnTouchListener { _, event ->
            middlePanelGestureDetector.onTouchEvent(event)
        }

        // temporary action to stop player
        binding.brightnessButton.setOnClickListener {
            stopPlayer()
        }
    }

    private fun showMessage(info: String) {
        message = info
        showControls()
        updatePlayerInfo()
    }

    private fun showControls() {
        val currentTime = SystemClock.elapsedRealtime()
        showControlStartTime = currentTime
        updateControls()
    }

    private fun hideControls() {
        val currentTime = SystemClock.elapsedRealtime()
        showControlStartTime = currentTime - UPDATE_CONTROLS_SHOW_TIME * 2
        updateControls()
    }

    private fun toggleControls() {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - showControlStartTime > UPDATE_CONTROLS_SHOW_TIME) {
            showControls();
        }
        else {
            hideControls()
        }
    }

    private fun progressNearBeginning(): Boolean {
        if (playerCreated) {
            val position = player.currentPosition
            return position < PROGRESS_NEAR_BEGINNING
        }
        return false
    }

    private fun progressNearEnding(): Boolean {
        if (playerCreated) {
            val position = player.currentPosition
            val duration = player.duration
            return position > 0 && duration > 0 && position + PROGRESS_NEAR_ENDING > duration
        }
        return false
    }

    private fun updateControls() {
        val currentTime = SystemClock.elapsedRealtime()
        val visibility = when {
//            mController.isSeekRepeat() -> View.VISIBLE
            progressNearBeginning() || progressNearEnding() -> View.VISIBLE
            playerCreated && !player.playWhenReady -> View.VISIBLE
            currentTime - showControlStartTime > UPDATE_CONTROLS_SHOW_TIME -> View.INVISIBLE
            else -> View.VISIBLE
        }
        viewModel.setPlayerInfoVisibility(visibility)

        val thumb = if (visibility == View.VISIBLE) R.drawable.seek_thumb_tiny else R.drawable.seek_thumb_invisible
        binding.timelineBar.thumb = resources.getDrawable(thumb, context.theme)
    }

    private fun createPlayer() {
        if (!playerCreated) {
            playerCreated = true
            player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector())
            player.addListener(playerEventListener)
            player.addVideoListener(playerVideoListener)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setSeekParameters(SeekParameters.NEXT_SYNC)
/*
            mediaSessionConnector = MediaSessionConnector(mediaSession)
            mediaSessionConnector.setQueueNavigator(object: TimelineQueueNavigator(mediaSession) {

                override fun getMediaDescription(player: Player?, windowIndex: Int): MediaDescriptionCompat {
                    val title = when (windowIndex) {
                        0, 2, 4, 6 -> "mpthreetest.mp3 " + windowIndex.toString()
                        else -> "crowd-cheering.mp3 " + windowIndex.toString()
                    }
                    val mediaDescription = MediaDescriptionCompat.Builder()
                        .setMediaId(title)
                        .setTitle(title)
                        .setDescription(title)
                        .build()
                    return mediaDescription

                }
            })
            mediaSessionConnector.setPlayer(player, null)
*/
        }
    }

    private fun releasePlayer() {
        if (playerCreated) {
            handler.removeCallbacks(updateClockAction);
            handler.removeCallbacks(updateProgressAction);
            player.playWhenReady = false
            player.removeListener(playerEventListener)
            player.removeVideoListener(playerVideoListener)
            player.release()
            playerCreated = false
//            mediaSessionConnector.setPlayer(null, null)
//            mediaSession.isActive = false
//            mediaSession.release()
        }

    }
    private fun startPlayer(fileItem: FileItem) {
        binding.mainLayout.visibility = View.GONE
        binding.playerViewLayout.visibility = View.VISIBLE
        viewModel.setCurrentFileItem(fileItem)

        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, APPLICATION_NAME))
        val concatenatingMediaSource = ConcatenatingMediaSource()

        val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(fileItem.url))
        concatenatingMediaSource.addMediaSource(mediaSource)

        player.prepare(concatenatingMediaSource)
        player.playWhenReady = true
        updateAllInfo()
    }

    private fun togglePausePlayer() {
        if (playerCreated) {
            player.playWhenReady = !player.playWhenReady
            if (!player.playWhenReady) {
                showControls()
            }
            updateAllInfo()
        }
    }

    private fun resumePlayer() {
        if (playerCreated) {
            player.playWhenReady = true
            updateAllInfo()
        }
    }

    private fun pausePlayer() {
        if (playerCreated) {
            player.playWhenReady = false
            updateAllInfo()
        }
    }

    private fun stopPlayer() {
        handler.removeCallbacks(updateClockAction);
        handler.removeCallbacks(updateProgressAction);
        if (playerCreated) {
            player.playWhenReady = false
            player.stop()
        }
        binding.mainLayout.visibility = View.VISIBLE
        binding.playerViewLayout.visibility = View.GONE
    }

    private fun updateAllInfo() {
        updatePlayerInfo()
        updateProgress()
        updateClock()
    }

    private fun updatePlayerInfo() {
        handler.removeCallbacks(updateInfoAction)
        updateInfoStartTime = SystemClock.elapsedRealtime();
        var info = ""
        if (message.isNotEmpty()) {
            info += "$message\n"
        }
//        if (info.isEmpty()) {
//            if (mPlayAudioAsyncOffset != 0L) {
//                val diff = mController.getPlayAudioAsyncDifference()
//                if (diff != 0L) {
//                    info = "Async: $diff \u2713\n"
//                }
//            }
//        }
//
        if (playerCreated && !player.playWhenReady) {
            info += "Paused\n"
        }
        if (viewModel.videoWidth > 0 && viewModel.videoHeight > 0) {
            info += "${viewModel.videoWidth} x ${viewModel.videoHeight}"
        }
        viewModel.setPlayerInfoBar(info)
        handler.postDelayed(updateInfoAction, UPDATE_INFO_SHOW_TIME);
    }

    private fun updateProgress() {
        handler.removeCallbacks(updateProgressAction);
        viewModel.setPlayerInfoTime(Formatter.formatTime(player.currentPosition) + " " + Formatter.formatTime(player.duration))
        viewModel.setProgressBarInfo((player.currentPosition / 1000L).toInt(), (player.duration / 1000L).toInt())

        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - updateInfoStartTime > UPDATE_INFO_SHOW_TIME) {
            message = ""
            updatePlayerInfo()
        }
        if (currentTime - showVolumeBarStartTime > VOLUME_BAR_SHOW_TIME) {
            binding.volumeBar.visibility = View.GONE
        }
        updateControls()

        handler.postDelayed(updateProgressAction, UPDATE_PROGRESS_INTERVAL);
    }

    private fun getBatteryLevel():Int {
        val batteryIntent = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (batteryIntent == null) 0 else {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
            level * 100 / scale
        }
    }

    private fun updateClock() {
        handler.removeCallbacks(updateClockAction);
        viewModel.setPlayerInfoClock("${getBatteryLevel()}% ${SimpleDateFormat("h:mm").format(Date())}")
        handler.postDelayed(updateClockAction, UPDATE_CLOCK_INTERVAL);
    }


    private fun registerLocalBroadcastReceiver() {
        localBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    when (intent.action) {
                        PlayerService.ACTION_MEDIA_SESSION_CALL_BACK -> {
                            val callBack = intent.extras?.getString(PlayerService.PARAM_CALL_BACK) ?: ""
                            val mediaId = intent.extras?.getString(PlayerService.PARAM_MEDIA_ID) ?: ""
                            val query = intent.extras?.getString(PlayerService.PARAM_QUERY) ?: ""
                            val position = intent.extras?.getLong(PlayerService.PARAM_POSITION, 0L) ?: 0L
                            val queueId = intent.extras?.getLong(PlayerService.PARAM_QUEUE_ID, 0L) ?: 0L

                            Toast.makeText(this@MainActivity, "MA: $callBack $mediaId", Toast.LENGTH_LONG).show()
                            when (callBack) {
                                "onPlayFromMediaId" -> {} // mediaId
                                "onPlayFromSearch" -> {} // query
                                "onPlay" -> resumePlayer()
                                "onPause" -> pausePlayer()
                                "onStop" -> stopPlayer()
                                "onSkipToNext" -> {}
                                "onSkipToPrevious" -> {}
                                "onSeekTo" -> {} // position
                                "onSkipToQueueItem" -> {} // queueId
                            }
                        }
                    }
                }
            }
        }
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).registerReceiver(it, IntentFilter(PlayerService.ACTION_MEDIA_SESSION_CALL_BACK))
        }
    }

    private fun unregisterLocalBroadcastReceiver() {
        localBroadcastReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
        localBroadcastReceiver = null
    }

    private inner class PlayerEventListener: Player.DefaultEventListener() {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            super.onPlayerStateChanged(playWhenReady, playbackState)
            updateProgress()
            when (playbackState) {
                Player.STATE_ENDED -> stopPlayer()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            super.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onSeekProcessed() {
            super.onSeekProcessed()
            updateProgress()
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
            super.onTracksChanged(trackGroups, trackSelections)
            updateProgress()
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
            super.onPlayerError(error)
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            super.onLoadingChanged(isLoading)
        }

        override fun onPositionDiscontinuity(reason: Int) {
            super.onPositionDiscontinuity(reason)
            updateProgress()
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            super.onTimelineChanged(timeline, manifest, reason)
            updateProgress()
        }
    }

    private inner class PlayerVideoListener: VideoListener {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            viewModel.setVideoSize(width, height)
            updatePlayerInfo()
        }

        override fun onRenderedFirstFrame() {
        }
    }

    private inner class MiddlePanelGestureListener: GestureDetector.SimpleOnGestureListener() {

        private val TAG = "MiddlePanelGestureListener"

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            Logger.enter(TAG, "onSingleTapConfirmed()")
//            if (mController.isSeekRepeat()) {
//                showControls()
//                mController.cancelSeekRepeat()
//            }
//            else {
//                toggleControls()
//            }
            toggleControls()
            togglePausePlayer()
            Logger.exit(TAG, "onSingleTapConfirmed()")
            return super.onSingleTapConfirmed(e)
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
//            Logger.enter(TAG, "onDoubleTap()")
            togglePausePlayer()
//            Logger.exit(TAG, "onDoubleTap()")
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent?) {
            super.onLongPress(e)
            Logger.enter(TAG, "onLongPress()")
            togglePausePlayer()
//            stopPlayer()
//            cancelPushToBackground()
            Logger.exit(TAG, "onLongPress()")
        }

        private fun convertDpToPixel(dp: Int): Int {
            return dp * App.getContext().resources.getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            Logger.enter(TAG, "onFling()")
            val swipeMinDistance = convertDpToPixel(SWIPE_MIN_DISTANCE_DP)
            val swipeThresholdVelocity = convertDpToPixel(SWIPE_THRESHOLD_VELOCITY_DP)
            val playNextDistance = convertDpToPixel(SWIPE_PLAY_NEXT_DISTANCE)
            showControls()
            if (Math.abs(velocityX) > Math.abs(velocityY)) {
                if (Math.abs(e1.x - e2.x) > swipeMinDistance && Math.abs(velocityX) > swipeThresholdVelocity) {
                    if (e1.x < e2.x) {
                        // swipe right
                    } else {
                        // swipe left
                    }
                }
            }
            else {
                if (Math.abs(e1.y - e2.y) > swipeMinDistance && Math.abs(velocityY) > swipeThresholdVelocity) {
                    if (Math.abs(e1.x - e2.x) > playNextDistance) {
                        if (e1.x > e2.x) {
                            // swipe left then up / down, will mark the file as finished
//                            fileItem.finished = true
                        }
                        if (e1.y > e2.y) {
                            // swipe left / right then up
//                            mController.playPrevious()
                        }
                        else {
                            // swipe left / right then down
//                            mController.playNext()
                        }
                    }
                    else {
                        if (e1.y < e2.y) {
                            // swipe down, close the app
                            stopPlayer()
//                            cancelPushToBackground()
                            finish()
                        }
                        else {
                            // on swipe up, push to background close the app
                            stopPlayer()
//                            allowPushToBackground()
                            finish()
                        }
                    }
                }
            }

/*
            val swipeMinDistance = mController.convertDpToPixel(SWIPE_MIN_DISTANCE_DP)
            val swipeThresholdVelocity = mController.convertDpToPixel(SWIPE_THRESHOLD_VELOCITY_DP)
            val playNextDistance = mController.convertDpToPixel(SWIPE_PLAY_NEXT_DISTANCE)
            val diffY = e1.y - e2.y
            val fileItem = DataStore.getFileItem(getUrl())
            Logger.v(TAG, "onFling() fileItem.duration = ${fileItem.duration}, swipeMinDistance = $swipeMinDistance, diffY = $diffY, velocityX = $velocityX, velocityY = $velocityY")
            showControls()
            if (fileItem.duration > 0) {
                mController.interactive = true
                if (Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (Math.abs(e1.x - e2.x) > swipeMinDistance && Math.abs(velocityX) > swipeThresholdVelocity) {
                        Logger.v(TAG, "onFling() swipe left / right")
                        if (e1.x < e2.x && mController.nearEnding(fileItem.position, fileItem.duration)) {
                            // near the ending, swipe right will play next
                            mController.playNext()
                        }
                        else {
                            val position = mController.calculateSeekPosition(e2.x - e1.x, velocityX)
                            mController.cancelPendingSeek()
                            mController.seekTo(position)
                        }
                    }
                }
                else {
                    if (Math.abs(e1.y - e2.y) > playNextDistance && Math.abs(velocityY) > swipeThresholdVelocity) {
                        Logger.v(TAG, "onFling() swipe up / down")
                        if (Math.abs(e1.x - e2.x) > playNextDistance) {
                            if (e1.x > e2.x) {
                                // swipe left then up / down, will mark the file as finished
                                fileItem.finished = true
                            }
                            if (e1.y > e2.y) {
                                // swipe left / right then up
                                mController.playPrevious()
                            }
                            else {
                                // swipe left / right then down
                                mController.playNext()
                            }
                        }
                        else {
                            if (e1.y < e2.y) {
                                // on swipe down, close the app
                                stopPlayer()
                                cancelPushToBackground()
                                finish(true)
                            }
                            else {
                                // on swipe up, push to background close the app
                                allowPushToBackground()
                                finish(true)
                            }
                        }
                    }
                }
            }
*/
            Logger.exit(TAG, "onFling()")
            return true
        }
    }

    // SeekBar.OnSeekBarChangeListener
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            showControls()
//            seekTo(progress * 1000L)
            updateAllInfo()
        }
    }

    // SeekBar.OnSeekBarChangeListener
    override fun onStartTrackingTouch(seekBar: SeekBar?) {
    }

    // SeekBar.OnSeekBarChangeListener
    override fun onStopTrackingTouch(seekBar: SeekBar?) {
    }
}
