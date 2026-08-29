package com.coronar.netfree

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var etChatId: EditText
    private lateinit var etUrl: EditText
    private lateinit var tvLog: TextView
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout programático simple (sin XML para scaffold rápido)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        etChatId = EditText(this).apply { hint = "Telegram chat_id (tu ID numérico)"; setText("") }
        val btnStart = Button(this).apply { text = "▶ Iniciar puente sin datos (KeepAlive 5m + Proxy 8888)" }
        val btnStop = Button(this).apply { text = "■ Detener puente" }
        etUrl = EditText(this).apply { hint = "https://ifconfig.me  o https://api.binance.com/..."; setText("https://ifconfig.me/ip") }
        val btnFetch = Button(this).apply { text = "🌐 Probar /net vía Telegram (sin datos)" }
        tvLog = TextView(this).apply { text = "Log: listo. Configura chat_id.\nTip: habla con @coronar_inversiones_bot y usa /estado para ver tu chat_id." }
        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        root.addView(TextView(this).apply { text = "CORONAR NetFree - Puente Vercel via Telegram"; textSize = 16f })
        root.addView(etChatId); root.addView(btnStart); root.addView(btnStop)
        root.addView(TextView(this).apply { text = "Prueba rápida /net:" }); root.addView(etUrl); root.addView(btnFetch)
        root.addView(tvLog)
        val scroll = ScrollView(this).apply { addView(webView.apply { layoutParams = LinearLayout.LayoutParams(-1, 800) }) }
        root.addView(scroll)
        setContentView(ScrollView(this).apply { addView(root) })

        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        btnStart.setOnClickListener {
            val chatId = etChatId.text.toString().trim()
            if (chatId.isBlank()) { toast("Ingresá tu chat_id de Telegram"); return@setOnClickListener }
            val svc = Intent(this, KeepAliveService::class.java).putExtra(KeepAliveService.CHAT_ID_EXTRA, chatId)
            startForegroundService(svc)
            tvLog.text = "✓ Puente iniciado. Proxy 127.0.0.1:8888 -> Telegram -> Vercel fra1\nKeepAlive cada 5m (<1MB/día portadora)\nUsa el navegador de esta App o configura proxy del sistema a 127.0.0.1:8888"
            // Guarda chat_id
            getPreferences(MODE_PRIVATE).edit().putString("chat_id", chatId).apply()
        }
        btnStop.setOnClickListener {
            stopService(Intent(this, KeepAliveService::class.java))
            tvLog.text = "■ Puente detenido"
        }
        btnFetch.setOnClickListener {
            val chatId = etChatId.text.toString().trim()
            val url = etUrl.text.toString().trim()
            if (chatId.isBlank()) { toast("Falta chat_id"); return@setOnClickListener }
            tvLog.text = "⏳ Fetch $url vía Telegram..."
            scope.launch {
                val r = withContext(Dispatchers.IO) { TelegramProxy.fetchViaTelegram(chatId, url) }
                if (r.isSuccess) {
                    val body = r.getOrNull() ?: ""
                    tvLog.text = "✓ via vercel-fra1\n${body.take(3000)}"
                    webView.loadDataWithBaseURL(url, body, "text/html", "utf-8", null)
                } else {
                    tvLog.text = "✗ Error: ${r.exceptionOrNull()?.message}"
                }
            }
        }
        // restaura chat_id
        etChatId.setText(getPreferences(MODE_PRIVATE).getString("chat_id",""))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
