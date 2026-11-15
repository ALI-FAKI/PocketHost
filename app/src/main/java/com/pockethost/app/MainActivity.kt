package com.pockethost.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pockethost.app.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val path = uriToFilePath(uri)
                if (path != null && File(path).isDirectory) {
                    SettingsStore.setFolderPath(this, path)
                    refreshUi()
                    Toast.makeText(this, "Folder set", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Only primary storage folders are supported for now",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        b.btnPickFolder.setOnClickListener { pickFolder() }
        b.btnStart.setOnClickListener { startServer() }
        b.btnStop.setOnClickListener { stopServer() }
        b.btnCopy.setOnClickListener { copyUrl() }
        b.btnShare.setOnClickListener { shareUrl() }
        
        // --- FIX APPLIED HERE ---
        b.btnHotspot.setOnClickListener {
            val intent = Intent("android.settings.TETHER_SETTINGS")
            try {
                this@MainActivity.startActivity(intent)
            } catch (e: Exception) {
                this@MainActivity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
        // ------------------------

        b.btnBattery.setOnClickListener { openBatterySettings() }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter(ServerService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stateReceiver, f)
        }
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
    }

    private fun hasAllFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun pickFolder() {
        if (!hasAllFiles()) {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                notifPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            Toast.makeText(this, "Grant storage access, then try again", Toast.LENGTH_LONG).show()
            return
        }
        folderLauncher.launch(null)
    }

    private fun uriToFilePath(uri: Uri): String? = try {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val parts = id.split(":", limit = 2)
        if (parts.size == 2 && parts[0] == "primary") {
            if (parts[1].isEmpty()) Environment.getExternalStorageDirectory().absolutePath
            else File(Environment.getExternalStorageDirectory(), parts[1]).absolutePath
        } else null
    } catch (_: Exception) { null }

    private fun startServer() {
        val folder = SettingsStore.getFolderPath(this)
        if (folder == null || !File(folder).isDirectory) {
            Toast.makeText(this, "Pick a folder first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAllFiles()) { pickFolder(); return }

        val intent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER, folder)
            putExtra(ServerService.EXTRA_PORT, SettingsStore.getPort(this))
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopServer() {
        startService(
            Intent(this, ServerService::class.java).apply { action = ServerService.ACTION_STOP }
        )
    }

    private fun copyUrl() {
        val url = b.tvLocalUrl.text?.toString().orEmpty()
        if (url.isBlank() || url == "—") return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("url", url))
        Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareUrl() {
        val url = b.tvLocalUrl.text?.toString().orEmpty()
        if (url.isBlank() || url == "—") return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }, "Share URL"
            )
        )
    }

    private fun openBatterySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refreshUi() {
        val folder = SettingsStore.getFolderPath(this)
        val running = ServerService.isRunning

        b.tvFolder.text = folder ?: getString(R.string.no_folder)

        if (running) {
            b.tvStatus.text = getString(R.string.status_running)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_running))
            b.tvLocalUrl.text = ServerService.localUrl ?: "—"
            b.btnStart.isEnabled = false
            b.btnStop.isEnabled = true
            b.btnCopy.isEnabled = true
            b.btnShare.isEnabled = true
        } else {
            b.tvStatus.text = getString(R.string.status_stopped)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_stopped))
            b.tvLocalUrl.text = "—"
            b.btnStart.isEnabled = folder != null
            b.btnStop.isEnabled = false
            b.btnCopy.isEnabled = false
            b.btnShare.isEnabled = false
        }

        b.tvHotspot.text = if (NetworkUtils.isHotspotOn(this))
            getString(R.string.hotspot_on) else getString(R.string.hotspot_off)
        b.tvDeviceIp.text = getString(R.string.device_ip, NetworkUtils.getLocalIpAddress())
    }
}
