package com.locklens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class Tab(val label: String, val icon: String) {
    Home("Home", "H"),
    Library("Library", "L"),
    Camera("Camera", "C"),
    Settings("Settings", "S")
}

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

    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home.name) }
    var isRecording by rememberSaveable { mutableStateOf(false) }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LockLensBackground
        ) {
            Scaffold(
                containerColor = LockLensBackground,
                bottomBar = {
                    LockLensBottomNavigation(
                        selectedTab = Tab.valueOf(selectedTab),
                        onTabSelected = { selectedTab = it.name }
                    )
                }
            ) { padding ->
                when (Tab.valueOf(selectedTab)) {
                    Tab.Home -> HomeScreen(
                        padding = padding,
                        isRecording = isRecording,
                        onRecordingClick = { isRecording = !isRecording },
                        onOpenSettings = { selectedTab = Tab.Settings.name },
                        onOpenCamera = { selectedTab = Tab.Camera.name }
                    )

                    Tab.Library -> LibraryScreen(padding)
                    Tab.Camera -> CameraScreen(padding)
                    Tab.Settings -> SettingsScreen(padding)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    isRecording: Boolean,
    onRecordingClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Header(onOpenSettings = onOpenSettings)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onRecordingClick,
                modifier = Modifier.size(220.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) LockLensRecording else LockLensPrimary,
                    contentColor = LockLensBackground
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) LockLensBackground else LockLensRecording)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "LIVE" else "REC",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRecording) "Stop\nRecording" else "Start\nRecording",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isRecording) "Recording UI state active. CameraX will be connected next." else "Ready to record",
                color = if (isRecording) LockLensRecording else LockLensSuccess,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusCard("Camera", "Back Camera", LockLensPrimary, onClick = onOpenCamera)
            StatusCard("Resolution", "1080p (1920x1080)", LockLensSecondary)
            StatusCard("Audio", "Mic On", LockLensSuccess)
            StatusCard("Folder", "/LockLens/Records", LockLensSecondary)
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_locklens_symbol),
                contentDescription = "LockLens logo",
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

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
        }

        Text(
            text = "Settings",
            color = LockLensPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onOpenSettings() }
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun LibraryScreen(padding: PaddingValues) {
    SimpleScreen(
        padding = padding,
        title = "Library",
        subtitle = "No recordings yet",
        body = "Start your first screen-off recording with LockLens."
    )
}

@Composable
private fun CameraScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Camera",
            color = LockLensTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "CameraX camera detection will be connected next.",
            color = LockLensTextSecondary,
            fontSize = 14.sp
        )

        StatusCard("Back Camera", "Selected · 1080p available", LockLensPrimary)
        StatusCard("Front Camera", "Available after CameraX scan", LockLensSecondary)
        StatusCard("Back Ultra-Wide", "Will appear if device supports it", LockLensSecondary)
        StatusCard("Back Telephoto", "Will appear if device supports it", LockLensSecondary)
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings",
            color = LockLensTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        StatusCard("Recording", "Default camera, resolution, audio", LockLensPrimary)
        StatusCard("Storage", "Folder and gallery visibility", LockLensSecondary)
        StatusCard("Privacy", "Local-only recordings and legal reminder", LockLensSuccess)
        StatusCard("Battery & Samsung", "Keep recording stable", LockLensSecondary)
        StatusCard("Interface", "AMOLED Graphite", LockLensPrimary)
    }
}

@Composable
private fun SimpleScreen(
    padding: PaddingValues,
    title: String,
    subtitle: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = LockLensTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = subtitle,
            color = LockLensTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = body,
            color = LockLensTextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LockLensBottomNavigation(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = LockLensSurface,
        tonalElevation = 0.dp
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == selectedTab

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(
                        text = tab.icon,
                        color = if (selected) LockLensPrimary else LockLensTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        color = if (selected) LockLensPrimary else LockLensTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
