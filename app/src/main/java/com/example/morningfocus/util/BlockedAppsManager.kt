package com.example.morningfocus.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Manages the list of user-defined blocked applications
 */
class BlockedAppsManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val lastTimeUsed: Long = 0
    )
    
    /**
     * Get the current list of blocked apps
     */
    fun getBlockedApps(): List<AppInfo> {
        val appsJson = prefs.getString(KEY_BLOCKED_APPS, null) ?: return DEFAULT_BLOCKED_APPS
        val type = object : TypeToken<List<AppInfo>>() {}.type
        return try {
            gson.fromJson<List<AppInfo>>(appsJson, type)
        } catch (e: Exception) {
            DEFAULT_BLOCKED_APPS
        }
    }
    
    /**
     * Add an app to the blocked list
     */
    fun addBlockedApp(packageName: String): Boolean {
        if (packageName.isEmpty()) {
            return false
        }
        
        val currentApps = getBlockedApps().toMutableList()
        
        // Check if app already exists
        if (currentApps.any { it.packageName == packageName }) {
            return false
        }
        
        try {
            // Get app info to display its name
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            // Add the new app and save
            currentApps.add(AppInfo(packageName, appName, isSystemApp))
            return saveApps(currentApps)
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Remove an app from the blocked list
     */
    fun removeBlockedApp(packageName: String): Boolean {
        val currentApps = getBlockedApps().toMutableList()
        if (currentApps.removeIf { it.packageName == packageName }) {
            return saveApps(currentApps)
        }
        return false
    }
    
    /**
     * Get list of recently used apps (last 20)
     */
    fun getRecentlyUsedApps(): List<AppInfo> {
        // Check if app has usage stats permission
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
            
        // Get stats from the last month
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -1)
        val startTime = calendar.timeInMillis
        
        // Query usage stats
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, 
            startTime, 
            endTime
        )
        
        if (usageStatsList.isNullOrEmpty()) {
            return emptyList()
        }
        
        // Get package manager to resolve app names
        val pm = context.packageManager
        
        // Process usage stats to get app info with last used time
        val appInfoMap = mutableMapOf<String, AppInfo>()
        
        for (usageStats in usageStatsList) {
            val packageName = usageStats.packageName
            
            // Skip if we already processed this package with a more recent timestamp
            if (appInfoMap.containsKey(packageName) && 
                appInfoMap[packageName]?.lastTimeUsed ?: 0 > usageStats.lastTimeUsed) {
                continue
            }
            
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Filter out system services and background processes
                if (isSystemApp && !packageName.startsWith("com.google") && 
                    !packageName.startsWith("com.android") &&
                    !COMMON_APPS.contains(packageName)) {
                    continue
                }
                
                // Filter out unnamed apps and services
                if (appName.isBlank() || appName == packageName) {
                    continue
                }
                
                appInfoMap[packageName] = AppInfo(
                    packageName = packageName,
                    appName = appName,
                    isSystemApp = isSystemApp,
                    lastTimeUsed = usageStats.lastTimeUsed
                )
            } catch (e: Exception) {
                // Skip apps we can't resolve
                continue
            }
        }
        
        // Return top 20 most recently used apps
        return appInfoMap.values
            .sortedByDescending { it.lastTimeUsed }
            .take(20)
    }
    
    /**
     * Get list of installed apps
     */
    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val result = installedApps.mapNotNull { appInfo ->
            try {
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                
                // Filter out unnamed apps and services
                if (appName.isBlank() || appName == appInfo.packageName) {
                    return@mapNotNull null
                }
                
                AppInfo(appInfo.packageName, appName, isSystemApp)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
        
        return result
    }
    
    /**
     * Search for apps by name
     */
    fun searchApps(query: String): List<AppInfo> {
        if (query.isEmpty()) {
            return emptyList()
        }
        
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // Get blocked apps to filter them out
        val blockedApps = getBlockedApps()
        val blockedPackages = blockedApps.map { it.packageName }
        
        val result = installedApps.mapNotNull { appInfo ->
            try {
                val packageName = appInfo.packageName
                
                // Skip if already blocked
                if (blockedPackages.contains(packageName)) {
                    return@mapNotNull null
                }
                
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Filter by search query
                if (!appName.contains(query, ignoreCase = true) && 
                    !packageName.contains(query, ignoreCase = true)) {
                    return@mapNotNull null
                }
                
                AppInfo(packageName, appName, isSystemApp)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName }
        
        return result.take(50) // Limit results for performance
    }
    
    /**
     * Save the entire list of blocked apps
     */
    private fun saveApps(apps: List<AppInfo>): Boolean {
        val appsJson = gson.toJson(apps)
        return prefs.edit()
            .putString(KEY_BLOCKED_APPS, appsJson)
            .commit()
    }
    
    companion object {
        private const val PREFS_NAME = "blocked_apps_prefs"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        
        // Default apps to suggest blocking
        private val DEFAULT_BLOCKED_APPS = listOf<AppInfo>()
        
        // Common apps that users might want to block even if they're system apps
        private val COMMON_APPS = setOf(
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.zhiliaoapp.musically"
        )
    }
} 