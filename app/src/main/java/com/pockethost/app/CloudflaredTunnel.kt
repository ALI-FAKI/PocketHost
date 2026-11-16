package com.pockethost.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object CloudflaredTunnel {
    private var process: Process? = null

    fun start(context: Context, rawToken: String) {
        if (process != null) return

        Thread {
            try {
                // Android extracts .so files from jniLibs to nativeLibraryDir
                val binary = File(context.applicationInfo.nativeLibraryDir, "libcloudflared.so")
                
                if (!binary.exists()) {
                    Log.e("Cloudflare", "Binary not found at: ${binary.absolutePath}")
                    return@Thread
                }

                // Clean the token in case the whole command was pasted
                var cleanToken = rawToken.trim()
                if (cleanToken.contains("install ")) {
                    cleanToken = cleanToken.substringAfter("install ").trim()
                }

                val pb = ProcessBuilder(binary.absolutePath, "tunnel", "--no-autoupdate", "run", "--token", cleanToken)
                pb.redirectErrorStream(true)
                process = pb.start()

                // Consume output to prevent the process from freezing
                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                
                // FIX: Use forEachLine to avoid the smart cast error
                reader.forEachLine { line ->
                    Log.d("Cloudflare", line)
                }
                
            } catch (e: Exception) {
                Log.e("Cloudflare", "Error starting tunnel", e)
            }
        }.start()
    }

    fun stop() {
        process?.destroy()
        process = null
    }
}
