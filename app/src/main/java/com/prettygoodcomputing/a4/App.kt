package com.prettygoodcomputing.a4

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.view.Window
import java.util.*
import android.provider.Settings.SettingNotFoundException
import com.crashlytics.android.Crashlytics


class App: Application {

    private val TAG = "App"

    constructor(): super()

    override fun onCreate() {
        super.onCreate()
        sApplication = this
    }

    companion object {

        private lateinit var sApplication: Application
        private val sRepository by lazy { AppRepository(sApplication) }

        @JvmStatic
        fun getApplication(): Application? {
            return sApplication
        }

        fun getContext(): Context {
            return getApplication()!!.applicationContext
        }

        fun getAppRepository(): AppRepository {
            return sRepository
        }

        fun enterImmersiveMode(window: Window) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
//                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }

        fun setScreenBrightness(brightness: Int = 255) {
            try {
                Settings.System.putInt(getContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            }
            catch (e: Exception) {
                Crashlytics.logException(e)
                e.printStackTrace()
            }
        }

        fun getScreenBrightness(): Int {
            try {
                return Settings.System.getInt(getContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                Crashlytics.logException(e)
                e.printStackTrace()
            }
            return -1
        }

        fun enableBluetooth() {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.state == BluetoothAdapter.STATE_OFF) {
                bluetoothAdapter.enable()
            }
        }

        fun lockLandscapeOrientation(activity: Activity, lock: Boolean = true) {
            activity.requestedOrientation = when (lock) {
                true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        }

        fun mute() {
            val audioManager = getContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (volume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            }
        }
    }
}
