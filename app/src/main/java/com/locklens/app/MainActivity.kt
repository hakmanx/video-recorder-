package com.locklens.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    Home("Главная", "Г"),
    Library("Видео", "В"),
    Camera("Камера", "К"),
    Settings("Настройки", "Н")
}

private data class QualityOption(
    val code: String,
    val title: String,
    val description: String
)

private data class RecordingItem(
    val title: String,
    val uri: Uri,
    val filePath: String?,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
    val location: String,
    val visibleInGallery: Boolean
)

private val qualityOptions = listOf(
    QualityOption(RecordingForegroundService.QUALITY_SD, "480p SD", "минимальный размер файла"),
    QualityOption(RecordingForegroundService.QUALITY_HD, "720p HD", "экономия батареи и памяти"),
    QualityOption(RecordingForegroundService.QUALITY_FHD, "1080p FHD", "рекомендуемый баланс"),
    QualityOption(RecordingForegroundService.QUALITY_UHD, "4K UHD", "максимальное качество, если доступно")
)

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
    val prefs = remember {
        context.getSharedPreferences("locklens_settings", Context.MODE_PRIVATE)
    }

    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home.name) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var audioEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("audio_enabled", true)) }
    var storageMode by rememberSaveable {
        mutableStateOf(prefs.getString("storage_mode", RecordingForegroundService.STORAGE_PRIVATE) ?: RecordingForegroundService.STORAGE_PRIVATE)
    }
    var customFolderUri by rememberSaveable { mutableStateOf(prefs.getString("custom_folder_uri", "") ?: "") }
    var selectedQuality by rememberSaveable {
        mutableStateOf(prefs.getString("selected_quality", RecordingForegroundService.QUALITY_FHD) ?: RecordingForegroundService.QUALITY_FHD)
    }
    var selectedLensFacing by rememberSaveable {
        mutableIntStateOf(prefs.getInt("selected_lens_facing", CameraSelector.LENS_FACING_BACK))
    }
    var statusText by rememberSaveable { mutableStateOf("Готово к записи") }
    var legalAccepted by remember { mutableStateOf(prefs.getBoolean("legal_accepted", false)) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val recordings = remember(refreshKey, customFolderUri) {
        loadRecordings(context, customFolderUri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasRequiredPermissions(context, audioEnabled, storageMode)) {
            val error = startRecordingService(
                context = context,
                lensFacing = selectedLensFacing,
                audioEnabled = audioEnabled,
                storageMode = storageMode,
                customFolderUri = customFolderUri,
                quality = selectedQuality
            )

            if (error == null) {
                isRecording = true
                statusText = "Запуск записи..."
            } else {
                isRecording = false
                statusText = error
            }
        } else {
            statusText = "Разрешения не выданы"
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            customFolderUri = uri.toString()
            storageMode = RecordingForegroundService.STORAGE_CUSTOM

            prefs.edit()
                .putString("custom_folder_uri", customFolderUri)
                .putString("storage_mode", storageMode)
                .apply()

            refreshKey++
            statusText = "Папка выбрана"
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = intent?.getStringExtra(RecordingForegroundService.EXTRA_MESSAGE) ?: ""

                when (intent?.getStringExtra(RecordingForegroundService.EXTRA_STATE)) {
                    RecordingForegroundService.STATE_PREPARING -> {
                        isRecording = true
                        statusText = message.ifBlank { "Подготовка камеры..." }
                    }

                    RecordingForegroundService.STATE_RECORDING -> {
                        isRecording = true
                        statusText = message.ifBlank { "Идёт запись" }
                    }

                    RecordingForegroundService.STATE_SAVING -> {
                        isRecording = true
                        statusText = message.ifBlank { "Сохраняем..." }
                    }

                    RecordingForegroundService.STATE_COMPLETED -> {
                        isRecording = false
                        statusText = message.ifBlank { "Запись сохранена" }
                        refreshKey++
                    }

                    RecordingForegroundService.STATE_FAILED -> {
                        isRecording = false
                        statusText = message.ifBlank { "Ошибка записи" }
                        refreshKey++
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

    MaterialTheme(
        colorScheme = darkColorScheme(
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
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = LockLensBackground
        ) {
            androidx.compose.material3.Scaffold(
                containerColor = LockLensBackground,
                bottomBar = {
                    BottomNav(
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
                        lensFacing = selectedLensFacing,
                        cameraName = cameraName(selectedLensFacing),
                        quality = qualityTitle(selectedQuality),
                        audioEnabled = audioEnabled,
                        storageMode = storageMode,
                        onRecordClick = {
                            if (isRecording) {
                                stopRecordingService(context)
                                statusText = "Останавливаем и сохраняем..."
                            } else {
                                if (hasRequiredPermissions(context, audioEnabled, storageMode)) {
                                    val error = startRecordingService(
                                        context = context,
                                        lensFacing = selectedLensFacing,
                                        audioEnabled = audioEnabled,
                                        storageMode = storageMode,
                                        customFolderUri = customFolderUri,
                                        quality = selectedQuality
                                    )

                                    if (error == null) {
                                        isRecording = true
                                        statusText = "Запуск записи..."
                                    } else {
                                        statusText = error
                                    }
                                } else {
                                    permissionLauncher.launch(requiredPermissions(audioEnabled, storageMode))
                                }
                            }
                        },
                        onOpenCamera = { selectedTab = Tab.Camera.name },
                        onOpenSettings = { selectedTab = Tab.Settings.name }
                    )

                    Tab.Library -> LibraryScreen(
                        padding = padding,
                        recordings = recordings,
                        onRefresh = {
                            refreshKey++
                            statusText = "Список обновлён"
                        },
                        onOpen = { item -> statusText = openRecording(context, item) },
                        onShare = { item -> statusText = shareRecording(context, item) },
                        onDelete = { item ->
                            statusText = if (deleteRecording(context, item)) {
                                refreshKey++
                                "Запись удалена"
                            } else {
                                "Не удалось удалить запись"
                            }
                        },
                        onExport = { item ->
                            statusText = if (exportToGallery(context, item)) {
                                refreshKey++
                                "Сохранено в Галерею / LockLens"
                            } else {
                                "Не удалось экспортировать"
                            }
                        }
                    )

                    Tab.Camera -> CameraScreen(
                        padding = padding,
                        selectedLensFacing = selectedLensFacing,
                        selectedQuality = selectedQuality,
                        onSelectLens = {
                            selectedLensFacing = it
                            prefs.edit().putInt("selected_lens_facing", it).apply()
                            statusText = "Выбрана камера: ${cameraName(it)}"
                        },
                        onSelectQuality = {
                            selectedQuality = it
                            prefs.edit().putString("selected_quality", it).apply()
                            statusText = "Выбрано качество: ${qualityTitle(it)}"
                        }
                    )

                    Tab.Settings -> SettingsScreen(
                        padding = padding,
                        audioEnabled = audioEnabled,
                        storageMode = storageMode,
                        customFolderSelected = customFolderUri.isNotBlank(),
                        permissionStatus = permissionStatusText(context, audioEnabled, storageMode),
                        onAudioChanged = {
                            audioEnabled = it
                            prefs.edit().putBoolean("audio_enabled", it).apply()
                            statusText = if (it) "Запись звука включена" else "Запись звука выключена"
                        },
                        onStorageModeChanged = {
                            storageMode = it
                            prefs.edit().putString("storage_mode", it).apply()
                            statusText = storageModeTitle(it)
                        },
                        onChooseFolder = {
                            folderLauncher.launch(null)
                        },
                        onRequestPermissions = {
                            permissionLauncher.launch(requiredPermissions(audioEnabled, storageMode))
                        },
                        onOpenAppSettings = { openAppSettings(context) },
                        onOpenBatterySettings = { openBatterySettings(context) }
                    )
                }

                if (!legalAccepted) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Используйте ответственно") },
                        text = {
                            Text(
                                "LockLens записывает видео только после вашего нажатия. Во время записи Android показывает системные индикаторы и уведомление."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    legalAccepted = true
                                    prefs.edit().putBoolean("legal_accepted", true).apply()
                                }
                            ) {
                                Text("Я понимаю")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { PreviewView(it) },
        update = { previewView ->
            val future = ProcessCameraProvider.getInstance(context)

            future.addListener(
                {
                    try {
                        val provider = future.get()
                        val selector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (_: Exception) {
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    )
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    isRecording: Boolean,
    statusText: String,
    lensFacing: Int,
    cameraName: String,
    quality: String,
    audioEnabled: Boolean,
    storageMode: String,
    onRecordClick: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LockLensSurface),
            border = BorderStroke(1.dp, LockLensSurfaceElevated)
        ) {
            CameraPreview(lensFacing, Modifier.fillMaxSize())
        }

        Button(
            onClick = onRecordClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = if (isRecording) "Остановить запись" else "Начать запись",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = statusText,
            color = if (isRecording) LockLensRecording else LockLensSuccess,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        StatusCard("Камера", cameraName, LockLensPrimary, onOpenCamera)
        StatusCard("Качество", quality, LockLensSecondary, onOpenCamera)
        StatusCard("Звук", if (audioEnabled) "Микрофон включён" else "Без звука", LockLensSuccess, onOpenSettings)
        StatusCard("Папка", storageModeTitle(storageMode), LockLensSecondary, onOpenSettings)
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.ic_locklens_symbol),
            contentDescription = "Логотип LockLens",
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text("LockLens", color = LockLensTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Запись с выключенным экраном", color = LockLensTextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LibraryScreen(
    padding: PaddingValues,
    recordings: List<RecordingItem>,
    onRefresh: () -> Unit,
    onOpen: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onExport: (RecordingItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Видео", color = LockLensTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Button(onClick = onRefresh) {
            Text("Обновить список")
        }

        if (recordings.isEmpty()) {
            StatusCard(
                title = "Записей пока нет",
                subtitle = "Нажмите «Начать запись» на главной. После остановки файл появится здесь.",
                accent = LockLensSecondary
            )
        } else {
            recordings.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = LockLensSurface),
                    border = BorderStroke(1.dp, LockLensSurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = LockLensTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${item.location} · ${formatSize(item.sizeBytes)} · ${formatDate(item.createdAtEpochMs)}",
                            color = LockLensTextSecondary,
                            fontSize = 13.sp
                        )

                        Row {
                            TextButton(onClick = { onOpen(item) }) { Text("Открыть") }
                            TextButton(onClick = { onShare(item) }) { Text("Поделиться") }
                        }

                        Row {
                            TextButton(onClick = { onExport(item) }) { Text("В галерею") }
                            TextButton(onClick = { onDelete(item) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScreen(
    padding: PaddingValues,
    selectedLensFacing: Int,
    selectedQuality: String,
    onSelectLens: (Int) -> Unit,
    onSelectQuality: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Камера", color = LockLensTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = LockLensSurface),
            border = BorderStroke(1.dp, LockLensSurfaceElevated)
        ) {
            CameraPreview(selectedLensFacing, Modifier.fillMaxSize())
        }

        SelectCard("Задняя камера", "Основная камера телефона", selectedLensFacing == CameraSelector.LENS_FACING_BACK) {
            onSelectLens(CameraSelector.LENS_FACING_BACK)
        }

        SelectCard("Фронтальная камера", "Камера для записи себя", selectedLensFacing == CameraSelector.LENS_FACING_FRONT) {
            onSelectLens(CameraSelector.LENS_FACING_FRONT)
        }

        Text("Качество", color = LockLensTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)

        qualityOptions.forEach { option ->
            SelectCard(option.title, option.description, selectedQuality == option.code) {
                onSelectQuality(option.code)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    audioEnabled: Boolean,
    storageMode: String,
    customFolderSelected: Boolean,
    permissionStatus: String,
    onAudioChanged: (Boolean) -> Unit,
    onStorageModeChanged: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Настройки", color = LockLensTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        SwitchCard(
            title = "Запись звука",
            subtitle = if (audioEnabled) "Микрофон включён" else "Видео без звука",
            checked = audioEnabled,
            onCheckedChange = onAudioChanged
        )

        Text("Куда сохранять", color = LockLensTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        SelectCard(
            "Внутренняя библиотека",
            "Не отображается в галерее",
            storageMode == RecordingForegroundService.STORAGE_PRIVATE
        ) {
            onStorageModeChanged(RecordingForegroundService.STORAGE_PRIVATE)
        }

        SelectCard(
            "Галерея / LockLens",
            "Видео будет видно в системной галерее",
            storageMode == RecordingForegroundService.STORAGE_GALLERY
        ) {
            onStorageModeChanged(RecordingForegroundService.STORAGE_GALLERY)
        }

        SelectCard(
            "Своя папка",
            if (customFolderSelected) "Папка выбрана" else "Папка не выбрана",
            storageMode == RecordingForegroundService.STORAGE_CUSTOM
        ) {
            onStorageModeChanged(RecordingForegroundService.STORAGE_CUSTOM)
            onChooseFolder()
        }

        OutlinedButton(onClick = onChooseFolder) {
            Text("Выбрать папку")
        }

        StatusCard("Разрешения", permissionStatus, LockLensPrimary)

        Button(onClick = onRequestPermissions) {
            Text("Запросить разрешения")
        }

        Button(onClick = onOpenAppSettings) {
            Text("Открыть настройки приложения")
        }

        Button(onClick = onOpenBatterySettings) {
            Text("Открыть настройки батареи")
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = LockLensSurface),
        border = BorderStroke(1.dp, LockLensSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = LockLensTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = LockLensTextSecondary, fontSize = 14.sp)
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
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = LockLensSurface),
        border = BorderStroke(1.dp, LockLensSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = LockLensTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = LockLensTextSecondary, fontSize = 14.sp)
            }

            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SelectCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LockLensSurfaceElevated else LockLensSurface
        ),
        border = BorderStroke(
            1.dp,
            if (selected) LockLensPrimary else LockLensSurfaceElevated
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = LockLensTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = LockLensTextSecondary, fontSize = 14.sp)
            }

            Text(
                text = if (selected) "Выбрано" else "Выбрать",
                color = if (selected) LockLensPrimary else LockLensTextSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BottomNav(
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
                        fontWeight = FontWeight.Bold
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        color = if (selected) LockLensPrimary else LockLensTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
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

private fun requiredPermissions(audioEnabled: Boolean, storageMode: String): Array<String> {
    val permissions = mutableListOf(Manifest.permission.CAMERA)

    if (audioEnabled) {
        permissions += Manifest.permission.RECORD_AUDIO
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }

    if (storageMode == RecordingForegroundService.STORAGE_GALLERY && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    return permissions.toTypedArray()
}

private fun hasRequiredPermissions(context: Context, audioEnabled: Boolean, storageMode: String): Boolean {
    return requiredPermissions(audioEnabled, storageMode).all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun permissionStatusText(context: Context, audioEnabled: Boolean, storageMode: String): String {
    val missing = requiredPermissions(audioEnabled, storageMode).filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    return if (missing.isEmpty()) {
        "Все нужные разрешения выданы"
    } else {
        "Не выданы: " + missing.joinToString(", ") { permissionRuName(it) }
    }
}

private fun permissionRuName(permission: String): String {
    return when (permission) {
        Manifest.permission.CAMERA -> "камера"
        Manifest.permission.RECORD_AUDIO -> "микрофон"
        Manifest.permission.POST_NOTIFICATIONS -> "уведомления"
        Manifest.permission.WRITE_EXTERNAL_STORAGE -> "память"
        else -> permission
    }
}

private fun startRecordingService(
    context: Context,
    lensFacing: Int,
    audioEnabled: Boolean,
    storageMode: String,
    customFolderUri: String,
    quality: String
): String? {
    return try {
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START_RECORDING
            putExtra(RecordingForegroundService.EXTRA_LENS_FACING, lensFacing)
            putExtra(RecordingForegroundService.EXTRA_AUDIO_ENABLED, audioEnabled)
            putExtra(RecordingForegroundService.EXTRA_STORAGE_MODE, storageMode)
            putExtra(RecordingForegroundService.EXTRA_CUSTOM_FOLDER_URI, customFolderUri)
            putExtra(RecordingForegroundService.EXTRA_QUALITY, quality)
        }

        ContextCompat.startForegroundService(context, intent)
        null
    } catch (error: Exception) {
        error.message ?: "Android заблокировал запуск записи"
    }
}

private fun stopRecordingService(context: Context) {
    try {
        context.startService(
            Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_STOP_RECORDING
            }
        )
    } catch (_: Exception) {
    }
}

private fun cameraName(lensFacing: Int): String {
    return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        "Фронтальная камера"
    } else {
        "Задняя камера"
    }
}

private fun qualityTitle(code: String): String {
    return qualityOptions.firstOrNull { it.code == code }?.title ?: "1080p FHD"
}

private fun storageModeTitle(mode: String): String {
    return when (mode) {
        RecordingForegroundService.STORAGE_GALLERY -> "Галерея / LockLens"
        RecordingForegroundService.STORAGE_CUSTOM -> "Выбранная папка"
        else -> "Внутренняя библиотека"
    }
}

private fun loadRecordings(context: Context, customFolderUri: String): List<RecordingItem> {
    val result = mutableListOf<RecordingItem>()

    try {
        val dir = appVideoDir(context)

        dir.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        }?.forEach { file ->
            result += RecordingItem(
                title = file.name,
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                createdAtEpochMs = file.lastModified(),
                location = "Внутри LockLens",
                visibleInGallery = false
            )
        }
    } catch (_: Exception) {
    }

    try {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("LockLens_%"),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                result += RecordingItem(
                    title = cursor.getString(nameIndex) ?: "LockLens_video.mp4",
                    uri = uri,
                    filePath = null,
                    sizeBytes = cursor.getLong(sizeIndex),
                    createdAtEpochMs = cursor.getLong(dateIndex) * 1000L,
                    location = "Галерея",
                    visibleInGallery = true
                )
            }
        }
    } catch (_: Exception) {
    }

    if (customFolderUri.isNotBlank()) {
        try {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(customFolderUri))

            folder?.listFiles()?.forEach { document ->
                val name = document.name ?: ""

                if (document.isFile && name.endsWith(".mp4", ignoreCase = true)) {
                    result += RecordingItem(
                        title = name,
                        uri = document.uri,
                        filePath = null,
                        sizeBytes = document.length(),
                        createdAtEpochMs = document.lastModified(),
                        location = "Выбранная папка",
                        visibleInGallery = false
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    return result.distinctBy { it.uri.toString() }.sortedByDescending { it.createdAtEpochMs }
}

private fun appVideoDir(context: Context): File {
    val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    val dir = File(root, "LockLens")

    if (!dir.exists()) {
        dir.mkdirs()
    }

    try {
        File(dir, ".nomedia").createNewFile()
    } catch (_: Exception) {
    }

    return dir
}

private fun openRecording(context: Context, item: RecordingItem): String {
    return try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Открыть видео"
            )
        )

        "Открываем видео"
    } catch (_: ActivityNotFoundException) {
        "На телефоне нет приложения для открытия видео"
    } catch (error: Exception) {
        error.message ?: "Не удалось открыть видео"
    }
}

private fun shareRecording(context: Context, item: RecordingItem): String {
    return try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Поделиться записью"
            )
        )

        "Открываем меню отправки"
    } catch (error: Exception) {
        error.message ?: "Не удалось поделиться записью"
    }
}

private fun deleteRecording(context: Context, item: RecordingItem): Boolean {
    return try {
        if (item.filePath != null) {
            File(item.filePath).delete()
        } else {
            context.contentResolver.delete(item.uri, null, null) > 0
        }
    } catch (_: Exception) {
        false
    }
}

private fun exportToGallery(context: Context, item: RecordingItem): Boolean {
    if (item.visibleInGallery) return true

    return try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, item.title)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LockLens")
            }
        }

        val destination = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        context.contentResolver.openInputStream(item.uri).use { input ->
            context.contentResolver.openOutputStream(destination).use { output ->
                if (input == null || output == null) return false
                input.copyTo(output)
            }
        }

        true
    } catch (_: Exception) {
        false
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 MB"
    val mb = sizeBytes / 1024.0 / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0L) return "без даты"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    } catch (_: Exception) {
    }
}

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (_: Exception) {
        openAppSettings(context)
    }
}
