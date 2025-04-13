package com.example.morningfocus.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val blockedSite = intent.getStringExtra("BLOCKED_SITE") ?: "this website"
        
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                BlockingScreen(
                    blockedSite = blockedSite,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun BlockingScreen(
    blockedSite: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDEDED)) // Light red background
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "Focus Mode Active",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB71C1C), // Dark red
                textAlign = TextAlign.Center
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.foundation.text.BasicText(
            text = "$blockedSite is blocked during your focus time",
            style = TextStyle(
                fontSize = 20.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        androidx.compose.foundation.text.BasicText(
            text = "Stay focused on your goals!",
            style = TextStyle(
                fontSize = 16.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2196F3)) // Blue button
                .clickable(onClick = onClose)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.text.BasicText(
                text = "Return to Work",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
} 