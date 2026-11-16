package com.pockethost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ServerService : Service() {

    private var server: SiteServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tunnelSession: Session? = null
    private var tunnelThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val folder = intent.getStringExtra(EXTRA_FOLDER)
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                if (folder.isNullOrBlank() || !File(folder).isDirectory) {
                    stopEverything()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification(port))
                acquireWakeLock()
                startServerSafely(File(folder), port)
                isRunning = true
                localUrl = "http://${NetworkUtils.getLocalIpAddress()}:$port"
                broadcastState()
                
                // START THE PUBLIC TUNNEL
                startPublicTunnel(port)
            }
        }
        return START_STICKY
    }

    private fun startServerSafely(root: File, port: Int) {
        server?.stop()
        server = SiteServer(port, root, applicationContext).also { it.start(SOCKET_READ_TIMEOUT, false) }
    }

    // NEW FUNCTION: Connects to localhost.run
    private fun startPublicTunnel(port: Int) {
        tunnelThread = Thread {
            try {
                val jsch = JSch()
                val session = jsch.getSession("nokey", "localhost.run", 22)
                session.setConfig("StrictHostKeyChecking", "no")
                session.connect(15000)
                session.setPortForwardingR(80, "localhost", port)
                tunnelSession = session
                
                // Read the stream to get the assigned public URL
                val inputStream = session.getStreamFromRemote
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.contains("https://")) {
                        publicUrl = l.substring(l.indexOf("https://")).trim()
                        broadcastState()
                        break
                    }
                }
            } catch (e: Exception) {
                publicUrl = "Tunnel Error"
                broadcastState()
            }
        }
        tunnelThread?.start()
    }

    private fun stopEverything() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        
        // STOP THE TUNNEL
        try { tunnelSession?.disconnect() } catch (_: Exception) {}
        tunnelSession = null
        tunnelThread = null
        
        releaseWakeLock()
        isRunning = false
        localUrl = null
        publicUrl = null
        broadcastState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketHost::Server")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun broadcastState() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun buildNotification(port: Int): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, port))
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notif_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.pockethost.app.START"
        const val ACTION_STOP = "com.pockethost.app.STOP"
        const val ACTION_STATE_CHANGED = "com.pockethost.app.STATE_CHANGED"
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_PORT = "port"
        private const val CHANNEL_ID = "server"
        private const val NOTIF_ID = 1
        private const val SOCKET_READ_TIMEOUT = 30_000

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var localUrl: String? = null
            private set
        @Volatile var publicUrl: String? = null // ADDED THIS
            private set
    }
}
