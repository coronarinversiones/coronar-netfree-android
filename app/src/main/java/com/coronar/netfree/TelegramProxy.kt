package com.coronar.netfree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Puente SIN DATOS via Telegram Bot API (zero-rating 149.154.0.0/16)
 * Replica coronar_red/api/proxy.py pero tunelizado por @coronar_inversiones_bot
 * - En Windows: keep_alive_vercel.py:17 -> https://coronar-red.vercel.app/api/proxy
 * - En Android: TelegramProxy -> https://api.telegram.org/bot<TOKEN>/sendMessage con "/net https://..."
 *   El bot en api/tg_webhook.py:28 _cmd_net_proxy() hace requests.get(url) con egress vercel-fra1 2600:1901
 *   y te devuelve por Telegram (gratis), sin tocar datos móviles ni TeleCentro pesado.
 */
object TelegramProxy {
    // Hardcodeado como en keep_alive_vercel.py:17 - mismo bot que ya tenés deployado
    const val BOT_TOKEN = "8529985492:AAFmCIIQunl..." // <-- REEMPLAZA con tu TELEGRAM_BOT_TOKEN real de coronar_bbinance_telegram/.env
    const val API_BASE = "https://api.telegram.org/bot$BOT_TOKEN"
    private const val API_BASE_REAL get() = "https://api.telegram.org/bot$BOT_TOKEN"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private fun apiUrl(method: String) = "https://api.telegram.org/bot$BOT_TOKEN/$method"

    /**
     * Envía URL a Vercel vía Telegram y espera respuesta.
     * Usa modo polling getUpdates (sin webhook) para mantener todo por api.telegram.org gratis.
     */
    suspend fun fetchViaTelegram(chatId: String, url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1) Enviar /net <url> al bot (zero-rating)
            val sendUrl = apiUrl("sendMessage")
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", "/net $url")
            }
            val req = Request.Builder()
                .url(sendUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp1 = client.newCall(req).execute()
            if (!resp1.isSuccessful) return@withContext Result.failure(Exception("sendMessage ${resp1.code}"))

            // 2) Polling getUpdates hasta recibir respuesta del bot (max 15s)
            val offsetKey = "netfree_offset"
            var offset = 0L
            val deadline = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(900)
                val pollReq = Request.Builder()
                    .url(apiUrl("getUpdates") + "?timeout=5&offset=$offset&limit=10")
                    .get().build()
                val pollResp = client.newCall(pollReq).execute()
                if (!pollResp.isSuccessful) continue
                val j = JSONObject(pollResp.body?.string() ?: "{}")
                val arr = j.optJSONArray("result") ?: continue
                for (i in 0 until arr.length()) {
                    val upd = arr.getJSONObject(i)
                    offset = upd.optLong("update_id", 0) + 1
                    val msg = upd.optJSONObject("message") ?: continue
                    val fromChat = msg.optJSONObject("chat")?.optString("id") ?: ""
                    // filtra solo mensajes del bot hacia este chat
                    if (fromChat != chatId) continue
                    val text = msg.optString("text", "")
                    if (text.contains("via vercel-fra1") || text.contains("via vercel")) {
                        // Marca como leído
                        return@withContext Result.success(text)
                    }
                    // Si es documento (html grande) viene como document
                    if (msg.has("document")) {
                        val doc = msg.getJSONObject("document")
                        val fileId = doc.optString("file_id")
                        // getFile -> download
                        val fileUrl = getFileUrl(fileId)
                        if (fileUrl != null) {
                            val fileContent = downloadFile(fileUrl)
                            return@withContext Result.success(fileContent)
                        }
                    }
                }
            }
            Result.failure(Exception("timeout esperando respuesta Vercel (15s)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileUrl(fileId: String): String? {
        return try {
            val req = Request.Builder().url(apiUrl("getFile") + "?file_id=${URLEncoder.encode(fileId,"UTF-8")}").get().build()
            val r = client.newCall(req).execute()
            val j = JSONObject(r.body?.string() ?: "")
            val path = j.optJSONObject("result")?.optString("file_path") ?: return null
            "https://api.telegram.org/file/bot$BOT_TOKEN/$path"
        } catch (_: Exception) { null }
    }

    private fun downloadFile(url: String): String {
        val req = Request.Builder().url(url).get().build()
        val r = client.newCall(req).execute()
        return r.body?.string()?.take(8000) ?: ""
    }

    /** Heartbeat mínimo portadora: mantiene Bot+ Vercel vivo sin datos (igual que keep_alive_vercel.py:104) */
    suspend fun heartbeat(chatId: String): Boolean {
        val r = fetchViaTelegram(chatId, "https://ifconfig.me/ip")
        return r.isSuccess
    }
}
