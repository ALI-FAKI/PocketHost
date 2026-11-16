package com.pockethost.app

import android.content.Context
import java.io.File

object CloudflaredTunnel {
    private var process: Process? = null

    fun start(context: Context, token: String) {
        if (process != null) return

        Thread {
            try {
                val binary = File(context.applicationInfo.nativeLibraryDir, "libcloudflared.so")
                if (!binary.exists()) {
                    return@Thread
                }

                val pb = ProcessBuilder(
                    binary.absolutePath,
                    "tunnel",
                    "--no-autoupdate",
                    "run",
                    "--token",
                    token
                )
                pb.redirectErrorStream(true)
                process = pb.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        try {
            process?.destroy()
            process = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
