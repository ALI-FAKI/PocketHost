package com.pockethost.app

import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.InputStreamReader

object CloudflaredTunnel {
    private var session: Session? = null
    private var thread: Thread? = null

    fun start(port: Int, onUrlFound: (String) -> Unit) {
        if (session != null) return

        thread = Thread {
            try {
                val jsch = JSch()
                val newSession = jsch.getSession("nokey", "localhost.run", 22)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.connect(15000)
                
                newSession.setPortForwardingR(80, "localhost", port)
                session = newSession

                val channel = newSession.openChannel("shell") as ChannelShell
                val reader = BufferedReader(InputStreamReader(channel.inputStream))
                channel.connect()

                reader.forEachLine { line ->
                    if (line.contains("https://") && line.contains(".lhr.life")) {
                        val url = line.substring(line.indexOf("https://")).trim()
                        onUrlFound(url)
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
