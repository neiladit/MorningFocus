package com.example.morningfocus.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalTime

class WebsiteBlocker(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val blockedDomains = listOf("reddit.com")
    private val chromePackage = "com.android.chrome"

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun getUsageStatsPermissionIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun monitorChromeUsage(startTime: LocalTime, endTime: LocalTime): Flow<Boolean> = flow {
        while (true) {
            val currentTime = LocalTime.now()
            val isInFocusWindow = if (startTime.isBefore(endTime)) {
                !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
            } else {
                !currentTime.isBefore(startTime) || currentTime.isBefore(endTime)
            }

            if (!isInFocusWindow) {
                emit(false)
                delay(1000)
                continue
            }

            val systemEndTime = System.currentTimeMillis()
            val systemStartTime = systemEndTime - 1000 // Check last second

            val usageEvents = usageStatsManager.queryEvents(systemStartTime, systemEndTime)
            val event = UsageEvents.Event()

            var isBlocking = false
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.packageName == chromePackage && event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    // Chrome is in foreground, we should block reddit
                    isBlocking = true
                    break
                }
            }
            
            emit(isBlocking)
            delay(1000) // Check every second
        }
    }

    fun shouldBlockReddit(): Boolean {
        // In a real implementation, this would check the actual URL
        // but for demo purposes, we're just assuming any Chrome usage during focus time
        // might be reddit and showing a warning
        return true
    }
} 