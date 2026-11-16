package com.pockethost.app

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.InputStreamReader

object CloudflaredTunnel {
    private var session: Session? = null
    private var thread: Thread? = null

    fun start(port: Int) {
        if (session != null) return

        thread = Thread {
            try {
                val jsch = JSch()
                // Connect to localhost.run anonymously
                val newSession = jsch.getSession("nokey", "localhost.run", 22)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.connect(15000)
                
                // Forward local port to remote port 80
                newSession.setPortForwardingR(80, "localhost", port)
                session = newSession

                // Read the stream to find the public URL
                val reader = BufferedReader(InputStreamReader(newSession.getStreamFromRemote))
                reader.forEachLine { line ->
                    if (line.contains("https://") && line.contains(".lhr.life")) {
                        val url = line.substring(line.indexOf("https://")).trim()
                        ServerService.publicUrl = url
                        ServerService.broadcastState()
                    }
                    Log.d("Tunnel", line)
                }
            } catch (e: Exception) {
                Log.e("Tunnel", "Error starting tunnel", e)
            }
        }
        thread?.start()
    }

    fun stop() {
        try {
            session?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        session = null
        thread = null
    }
}
