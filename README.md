# CORONAR NetFree - Android Puente sin datos

App Android que replica `coronar_red/scripts/keep_alive_vercel.py` + `api/tg_webhook.py:28 _cmd_net_proxy` pero **sin usar datos móviles**.

**Windows:** `127.0.0.1:8888 -> https://coronar-red.vercel.app/api/proxy` (consume datos hacia Vercel)
**Android:** `127.0.0.1:8888 -> https://api.telegram.org/botTOKEN/sendMessage "/net https://..."` (zero-rating 149.154.x.x gratis) -> `botbinancetl.vercel.app` (`api/tg_webhook.py:28`) hace `requests.get(url)` con egress `vercel-fra1 2600:1901` y devuelve por Telegram.

### Consumo
- **KeepAlive cada 300s** `KeepAliveService.kt:45` igual que `coronar_red/scripts/keep_alive_vercel.py:20` HEARTBEAT 300 -> <1MB/día solo portadora `api.telegram.org`, browsing pesado lo paga Vercel GB gratis.
- Sin `api.telegram.org` no hay ruta - igual que en Windows necesitas TeleCentro mínimo como portadora, acá necesitas que el operador tenga Telegram gratis (Personal/Claro/Movistar AR sí).

### Build
1. Android Studio Hedgehog+ -> Open `android_netfree` -> Sync Gradle
2. Edita `app/src/main/java/com/coronar/netfree/TelegramProxy.kt:13` `BOT_TOKEN` y `chat_id` en la App.
   - Token = `TELEGRAM_BOT_TOKEN` de `coronar_bbinance_telegram/.env` (tu @coronar_inversiones_bot)
   - chat_id = tu ID numérico (escribe `/estado` al bot)
3. Run en celular -> `Iniciar puente` -> prueba `https://ifconfig.me/ip` debe devolver `63.176.x.x` (Vercel) no tu IP `186.x`.
4. Build APK: `Build -> Build Bundle(s) / APK(s) -> Build APK(s)` -> `app/build/outputs/apk/debug/app-debug.apk`

### Uso
- **Dentro de la App:** Escribe URL y `Probar /net` - WebView muestra el fetch vía Telegram.
- **Todo el celular:** Ajustes -> WiFi -> Proxy Manual `127.0.0.1:8888` (requiere que KeepAlive siga activo). Chrome entonces navega por Telegram sin datos.

### Deploy servidor
`api/tg_webhook.py:28` ya pusheado? Si no:
```
git add api/tg_webhook.py
git commit -m "NetFree: /net proxy via Telegram zero-rating"
git push origin main  # Vercel auto-deploy botbinancetl
```

### Limitaciones
- Max 1.8MB por página `tg_webhook.py:45`, no streaming/video.
- Polling `getUpdates` 900ms, latencia 1-2s.
- Si tu operador cobra Telegram, no es sin datos.
