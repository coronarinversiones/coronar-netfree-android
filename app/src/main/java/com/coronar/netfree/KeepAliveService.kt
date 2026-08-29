package com.coronar.netfree

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Servicio foreground que mantiene conexión viva SIN DATOS
 * Replica coronar_red/scripts/keep_alive_vercel.py:131 heartbeat_loop(300)
 * Cada 5m hace fetchViaTelegram("https://ifconfig.me/ip") por api.telegram.org (gratis)
 * Mantiene Vercel despierto y Telegram poll activo, con <1MB/día de portadora.
 */
class KeepAliveService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var proxy: LocalProxyServer? = null
    private var job: Job? = null

    companion object {
        const val CHAT_ID_EXTRA = "chat_id"
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "coronar_netfree_keepalive"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chatId = intent?.getStringExtra(CHAT_ID_EXTRA) ?: ""
        if (chatId.isBlank()) { stopSelf(); return START_NOT_STICKY }

        startForeground(NOTIF_ID, buildNotif("Iniciando puente..."))
        // Proxy 127.0.0.1:8888
        proxy = LocalProxyServer(8888, chatId, scope).also { it.start() }

        job?.cancel()
        job = scope.launch {
            var okCount = 0; var failCount = 0
            while (isActive) {
                val ok = try { TelegramProxy.heartbeat(chatId) } catch(_:Exception){ false }
                if (ok) okCount++ else failCount++
                updateNotif("Puente activo | OK:$okCount Fail:$failCount | Proxy 127.0.0.1:8888 -> Vercel via Telegram")
                delay(300_000) // 5m = mínimo portadora como keep_alive_vercel.py:20
            }
        }
        return START_STICKY
    }

    private fun buildNotif(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CORONAR NetFree - Puente sin datos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
    private fun updateNotif(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif(text))
    }
    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "CORONAR Puente", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    override fun onDestroy() { job?.cancel(); proxy?.stop(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
