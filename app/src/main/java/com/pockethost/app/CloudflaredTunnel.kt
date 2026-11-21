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
    private var isRunning = false

    fun start(port: Int, onUrlFound: (String) -> Unit) {
        if (isRunning) return
        isRunning = true

        thread = Thread {
            while (isRunning) {
                try {
                    val jsch = JSch()
                    val newSession = jsch.getSession("nokey", "localhost.run", 22)
                    newSession.setConfig("StrictHostKeyChecking", "no")
                    
                    // Send keep-alive packets to prevent timeouts
                    newSession.setServerAliveInterval(60000)
                    newSession.setServerAliveCountMax(3)

                    newSession.connect(15000)
                    newSession.setPortForwardingR(80, "localhost", port)
                    session = newSession

                    val channel = newSession.openChannel("shell") as ChannelShell
                    val reader = BufferedReader(InputStreamReader(channel.inputStream))
                    channel.connect()

                    // This Regex extracts ONLY the clean URL, ignoring all the terminal junk
                    val urlRegex = Regex("""https://[a-zA-Z0-9]+\.app\.lhr\.life""")

                    reader.forEachLine { line ->
                        val match = urlRegex.find(line)
                        if (match != null) {
                            val cleanUrl = match.value
                            Log.d("Tunnel", "Success! Clean URL found: $cleanUrl")
                            onUrlFound(cleanUrl)
                        }
                        // We do not log the messy 'line' here anymore to keep logs clean
                    }
                } catch (e: Exception) {
                    Log.e("Tunnel", "Tunnel error, reconnecting in 5s...", e)
                }

                // Clean up before reconnecting
                try { session?.disconnect() } catch (_: Exception) {}
                session = null

                if (isRunning) {
                    Thread.sleep(5000) // Wait 5 seconds before trying again
                }
            }
        }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
        thread = null
    }
}
