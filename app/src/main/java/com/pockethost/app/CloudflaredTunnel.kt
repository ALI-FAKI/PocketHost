package com.pockethost.app

object CloudflaredTunnel {

    init {
        try {
            System.loadLibrary("cloudflared")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    private external fun CloudflaredInit()
    private external fun CloudflaredRun(args: String)
    private external fun CloudflaredStop()

    fun start(rawToken: String) {
        Thread {
            try {
                // Clean the token in case the whole command was pasted
                var cleanToken = rawToken.trim()
                if (cleanToken.contains("install ")) {
                    cleanToken = cleanToken.substringAfter("install ").trim()
                }

                CloudflaredInit()
                val args = "tunnel --no-autoupdate run --token $cleanToken"
                CloudflaredRun(args)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        try {
            CloudflaredStop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
