package com.coronar.netfree

import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Proxy local 127.0.0.1:8888 -> Telegram -> Vercel fra1
 * Replica coronar_red/scripts/keep_alive_vercel.py:238 ThreadedHTTPServer 0.0.0.0:8888
 * En Windows: browser proxy 127.0.0.1:8888 -> requests.request -> Vercel
 * En Android: WebView/Chrome proxy 127.0.0.1:8888 -> TelegramProxy.fetchViaTelegram()
 * Todo el tráfico sale por api.telegram.org (149.154.x.x gratis, no descuenta datos)
 */
class LocalProxyServer(
    private val port: Int = 8888,
    private val chatId: String,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var running = false
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (running) return
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                log("Proxy escuchando 127.0.0.1:$port -> Telegram -> Vercel fra1 2600:1901")
                while (running) {
                    val sock = serverSocket?.accept() ?: break
                    executor.submit { handleClient(sock) }
                }
            } catch (e: Exception) { log("Proxy error: ${e.message}") }
        }.start()
    }

    fun stop() { running = false; try { serverSocket?.close() } catch(_:Exception){} }

    private fun handleClient(sock: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(sock.getInputStream()))
            val output = sock.getOutputStream()
            val requestLine = input.readLine() ?: return
            // GET http://example.com/path HTTP/1.1  o  GET /path HTTP/1.1
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            var target = parts[1]
            // Lee headers hasta \r\n
            var host = ""
            var line: String?
            while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Host:", true)) host = line!!.substringAfter(":").trim()
            }
            if (!target.startsWith("http")) {
                if (host.isNotEmpty()) target = "https://$host$target"
                else { sendError(output, 400, "URL invalida"); return }
            }
            log("Proxy $target")
            // Fetch vía Telegram (bloqueante pero en thread pool)
            val result = runBlocking { TelegramProxy.fetchViaTelegram(chatId, target) }
            if (result.isSuccess) {
                val body = result.getOrNull() ?: ""
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bodyBytes.size}\r\nX-Via: vercel-2600:1901-via-telegram\r\nConnection: close\r\n\r\n"
                output.write(headers.toByteArray())
                output.write(bodyBytes)
                output.flush()
                log("Proxy OK ${bodyBytes.size}B")
            } else {
                sendError(output, 502, "Telegram proxy error: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) { try { sendError(sock.getOutputStream(), 500, e.message ?: "error") } catch(_:Exception){} }
        finally { try { sock.close() } catch(_:Exception){} }
    }

    private fun sendError(out: OutputStream, code: Int, msg: String) {
        val body = "<h1>$code</h1><p>$msg</p><p>via Telegram -> Vercel fra1</p>"
        val hdr = "HTTP/1.1 $code Error\r\nContent-Type: text/html\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
        out.write(hdr.toByteArray()); out.write(body.toByteArray()); out.flush()
    }

    private fun log(s: String) { android.util.Log.i("CORONAR-Proxy", s) }
}
