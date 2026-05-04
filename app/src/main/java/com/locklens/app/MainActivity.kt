package com.locklens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LockLensBackground = Color(0xFF05070A)
private val LockLensSurface = Color(0xFF10151C)
private val LockLensSurfaceElevated = Color(0xFF171E28)
private val LockLensPrimary = Color(0xFF20D6FF)
private val LockLensSecondary = Color(0xFF6C7DFF)
private val LockLensRecording = Color(0xFFFF4D5E)
private val LockLensSuccess = Color(0xFF2EE6A6)
private val LockLensTextPrimary = Color(0xFFF4F7FB)
private val LockLensTextSecondary = Color(0xFF9CA8B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LockLensApp()
        }
    }
}

@Composable
private fun LockLensApp() {
    val colorScheme = darkColorScheme(
        primary = LockLensPrimary,
        secondary = LockLensSecondary,
        background = LockLensBackground,
        surface = LockLensSurface,
        surfaceVariant = LockLensSurfaceElevated,
        onPrimary = LockLensBackground,
        onSecondary = LockLensTextPrimary,
        onBackground = LockLensTextPrimary,
        onSurface = LockLensTextPrimary,
        onSurfaceVariant = LockLensTextSecondary,
        error = LockLensRecording
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LockLensBackground
        ) {
            Scaffold(
                containerColor = LockLensBackground,
                bottomBar = { LockLensBottomNavigation() }
            ) { padding ->
                HomeScreen(padding)
            }
        }
    }
}

@Composable
private fun HomeScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LockLens",
                        color = LockLensTextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Record smarter. Save battery.",
                        color = LockLensTextSecondary,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = "Settings",
                    color = LockLensPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .width(220.dp)
                        .height(220.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LockLensPrimary,
                        contentColor = LockLensBackground
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(LockLensRecording)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REC",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Start\nRecording",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusCard("Camera", "Back Camera", LockLensPrimary)
                StatusCard("Resolution", "1080p (1920x1080)", LockLensSecondary)
                StatusCard("Audio", "Mic On", LockLensSuccess)
                StatusCard("Folder", "/LockLens/Records", LockLensSecondary)
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LockLensSurface),
        border = BorderStroke(1.dp, LockLensSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = LockLensTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = LockLensTextSecondary,
                    fontSize = 14.sp
                )
            }

            Spacer(
                modifier = Modifier
                    .width(12.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun LockLensBottomNavigation() {
    NavigationBar(
        containerColor = LockLensSurface,
        tonalElevation = 0.dp
    ) {
        val items = listOf("Home", "Library", "Camera", "Settings")

        items.forEachIndexed { index, label ->
            val selected = index == 0

            NavigationBarItem(
                selected = selected,
                onClick = { },
                icon = {
                    Text(
                        text = label.first().toString(),
                        color = if (selected) LockLensPrimary else LockLensTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                },
                label = {
                    Text(
                        text = label,
                        color = if (selected) LockLensPrimary else LockLensTextSecondary,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LockLensPrimary,
                    selectedTextColor = LockLensPrimary,
                    unselectedIconColor = LockLensTextSecondary,
                    unselectedTextColor = LockLensTextSecondary,
                    indicatorColor = LockLensSurfaceElevated
                )
            )
        }
    }
}
