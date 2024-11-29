/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()
    private lateinit var sharedStoragePickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var dictUri: String

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.mainmenu, menu)
        return true
    }
    //private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_bookshelf,
                R.id.navigation_catalog_list,
                R.id.navigation_about
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        //弹出请求获取存储权限的对话框
        val REQUEST_EXTERNAL_STORAGE = 1
        val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val permission = ActivityCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                this@MainActivity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }

        sharedStoragePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    dictUri = setDictPreferences(it)
                }
            }
        viewModel.channel.receive(this) { handleEvent(it) }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun handleEvent(event: MainViewModel.Event) {
        when (event) {
            is MainViewModel.Event.ImportPublicationSuccess ->
                Snackbar.make(
                    findViewById(android.R.id.content),
                    getString(R.string.import_publication_success),
                    Snackbar.LENGTH_LONG
                ).show()

            is MainViewModel.Event.ImportPublicationError -> {
                event.error.toUserError().show(this)
            }
        }
    }

    //获取字典的uri
    private fun setDictPreferences(uri: Uri): String {
        val path = uri.path
        var externalFileRootDir = getExternalFilesDir(null)
        do {
            externalFileRootDir = externalFileRootDir?.parentFile
        } while (externalFileRootDir?.absolutePath!!.contains("/Android"))

        val saveDir = externalFileRootDir.absolutePath
        var name = saveDir + "/" + path?.split(":")?.get(1)

        // 向preferences中存入数据
        val sharedPreferences = getSharedPreferences("dictpref", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("uri", name)
        editor.apply()

        return name
    }

    fun fabClick(view: android.view.View) {
        Toast.makeText(this, "请先选择字典位置", Toast.LENGTH_LONG).show()
        sharedStoragePickerLauncher.launch(
            arrayOf(
                "*/*",
                "text/plain",
                "application/pdf",
                "application/epub+zip"
            )
        )
        //if(isExternalStorageWritable())
        //    loadFile("test.txt")
    }

    //从preferences中读取字典数据，若没有则选择默认字典
    fun setDict(itemMenu: MenuItem) {
        /*val sharedPreferences = this.getSharedPreferences("dictpref", Context.MODE_PRIVATE)
        val path = sharedPreferences.getString("uri", null)
        if(path==null){

        }*/

        sharedStoragePickerLauncher.launch(
            arrayOf(
                "*/*",
                "text/plain",
                "application/pdf",
                "application/epub+zip"
            )
        )


    }
}
