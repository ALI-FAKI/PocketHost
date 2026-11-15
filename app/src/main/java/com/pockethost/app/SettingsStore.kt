package com.pockethost.app

import android.content.Context

object SettingsStore {
    private const val PREFS = "pockethost_prefs"
    private const val K_FOLDER = "folder"
    private const val K_PORT = "port"
    private const val K_AUTOSTART = "autostart"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFolderPath(ctx: Context): String? = prefs(ctx).getString(K_FOLDER, null)
    fun setFolderPath(ctx: Context, path: String) =
        prefs(ctx).edit().putString(K_FOLDER, path).apply()

    fun getPort(ctx: Context): Int = prefs(ctx).getInt(K_PORT, 8080)
    fun setPort(ctx: Context, port: Int) =
        prefs(ctx).edit().putInt(K_PORT, port).apply()

    fun getAutoStart(ctx: Context): Boolean = prefs(ctx).getBoolean(K_AUTOSTART, true)
    fun setAutoStart(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(K_AUTOSTART, enabled).apply()
}
