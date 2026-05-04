package com.locklens.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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
    Home("Главная", "H"),
    Library("Видео", "V"),
    Camera("Камера", "C"),
    Settings("Настройки", "S")
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
    val context = LocalContext.current

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
    var audioEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedLensFacing by rememberSaveable {
        mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
    }
    var statusText by rememberSaveable {
        mutableStateOf("Готово к записи")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = requiredPermissions(audioEnabled).all { permission ->
            result[permission] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
        }

        if (granted) {
            startRecordingService(
                context = context,
                lensFacing = selectedLensFacing,
                audioEnabled = audioEnabled
            )
            isRecording = true
            statusText = "Запуск записи..."
        } else {
            statusText = "Разрешите камеру, уведомления и микрофон для записи со звуком"
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.getStringExtra(RecordingForegroundService.EXTRA_STATE)) {
                    RecordingForegroundService.STATE_PREPARING -> {
                        isRecording = true
                        statusText = "Подготовка камеры..."
                    }

                    RecordingForegroundService.STATE_RECORDING -> {
                        isRecording = true
                        statusText = "Идёт запись. Можно выключить экран."
                    }

                    RecordingForegroundService.STATE_COMPLETED -> {
                        isRecording = false
                        statusText = "Запись сохранена в Видео / LockLens"
                    }

                    RecordingForegroundService.STATE_FAILED -> {
                        isRecording = false
                        statusText = intent.getStringExtra(
                            RecordingForegroundService.EXTRA_MESSAGE
                        ) ?: "Ошибка записи"
                    }
                }
            }
        }

        val filter = IntentFilter(RecordingForegroundService.BROADCAST_RECORDING_STATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

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
                        statusText = statusText,
                        selectedCamera = cameraName(selectedLensFacing),
                        audioEnabled = audioEnabled,
                        onRecordingClick = {
                            if (isRecording) {
                                stopRecordingService(context)
                                statusText = "Останавливаем и сохраняем..."
                            } else {
                                if (hasRequiredPermissions(context, audioEnabled)) {
                                    startRecordingService(
                                        context = context,
                                        lensFacing = selectedLensFacing,
                                        audioEnabled = audioEnabled
                                    )
                                    isRecording = true
                                    statusText = "Запуск записи..."
                                } else {
                                    permissionLauncher.launch(requiredPermissions(audioEnabled))
                                }
                            }
                        },
                        onOpenCamera = { selectedTab = Tab.Camera.name }
                    )

                    Tab.Library -> LibraryScreen(padding)
                    Tab.Camera -> CameraScreen(
                        padding = padding,
                        selectedLensFacing = selectedLensFacing,
                        onSelectLens = {
                            selectedLensFacing = it
                            statusText = "Выбрана камера: ${cameraName(it)}"
                        }
                    )

                    Tab.Settings -> SettingsScreen(
                        padding = padding,
                        audioEnabled = audioEnabled,
                        onAudioChanged = {
                            audioEnabled = it
                            statusText = if (it) {
                                "Запись звука включена"
                            } else {
                                "Запись звука выключена"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    isRecording: Boolean,
    statusText: String,
    selectedCamera: String,
    audioEnabled: Boolean,
    onRecordingClick: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Header()

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
                        text = if (isRecording) "Остановить\nзапись" else "Начать\nзапись",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                color = if (isRecording) LockLensRecording else LockLensSuccess,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusCard("Камера", selectedCamera, LockLensPrimary, onClick = onOpenCamera)
            StatusCard("Качество", "1080p FHD, fallback если недоступно", LockLensSecondary)
            StatusCard("Звук", if (audioEnabled) "Микрофон включён" else "Без звука", LockLensSuccess)
            StatusCard("Папка", "Видео / LockLens", LockLensSecondary)
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_locklens_symbol),
            contentDescription = "Логотип LockLens",
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LockLens",
                color = LockLensTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Запись с выключенным экраном",
                color = LockLensTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            Column(modifier = Modifier.weight(1f)) {
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
        title = "Видео",
        subtitle = "Записи сохраняются в папку Видео / LockLens",
        body = "После остановки записи откройте системную Галерею или приложение Файлы. Внутреннюю библиотеку добавим следующим шагом."
    )
}

@Composable
private fun CameraScreen(
    padding: PaddingValues,
    selectedLensFacing: Int,
    onSelectLens: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Камера",
            color = LockLensTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Выберите камеру для записи. Если фронтальная камера недоступна, Android покажет ошибку запуска.",
            color = LockLensTextSecondary,
            fontSize = 14.sp
        )

        SelectableCard(
            title = "Задняя камера",
            subtitle = "Основная камера телефона",
            selected = selectedLensFacing == CameraSelector.LENS_FACING_BACK,
            onClick = { onSelectLens(CameraSelector.LENS_FACING_BACK) }
        )

        SelectableCard(
            title = "Фронтальная камера",
            subtitle = "Камера для записи себя",
            selected = selectedLensFacing == CameraSelector.LENS_FACING_FRONT,
            onClick = { onSelectLens(CameraSelector.LENS_FACING_FRONT) }
        )
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    audioEnabled: Boolean,
    onAudioChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Настройки",
            color = LockLensTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Запись звука",
                        color = LockLensTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (audioEnabled) "Микрофон включён" else "Видео без звука",
                        color = LockLensTextSecondary,
                        fontSize = 14.sp
                    )
                }

                Switch(
                    checked = audioEnabled,
                    onCheckedChange = onAudioChanged
                )
            }
        }

        StatusCard("Хранение", "Тестовая версия: Видео / LockLens", LockLensSecondary)
        StatusCard("Приватность", "Запись только после нажатия пользователем", LockLensSuccess)
        StatusCard("Samsung", "Если запись обрывается, отключите ограничения батареи", LockLensSecondary)
        StatusCard("Тема", "AMOLED Graphite", LockLensPrimary)
    }
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LockLensSurfaceElevated else LockLensSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) LockLensPrimary else LockLensSurfaceElevated
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = LockLensTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = LockLensTextSecondary,
                    fontSize = 14.sp
                )
            }

            Text(
                text = if (selected) "Выбрано" else "Выбрать",
                color = if (selected) LockLensPrimary else LockLensTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
            fontSize = 19.sp,
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
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

private fun requiredPermissions(audioEnabled: Boolean): Array<String> {
    val permissions = mutableListOf(Manifest.permission.CAMERA)

    if (audioEnabled) {
        permissions += Manifest.permission.RECORD_AUDIO
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }

    return permissions.toTypedArray()
}

private fun hasRequiredPermissions(
    context: Context,
    audioEnabled: Boolean
): Boolean {
    return requiredPermissions(audioEnabled).all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun startRecordingService(
    context: Context,
    lensFacing: Int,
    audioEnabled: Boolean
) {
    val intent = Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_START_RECORDING
        putExtra(RecordingForegroundService.EXTRA_LENS_FACING, lensFacing)
        putExtra(RecordingForegroundService.EXTRA_AUDIO_ENABLED, audioEnabled)
    }

    ContextCompat.startForegroundService(context, intent)
}

private fun stopRecordingService(context: Context) {
    val intent = Intent(context, RecordingForegroundService::class.java).apply {
        action = RecordingForegroundService.ACTION_STOP_RECORDING
    }

    context.startService(intent)
}

private fun cameraName(lensFacing: Int): String {
    return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        "Фронтальная камера"
    } else {
        "Задняя камера"
    }
}
