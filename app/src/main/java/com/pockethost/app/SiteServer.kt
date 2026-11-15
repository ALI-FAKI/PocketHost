package com.pockethost.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class SiteServer(port: Int, private val root: File, private val context: Context) : NanoHTTPD(port) {

    private val rootCanonical: String = root.canonicalPath
    private val startTime = System.currentTimeMillis()
    private val requestCount = AtomicInteger(0)

    override fun serve(session: IHTTPSession): Response {
        // Count every incoming request
        requestCount.incrementAndGet()

        // 1. Dashboard Route
        if (session.uri == "/__host" || session.uri == "/__host/") {
            return handleDashboard()
        }

        if (session.method != Method.GET && session.method != Method.HEAD) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed"
            )
        }

        var uri = session.uri
        if (uri.isNullOrBlank() || uri == "/") uri = "/index.html"

        val target: File
        try {
            target = File(root, uri).canonicalFile
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad path"
            )
        }

        if (!target.path.startsWith(rootCanonical)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden"
            )
        }

        var file = if (target.isDirectory) File(target, "index.html") else target

        // 2. SPA Fallback Logic
        if (!file.exists() || !file.isFile) {
            val hasExtension = uri.substringAfterLast('.', "").isNotEmpty()
            if (!hasExtension) {
                file = File(root, "index.html")
            } else {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $uri"
                )
            }
        }

        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $uri"
            )
        }

        val mime = getMimeTypeForFile(file.name)
        val length = file.length()

        return if (session.method == Method.HEAD) {
            newFixedLengthResponse(Response.Status.OK, mime, null, length)
        } else {
            newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), length)
        }
    }

    // 3. Dashboard HTML Generator
    private fun handleDashboard(): Response {
        val uptimeMs = System.currentTimeMillis() - startTime
        val seconds = (uptimeMs / 1000) % 60
        val minutes = (uptimeMs / (1000 * 60)) % 60
        val hours = (uptimeMs / (1000 * 60 * 60))
        val uptimeStr = "${hours}h ${minutes}m ${seconds}s"

        // Read battery stats
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()).toInt() else 0
        
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0
        
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Generate HTML
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>PocketHost Dashboard</title>
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #e2e8f0; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
                    .card { background: #1e293b; padding: 2rem; border-radius: 1rem; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.5); width: 300px; border: 1px solid #334155; }
                    h1 { margin-top: 0; font-size: 1.5rem; color: #10b981; text-align: center; }
                    .stat { display: flex; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid #334155; font-size: 0.95rem;}
                    .stat:last-child { border-bottom: none; }
                    .label { color: #94a3b8; }
                    .value { font-weight: bold; font-family: monospace; }
                    .status-ok { color: #10b981; }
                    .status-warn { color: #ef4444; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>● Server Online</h1>
                    <div class="stat"><span class="label">Uptime</span><span class="value">$uptimeStr</span></div>
                    <div class="stat"><span class="label">Requests</span><span class="value">${requestCount.get()}</span></div>
                    <div class="stat"><span class="label">Battery</span><span class="value ${if (batteryPct < 20) "status-warn" else "status-ok"}">${batteryPct}% ${if(isCharging) "⚡" else ""}</span></div>
                    <div class="stat"><span class="label">Temp</span><span class="value ${if (tempCelsius > 40) "status-warn" else ""}">${String.format(Locale.US, "%.1f", tempCelsius)}°C</span></div>
                    <div style="text-align: center; margin-top: 1.5rem; font-size: 0.75rem; color: #64748b;">PocketHost Admin</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
