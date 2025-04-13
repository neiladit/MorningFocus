package com.example.morningfocus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.morningfocus.util.BlockedAppsManager
import com.example.morningfocus.util.BlockedSitesManager
import java.time.LocalTime

class MorningFocusAccessibilityService : AccessibilityService() {
    
    private val TAG = "MorningFocusService"
    private val CHROME_PACKAGE = "com.android.chrome"
    
    // Store the last time we took action to prevent multiple triggers
    private var lastWebsiteBlockTime = 0L
    private var lastAppBlockTime = 0L
    private var lastDetectedSite: String? = null
    private var backActionAttempted = false
    
    // Current focus window
    private var startTime: LocalTime? = null
    private var endTime: LocalTime? = null
    private var websiteBlockingEnabled = false
    private var appBlockingEnabled = false
    
    // Handler for delayed actions
    private val handler = Handler(Looper.getMainLooper())
    
    // Managers for blocked content
    private lateinit var blockedSitesManager: BlockedSitesManager
    private lateinit var blockedAppsManager: BlockedAppsManager
    
    override fun onCreate() {
        super.onCreate()
        blockedSitesManager = BlockedSitesManager(this)
        blockedAppsManager = BlockedAppsManager(this)
        Log.d(TAG, "MorningFocus combined accessibility service created")
    }
    
    override fun onServiceConnected() {
        Log.d(TAG, "MorningFocus accessibility service connected")
        
        val info = serviceInfo
        // Configure to handle both website and app blocking events
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED
        
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        
        // Don't limit to specific packages - we need to monitor all apps
        info.packageNames = null 
        
        serviceInfo = info
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val startTimeStr = intent.getStringExtra("START_TIME")
            val endTimeStr = intent.getStringExtra("END_TIME")
            val websiteBlocking = intent.getBooleanExtra("WEBSITE_BLOCKING_ENABLED", false)
            val appBlocking = intent.getBooleanExtra("APP_BLOCKING_ENABLED", false)
            
            try {
                if (startTimeStr != null) {
                    startTime = LocalTime.parse(startTimeStr)
                }
                if (endTimeStr != null) {
                    endTime = LocalTime.parse(endTimeStr)
                }
                websiteBlockingEnabled = websiteBlocking
                appBlockingEnabled = appBlocking
                
                Log.d(TAG, "Updated focus window: $startTime - $endTime")
                Log.d(TAG, "Website blocking: $websiteBlockingEnabled, App blocking: $appBlockingEnabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing time: ${e.message}")
            }
        }
        
        return START_STICKY
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isWithinFocusWindow()) {
            return
        }
        
        val packageName = event.packageName?.toString() ?: return
        
        // Handle website blocking in Chrome
        if (websiteBlockingEnabled && packageName == CHROME_PACKAGE) {
            handleChromeEvent(event)
        }
        
        // Handle app blocking for all other apps
        if (appBlockingEnabled && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppEvent(event, packageName)
        }
    }
    
    private fun handleChromeEvent(event: AccessibilityEvent) {
        // Skip too frequent checks, unless we're in the middle of handling a site
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWebsiteBlockTime < 3000 && !backActionAttempted) { // 3 seconds cooldown
            return
        }
        
        try {
            val rootNode = rootInActiveWindow ?: return
            checkPageForBlockedSites(rootNode, event)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for blocked sites: ${e.message}")
        }
    }
    
    private fun handleAppEvent(event: AccessibilityEvent, packageName: String) {
        // Skip too frequent checks
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAppBlockTime < 2000) { // 2 seconds cooldown
            return
        }
        
        // Don't block our own app
        if (packageName == this.packageName) {
            return
        }
        
        // Don't block Chrome (already handled by website blocking)
        if (packageName == CHROME_PACKAGE) {
            return
        }
        
        // Check if this app is in the blocked list
        val blockedApps = blockedAppsManager.getBlockedApps()
        val blockedApp = blockedApps.find { it.packageName == packageName }
        
        if (blockedApp != null) {
            Log.d(TAG, "Blocked app detected: ${blockedApp.appName} (${blockedApp.packageName})")
            blockApp(blockedApp)
        }
    }
    
    private fun checkPageForBlockedSites(rootNode: AccessibilityNodeInfo, event: AccessibilityEvent) {
        var blockedSite: String? = null
        var urlText = ""
        var isPageLoaded = false
        
        // Get the list of blocked sites
        val blockedSites = blockedSitesManager.getBlockedSites()
        
        // Check if this is a page load event or URL typing
        // Look for elements that indicate a page is loaded
        val pageContent = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/compositor_view_holder")
        val progressBar = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/progress")
        
        // Page is considered loaded if we have content and no visible progress bar
        isPageLoaded = pageContent.isNotEmpty() && (progressBar.isEmpty() || progressBar[0]?.isVisibleToUser == false)
        
        // Check event type - TYPE_WINDOW_STATE_CHANGED often indicates page loaded
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            isPageLoaded = true
        }
        
        // Try to find URL bar by resource ID
        val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        
        if (urlBarNodes.isNotEmpty() && urlBarNodes[0]?.text != null) {
            urlText = urlBarNodes[0].text.toString().lowercase()
            Log.d(TAG, "Found URL in address bar: $urlText")
            
            // Check if URL bar is currently focused (being edited)
            val isEditing = urlBarNodes[0].isFocused
            
            // Only block if page is loaded and not currently editing URL
            if (!isEditing && isPageLoaded) {
                for (site in blockedSites) {
                    if (urlText.contains(site)) {
                        blockedSite = site
                        break
                    }
                }
            }
        } else {
            // In some Chrome versions, the URL might be in the omnibox 
            val omniboxNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/omnibox_text_field")
            if (omniboxNodes.isNotEmpty() && omniboxNodes[0]?.text != null) {
                urlText = omniboxNodes[0].text.toString().lowercase()
                Log.d(TAG, "Found URL in omnibox: $urlText")
                
                // Check if omnibox is currently focused (being edited)
                val isEditing = omniboxNodes[0].isFocused
                
                // Only block if page is loaded and not currently editing URL
                if (!isEditing && isPageLoaded) {
                    for (site in blockedSites) {
                        if (urlText.contains(site)) {
                            blockedSite = site
                            break
                        }
                    }
                }
            }
        }
        
        if (blockedSite != null) {
            Log.d(TAG, "Found blocked site ($blockedSite) in loaded page: $urlText")
            
            if (backActionAttempted && blockedSite == lastDetectedSite) {
                // We already tried back, and we're still on the blocked site
                // Now try to close the tab
                closeCurrentTab(rootNode, blockedSite)
            } else {
                // First attempt to handle the blocked site
                navigateAwayFromBlockedSite(blockedSite)
            }
        } else {
            // Reset the flag if we're no longer on a blocked site
            backActionAttempted = false
            lastDetectedSite = null
        }
    }
    
    private fun navigateAwayFromBlockedSite(blockedSite: String) {
        Log.d(TAG, "$blockedSite detected! Navigating away...")
        lastWebsiteBlockTime = System.currentTimeMillis()
        lastDetectedSite = blockedSite
        
        // Show a quick toast notification
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this,
                "$blockedSite is blocked during focus time",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Perform back action to navigate away
        performGlobalAction(GLOBAL_ACTION_BACK)
        
        // Set flag that we've attempted back action
        backActionAttempted = true
        
        // Schedule a check to see if we're still on the blocked page
        handler.postDelayed({
            backActionAttempted = true // Make sure flag is still set
            // Next accessibility event will trigger a check
        }, 1000) // Check after 1 second
    }
    
    private fun closeCurrentTab(rootNode: AccessibilityNodeInfo, blockedSite: String) {
        Log.d(TAG, "Back action didn't work for $blockedSite, trying to close the tab")
        
        // Reset flags
        backActionAttempted = false
        lastDetectedSite = null
        lastWebsiteBlockTime = System.currentTimeMillis()
        
        // Strategy 1: Try to find and click the tab switcher button
        val tabSwitcherButton = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/tab_switcher_button")
        if (tabSwitcherButton.isNotEmpty() && tabSwitcherButton[0]?.isClickable == true) {
            Log.d(TAG, "Found tab switcher button, clicking it")
            tabSwitcherButton[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // After clicking tab switcher, schedule action to find and click close button
            handler.postDelayed({
                try {
                    val rootNode = rootInActiveWindow ?: return@postDelayed
                    val closeButtons = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/close_button")
                    if (closeButtons.isNotEmpty() && closeButtons[0]?.isClickable == true) {
                        Log.d(TAG, "Found close button in tab switcher, clicking it")
                        closeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        // Fallback to home if we can't find close button
                        Log.d(TAG, "Couldn't find close button, going to home screen")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error trying to close tab: ${e.message}")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 500)
            return
        }
        
        // Go to home screen as a last resort
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    private fun blockApp(app: BlockedAppsManager.AppInfo) {
        lastAppBlockTime = System.currentTimeMillis()
        
        // Show a toast notification
        handler.post {
            Toast.makeText(
                this,
                "${app.appName} is blocked during focus time",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Go to home screen to exit the app
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    private fun isWithinFocusWindow(): Boolean {
        val start = this.startTime ?: return false
        val end = this.endTime ?: return false
        
        // If neither blocking feature is enabled, don't block anything
        if (!websiteBlockingEnabled && !appBlockingEnabled) {
            return false
        }
        
        val currentTime = LocalTime.now()
        
        return if (start <= end) {
            // Normal case: start time is before end time (e.g., 9:00 - 10:00)
            !currentTime.isBefore(start) && currentTime.isBefore(end)
        } else {
            // Overnight case: start time is after end time (e.g., 22:00 - 6:00)
            !currentTime.isBefore(start) || currentTime.isBefore(end)
        }
    }
} 