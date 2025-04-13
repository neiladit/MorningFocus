package com.example.morningfocus

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.morningfocus.service.FocusService
import com.example.morningfocus.service.MorningFocusAccessibilityService
import com.example.morningfocus.util.WebsiteBlocker
import com.example.morningfocus.util.BlockedSitesManager
import com.example.morningfocus.util.BlockedAppsManager
import com.example.morningfocus.util.SettingsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                TimeApp()
            }
        }
    }
}

@Composable
fun TimeApp() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var startTime by remember { mutableStateOf(settingsManager.getStartTime()) }
    var endTime by remember { mutableStateOf(settingsManager.getEndTime()) }
    var windowSet by remember { mutableStateOf(settingsManager.isWindowSet()) }
    var tempStartTime by remember { mutableStateOf(startTime) }
    var tempEndTime by remember { mutableStateOf(endTime) }
    var blockingEnabled by remember { mutableStateOf(settingsManager.isBlockingEnabled()) }
    var appBlockingEnabled by remember { mutableStateOf(settingsManager.isAppBlockingEnabled()) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showBlockedSitesDialog by remember { mutableStateOf(false) }
    var showBlockedAppsDialog by remember { mutableStateOf(false) }
    
    // Check if current time is within focus window
    val isInFocusWindow = remember(currentTime, startTime, endTime) {
        if (startTime.isBefore(endTime)) {
            // Normal case: start time is before end time (e.g., 9:00 - 10:00)
            !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
        } else {
            // Overnight case: start time is after end time (e.g., 22:00 - 6:00)
            !currentTime.isBefore(startTime) || currentTime.isBefore(endTime)
        }
    }
    
    val blockedSitesManager = remember { BlockedSitesManager(context) }
    val blockedAppsManager = remember { BlockedAppsManager(context) }
    val websiteBlocker = remember { WebsiteBlocker(context) }
    var hasPermission by remember { mutableStateOf(websiteBlocker.hasUsageStatsPermission()) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = websiteBlocker.hasUsageStatsPermission()
    }
    
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = LocalTime.now()
            hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
            delay(1000)
        }
    }
    
    // Start or stop the service when focus window changes
    LaunchedEffect(windowSet, startTime, endTime, blockingEnabled, appBlockingEnabled) {
        if (windowSet) {
            if (blockingEnabled || appBlockingEnabled) {
                // First update the regular focus service
                val intent = Intent(context, FocusService::class.java).apply {
                    putExtra("start_time", startTime.toString())
                    putExtra("end_time", endTime.toString())
                }
                context.startService(intent)
                
                // Then update the combined accessibility service
                if (hasAccessibilityPermission) {
                    updateMorningFocusService(context, startTime, endTime, blockingEnabled, appBlockingEnabled)
                }
            } else {
                context.stopService(Intent(context, FocusService::class.java))
            }
        } else {
            context.stopService(Intent(context, FocusService::class.java))
        }
    }
    
    // Check if accessibility permission is needed and show dialog
    LaunchedEffect(blockingEnabled, appBlockingEnabled, hasAccessibilityPermission) {
        if ((blockingEnabled || appBlockingEnabled) && !hasAccessibilityPermission) {
            showAccessibilityDialog = true
        }
    }
    
    if (showAccessibilityDialog) {
        Dialog(onDismissRequest = { showAccessibilityDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = context.getString(R.string.accessibility_permission_title),
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = context.getString(R.string.accessibility_permission_message),
                        style = TextStyle(fontSize = 16.sp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        SimpleButton(
                            onClick = { showAccessibilityDialog = false },
                            text = context.getString(R.string.accessibility_permission_cancel)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        SimpleButton(
                            onClick = {
                                showAccessibilityDialog = false
                                openAccessibilitySettings(context)
                            },
                            text = context.getString(R.string.accessibility_permission_button)
                        )
                    }
                }
            }
        }
    }
    
    // Add dialog to manage blocked sites
    if (showBlockedSitesDialog) {
        BlockedSitesDialog(
            blockedSites = blockedSitesManager.getBlockedSites(),
            onAddSite = { site ->
                if (blockedSitesManager.addBlockedSite(site)) {
                    blockedSitesManager.getBlockedSites()
                }
            },
            onRemoveSite = { site ->
                if (blockedSitesManager.removeBlockedSite(site)) {
                    blockedSitesManager.getBlockedSites()
                }
            },
            onDismiss = { showBlockedSitesDialog = false }
        )
    }
    
    // Add dialog to manage blocked apps
    if (showBlockedAppsDialog) {
        BlockedAppsDialog(
            blockedApps = blockedAppsManager.getBlockedApps(),
            onAddApp = { packageName ->
                if (blockedAppsManager.addBlockedApp(packageName)) {
                    blockedAppsManager.getBlockedApps()
                }
            },
            onRemoveApp = { packageName ->
                if (blockedAppsManager.removeBlockedApp(packageName)) {
                    blockedAppsManager.getBlockedApps()
                }
            },
            onDismiss = { showBlockedAppsDialog = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current time display
        Text(
            text = "Current Time",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
        )
        
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = TextStyle(
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Focus window active notification
        if (windowSet && isInFocusWindow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFE0B2)) // Light orange background
                    .border(2.dp, Color(0xFFFF9800), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FOCUS WINDOW ACTIVE",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Please don't open distracting apps",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color(0xFF333333)
                        ),
                        textAlign = TextAlign.Center
                    )
                    
                    if (blockingEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Distracting websites are being blocked (${blockedSitesManager.getBlockedSites().size} sites)",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Time window input
        Text(
            text = "Set Focus Window",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Start Time",
                    style = TextStyle(
                        fontSize = 16.sp
                    )
                )
                
                TimeDisplay(
                    time = tempStartTime,
                    onClick = {
                        val timePickerDialog = TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                tempStartTime = LocalTime.of(hourOfDay, minute)
                            },
                            tempStartTime.hour,
                            tempStartTime.minute,
                            false // Change to false for 12-hour format with AM/PM
                        )
                        timePickerDialog.show()
                    }
                )
            }
            
            Text(
                text = "to",
                style = TextStyle(fontSize = 18.sp),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "End Time",
                    style = TextStyle(
                        fontSize = 16.sp
                    )
                )
                
                TimeDisplay(
                    time = tempEndTime,
                    onClick = {
                        val timePickerDialog = TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                tempEndTime = LocalTime.of(hourOfDay, minute)
                            },
                            tempEndTime.hour,
                            tempEndTime.minute,
                            false // Change to false for 12-hour format with AM/PM
                        )
                        timePickerDialog.show()
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Blocking options section
        Text(
            text = "Blocking Options",
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // Website blocking option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Block Distracting Websites",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = blockingEnabled,
                    onCheckedChange = { isEnabled ->
                        blockingEnabled = isEnabled
                        settingsManager.saveBlockingEnabled(isEnabled)
                        if (isEnabled && !hasAccessibilityPermission) {
                            requestAllAccessibilityPermissions(context)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                CustomOutlinedButton(
                    onClick = { showBlockedSitesDialog = true },
                    text = "Manage Blocked Sites"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // App blocking option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Block Distracting Apps",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = appBlockingEnabled,
                    onCheckedChange = { isEnabled ->
                        appBlockingEnabled = isEnabled
                        settingsManager.saveAppBlockingEnabled(isEnabled)
                        if (isEnabled && !hasAccessibilityPermission) {
                            requestAllAccessibilityPermissions(context)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                CustomOutlinedButton(
                    onClick = { showBlockedAppsDialog = true },
                    text = "Manage Blocked Apps"
                )
            }
        }
        
        // Permission warnings if blocking is enabled
        if ((blockingEnabled || appBlockingEnabled) && (!hasPermission || !hasAccessibilityPermission)) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFEBEE)) // Light red background
                    .border(1.dp, Color.Red, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Missing Permissions",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!hasPermission) {
                        Text(
                            text = "Usage access permission needed",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = Color.Red
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SimpleButton(
                            onClick = { 
                                permissionLauncher.launch(websiteBlocker.getUsageStatsPermissionIntent())
                            },
                            text = "Grant Usage Permission",
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (!hasAccessibilityPermission) {
                        Text(
                            text = "Accessibility permission needed",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = Color.Red
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "This is required to detect and block distracting content",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SimpleButton(
                            onClick = { 
                                requestAllAccessibilityPermissions(context)
                            },
                            text = "Grant Accessibility Permission",
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        SimpleButton(
            onClick = { 
                startTime = tempStartTime
                endTime = tempEndTime
                windowSet = true
                
                // Save settings
                settingsManager.saveStartTime(startTime)
                settingsManager.saveEndTime(endTime)
                settingsManager.saveWindowSet(true)
                
                // Update accessibility service with focus window
                if (hasAccessibilityPermission) {
                    updateMorningFocusService(context, startTime, endTime, blockingEnabled, appBlockingEnabled)
                }
            },
            text = "Set Focus Window",
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Show the set focus window
        if (windowSet) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE3F2FD))
                    .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Focus Window: ${startTime.format(DateTimeFormatter.ofPattern("h:mm a"))} to ${endTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF1565C0)
                    )
                )
            }
        }
    }
}

@Composable
fun TimeDisplay(
    time: LocalTime,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun SimpleButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2196F3))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun Text(
    text: String,
    style: TextStyle = TextStyle(fontSize = 16.sp),
    color: Color = Color.Black,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            textAlign = textAlign ?: style.textAlign
        )
    )
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) Color(0xFF2196F3) else Color(0xFFE0E0E0)
    val thumbColor = if (checked) Color(0xFFFFFFFF) else Color(0xFFBDBDBD)
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .size(width = 52.dp, height = 32.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (checked) 24.dp else 4.dp, end = if (checked) 4.dp else 24.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
fun BlockedSitesDialog(
    blockedSites: List<String>,
    onAddSite: (String) -> Unit,
    onRemoveSite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newSiteText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Manage Blocked Websites",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input field for new site
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = newSiteText,
                            onValueChange = { 
                                newSiteText = it
                                errorMessage = ""
                            },
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        )
                        
                        if (newSiteText.isEmpty()) {
                            Text(
                                text = "Enter website domain (e.g., twitter.com)",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Add button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3))
                            .clickable { 
                                if (newSiteText.isNotEmpty()) {
                                    onAddSite(newSiteText)
                                    newSiteText = ""
                                } else {
                                    errorMessage = "Please enter a website domain"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color.White,
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color.Red
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // List of current blocked sites
                Text(
                    text = "Currently Blocked:",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (blockedSites.isEmpty()) {
                    Text(
                        text = "No websites in blocklist yet",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        items(blockedSites) { site ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = site,
                                    style = TextStyle(fontSize = 16.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252))
                                        .clickable { onRemoveSite(site) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "×",
                                        color = Color.White,
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Done button
                SimpleButton(
                    onClick = onDismiss,
                    text = "Done",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun BlockedAppsDialog(
    blockedApps: List<BlockedAppsManager.AppInfo>,
    onAddApp: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val blockedAppsManager = remember { BlockedAppsManager(context) }
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    
    // Get recently used apps
    val recentApps = remember { blockedAppsManager.getRecentlyUsedApps() }
    
    // Filter apps based on search
    val searchResults = remember(searchText, blockedApps) {
        if (searchText.isEmpty()) {
            emptyList()
        } else {
            blockedAppsManager.searchApps(searchText)
        }
    }
    
    // Show recent apps when not searching, show search results when searching
    val appsToShow = if (searchText.isEmpty()) {
        recentApps.filter { app -> 
            !blockedApps.any { it.packageName == app.packageName }
        }
    } else {
        searchResults
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Manage Blocked Apps",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    BasicTextField(
                        value = searchText,
                        onValueChange = { 
                            searchText = it
                            isSearching = it.isNotEmpty() 
                        },
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                    )
                    
                    if (searchText.isEmpty()) {
                        Text(
                            text = "Search apps...",
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // List of current blocked apps
                Text(
                    text = "Currently Blocked:",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (blockedApps.isEmpty()) {
                    Text(
                        text = "No apps in blocklist yet",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(blockedApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.appName,
                                    style = TextStyle(fontSize = 16.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5252))
                                        .clickable { onRemoveApp(app.packageName) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "×",
                                        color = Color.White,
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Available apps section
                Text(
                    text = if (searchText.isEmpty()) "Recently Used Apps:" else "Search Results:",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                if (searchText.isNotEmpty() && searchResults.isEmpty()) {
                    Text(
                        text = "No matching apps found",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(appsToShow) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onAddApp(app.packageName) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appName,
                                style = TextStyle(fontSize = 16.sp),
                                modifier = Modifier.weight(1f)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                                    .clickable { onAddApp(app.packageName) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    color = Color.White,
                                    style = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Done button
                SimpleButton(
                    onClick = onDismiss,
                    text = "Done",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// Accessibility service helper function
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val componentName = ComponentName(context, MorningFocusAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    
    return enabledServices?.contains(componentName.flattenToString()) == true
}

// Open accessibility settings
fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val componentName = ComponentName(context, MorningFocusAccessibilityService::class.java).flattenToString()
    val bundle = Bundle()
    bundle.putString(":settings:fragment_args_key", componentName)
    intent.putExtra(":settings:fragment_args_key", componentName)
    intent.putExtra(":settings:show_fragment_args", bundle)
    context.startActivity(intent)
}

// Update the combined accessibility service
fun updateMorningFocusService(context: Context, start: LocalTime, end: LocalTime, websiteBlocking: Boolean, appBlocking: Boolean) {
    val intent = Intent(context, MorningFocusAccessibilityService::class.java).apply {
        putExtra("START_TIME", start.toString())
        putExtra("END_TIME", end.toString())
        putExtra("WEBSITE_BLOCKING_ENABLED", websiteBlocking)
        putExtra("APP_BLOCKING_ENABLED", appBlocking)
    }
    context.startService(intent)
}

// Add a sequential permission helper
private fun requestAllAccessibilityPermissions(context: Context) {
    val builder = AlertDialog.Builder(context)
    builder.setTitle("Accessibility Permission")
        .setMessage("Morning Focus needs accessibility permission to block distracting websites and apps during your focus time.")
        .setPositiveButton("Continue") { _, _ ->
            if (!isAccessibilityServiceEnabled(context)) {
                openAccessibilitySettings(context)
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

// Add custom outlined button component
@Composable
fun CustomOutlinedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF2196F3),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
} 