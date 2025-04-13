package com.example.morningfocus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.morningfocus.MainActivity
import com.example.morningfocus.R
import com.example.morningfocus.ui.BlockingActivity
import java.time.LocalTime

class FocusService : Service() {
    private val TAG = "FocusService"
    private val CHANNEL_ID = "FocusServiceChannel"
    private val NOTIFICATION_ID = 1
    
    private val CHROME_PACKAGE = "com.android.chrome"
    private val BLOCKED_SITES = listOf("reddit.com")
    
    private var startTime: LocalTime? = null
    private var endTime: LocalTime? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Check interval (5 seconds)
    private val CHECK_INTERVAL: Long = 5000
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForegroundApps()
            if (isRunning) {
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_SERVICE" -> {
                startTime = intent.getSerializableExtra("START_TIME") as? LocalTime
                endTime = intent.getSerializableExtra("END_TIME") as? LocalTime
                
                if (!isRunning) {
                    isRunning = true
                    startForeground(NOTIFICATION_ID, createNotification())
                    handler.post(checkRunnable)
                    Log.d(TAG, "Focus service started. Focus window: $startTime - $endTime")
                }
            }
            "STOP_SERVICE" -> {
                stopService()
            }
        }
        
        return START_STICKY
    }
    
    private fun stopService() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Focus service stopped")
    }
    
    private fun checkForegroundApps() {
        if (!isWithinFocusWindow()) {
            Log.d(TAG, "Outside focus window, stopping service")
            stopService()
            return
        }
        
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Get usage events from the last 10 seconds
        val usageEvents = usageStatsManager.queryEvents(time - 10000, time)
        val event = UsageEvents.Event()
        
        var isChromeInForeground = false
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && 
                event.packageName == CHROME_PACKAGE) {
                isChromeInForeground = true
                checkChromeForBlockedSites()
                break
            }
        }
        
        Log.d(TAG, "Chrome foreground check: $isChromeInForeground")
    }
    
    private fun checkChromeForBlockedSites() {
        // This is a simplified implementation. To actually check URLs in Chrome,
        // we would need a content provider or accessibility service, which is beyond
        // the scope of this example. Instead, we'll just show a blocking notification
        // when Chrome is opened during focus time.
        
        val blockingIntent = Intent(this, BlockingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("BLOCKED_SITE", "reddit.com")
        }
        startActivity(blockingIntent)
        
        Log.d(TAG, "Chrome detected during focus time, showing blocking screen")
    }
    
    private fun isWithinFocusWindow(): Boolean {
        val startTime = this.startTime ?: return false
        val endTime = this.endTime ?: return false
        val currentTime = LocalTime.now()
        
        return if (startTime <= endTime) {
            currentTime in startTime..endTime
        } else {
            // Handle case where focus window spans midnight
            currentTime >= startTime || currentTime <= endTime
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Service Channel"
            val descriptionText = "Channel for Focus Service notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Mode Active")
            .setContentText("Blocking distracting websites")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(checkRunnable)
    }
} 