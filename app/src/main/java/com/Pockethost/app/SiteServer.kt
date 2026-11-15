package com.pockethost.app

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class SiteServer(port: Int, private val root: File) : NanoHTTPD(port) {

    private val rootCanonical: String = root.canonicalPath

    override fun serve(session: IHTTPSession): Response {
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

        val file = if (target.isDirectory) File(target, "index.html") else target

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
}
