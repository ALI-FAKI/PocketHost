package com.pockethost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        if (!SettingsStore.getAutoStart(context)) return
        val folder = SettingsStore.getFolderPath(context) ?: return
        if (!File(folder).isDirectory) return
        val svc = Intent(context, ServerService::class.java).apply {
            action = ServerService.ACTION_START
            putExtra(ServerService.EXTRA_FOLDER, folder)
            putExtra(ServerService.EXTRA_PORT, SettingsStore.getPort(context))
        }
        ContextCompat.startForegroundService(context, svc)
    }
}
