package com.out.androidtvremote.utils

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.SimpleWebServer
import java.io.File

class WebServer(localAddr: String?, port: Int, wwwroot: File?) :
    SimpleWebServer(localAddr, port, wwwroot, true, "*") {
    override fun useGzipWhenAccepted(r: NanoHTTPD.Response): Boolean {
        return super.useGzipWhenAccepted(r) && r.status !== NanoHTTPD.Response.Status.NOT_MODIFIED
    }
}
