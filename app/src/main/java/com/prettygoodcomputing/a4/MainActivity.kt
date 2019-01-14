package com.prettygoodcomputing.a4

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.prettygoodcomputing.a4.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = "MainActivity"

    private val REQUEST_NAVIGATE_FOLDERS = 100
    private val REQUEST_NAVIGATE_SETTINGS = 101
    private val REQUEST_PERMISSIONS = 102

    private val binding by lazy { DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main) }
    private val viewModel by lazy { MainViewModel(application) }
    private val repository by lazy { viewModel.repository }
    private val context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpDataBinding()
        setUpRecyclerView()
        setUpFloatingActionButton()
        setUpNavigationDrawer()
        setUpEasyPermissions()

//        startForegroundService(Intent(this, PlayerService::class.java))
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
        binding.recyclerView.setOnTouchListener { v, event ->
            recyclerViewGestureDetector.onTouchEvent(event)
        }

        // setup the content of the recycler view
        val fileItemAdapter = FileItemAdapter(this, viewModel)
        binding.recyclerView.adapter = fileItemAdapter
        fileItemAdapter.setOnItemClickListener(object : FileItemAdapter.OnItemClickListener {
            override fun onItemClick(fileItem: FileItem) {
                viewModel.select(fileItem)
            }

            override fun onItemLongClick(fileItem: FileItem) {
                viewModel.onLongClickFileItem(fileItem)
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
            this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
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

    override fun onResume() {
        super.onResume()
        binding.toolbar.title = Formatter.formatFolderName(repository.getCurrentFolder())
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
            R.id.action_delete_all_finished -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        closeDrawer()
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_folders -> {
                startActivityForResult(Intent(context, SelectedFoldersActivity::class.java), REQUEST_NAVIGATE_FOLDERS)
            }
            R.id.nav_settings -> {
                startActivityForResult(Intent(context, SettingsActivity::class.java), REQUEST_NAVIGATE_SETTINGS)
            }
            R.id.nav_exit -> {
                finish()
            }
        }
        return true
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
}
