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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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

private val qualityOptions = listOf(
    QualityOption(
        RecordingForegroundService.QUALITY_SD,
        "480p SD",
        "Минимальный размер файла"
    ),
    QualityOption(
        RecordingForegroundService.QUALITY_HD,
        "720p HD",
        "Экономия батареи и памяти"
    ),
    QualityOption(
        RecordingForegroundService.QUALITY_FHD,
        "1080p FHD",
        "Рекомендуемый баланс"
    ),
    QualityOption(
        RecordingForegroundService.QUALITY_UHD,
        "4K UHD",
        "Максимум качества, высокий расход памяти"
    )
)

private data class RecordingItem(
    val title: String,
    val uri: Uri,
    val filePath: String?,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
    val visibleInGallery: Boolean
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

    var selectedTab by rememberSaveable {
        mutableStateOf(Tab.Home.name)
    }
    var isRecording by rememberSaveable {
        mutableStateOf(false)
    }
    var audioEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("audio_enabled", true))
    }
    var saveVisibleInGallery by rememberSaveable {
        mutableStateOf(prefs.getBoolean("save_visible_in_gallery", false))
    }
    var selectedQuality by rememberSaveable {
        mutableStateOf(
            prefs.getString(
                "selected_quality",
                RecordingForegroundService.QUALITY_FHD
            ) ?: RecordingForegroundService.QUALITY_FHD
        )
    }
    var selectedLensFacing by rememberSaveable {
        mutableIntStateOf(
            prefs.getInt(
                "selected_lens_facing",
                CameraSelector.LENS_FACING_BACK
            )
        )
    }
    var statusText by rememberSaveable {
        mutableStateOf("Готово к записи")
    }
    var legalAccepted by remember {
        mutableStateOf(prefs.getBoolean("legal_accepted", false))
    }
    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    val recordings = remember(refreshKey) {
        loadRecordings(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasRequiredPermissions(context, audioEnabled, saveVisibleInGallery)) {
            val startError = startRecordingService(
                context = context,
                lensFacing = selectedLensFacing,
                audioEnabled = audioEnabled,
                saveVisibleInGallery = saveVisibleInGallery,
                quality = selectedQuality
            )

            if (startError == null) {
                isRecording = true
                statusText = "Запуск записи..."
            } else {
                isRecording = false
                statusText = startError
            }
        } else {
            statusText = "Разрешения не выданы. Откройте Настройки → Разрешения."
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

                    RecordingForegroundService.STATE_SAVING -> {
                        isRecording = true
                        statusText = "Останавливаем и сохраняем запись..."
                    }

                    RecordingForegroundService.STATE_COMPLETED -> {
                        isRecording = false
                        statusText = intent.getStringExtra(
                            RecordingForegroundService.EXTRA_MESSAGE
                        ) ?: "Запись сохранена"
                        refreshKey++
                    }

                    RecordingForegroundService.STATE_FAILED -> {
                        isRecording = false
                        statusText = intent.getStringExtra(
                            RecordingForegroundService.EXTRA_MESSAGE
                        ) ?: "Ошибка записи"
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
                        selectedQuality = qualityTitle(selectedQuality),
                        audioEnabled = audioEnabled,
                        saveVisibleInGallery = saveVisibleInGallery,
                        onRecordingClick = {
                            if (isRecording) {
                                stopRecordingService(context)
                                statusText = "Останавливаем и сохраняем запись..."
                            } else {
                                if (hasRequiredPermissions(
                                        context,
                                        audioEnabled,
                                        saveVisibleInGallery
                                    )
                                ) {
                                    val startError = startRecordingService(
                                        context = context,
                                        lensFacing = selectedLensFacing,
                                        audioEnabled = audioEnabled,
                                        saveVisibleInGallery = saveVisibleInGallery,
                                        quality = selectedQuality
                                    )

                                    if (startError == null) {
                                        isRecording = true
                                        statusText = "Запуск записи..."
                                    } else {
                                        isRecording = false
                                        statusText = startError
                                    }
                                } else {
                                    permissionLauncher.launch(
                                        requiredPermissions(
                                            audioEnabled,
                                            saveVisibleInGallery
                                        )
                                    )
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
                            statusText = "Список видео обновлён"
                        },
                        onPlay = { item ->
                            statusText = openRecording(context, item)
                        },
                        onShare = { item ->
                            statusText = shareRecording(context, item)
                        },
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
                                "Копия сохранена в Галерею / LockLens"
                            } else {
                                "Не удалось экспортировать в галерею"
                            }
                        },
                        onHide = { item ->
                            statusText = if (hideFromGallery(context, item)) {
                                refreshKey++
                                "Запись скрыта из галереи и перенесена в библиотеку"
                            } else {
                                "Не удалось скрыть запись из галереи"
                            }
                        },
                        onRename = { item, newName ->
                            val renamed = renameRecording(context, item, newName)

                            if (renamed) {
                                refreshKey++
                            }

                            renamed
                        }
                    )

                    Tab.Camera -> CameraScreen(
                        padding = padding,
                        selectedLensFacing = selectedLensFacing,
                        selectedQuality = selectedQuality,
                        onSelectLens = {
                            selectedLensFacing = it
                            prefs.edit()
                                .putInt("selected_lens_facing", it)
                                .apply()
                            statusText = "Выбрана камера: ${cameraName(it)}"
                        },
                        onSelectQuality = {
                            selectedQuality = it
                            prefs.edit()
                                .putString("selected_quality", it)
                                .apply()
                            statusText = "Выбрано качество: ${qualityTitle(it)}"
                        }
                    )

                    Tab.Settings -> SettingsScreen(
                        padding = padding,
                        audioEnabled = audioEnabled,
                        saveVisibleInGallery = saveVisibleInGallery,
                        permissionStatus = permissionStatusText(
                            context,
                            audioEnabled,
                            saveVisibleInGallery
                        ),
                        onAudioChanged = {
                            audioEnabled = it
                            prefs.edit()
                                .putBoolean("audio_enabled", it)
                                .apply()
                            statusText = if (it) {
                                "Запись звука включена"
                            } else {
                                "Запись звука выключена"
                            }
                        },
                        onGalleryChanged = {
                            saveVisibleInGallery = it
                            prefs.edit()
                                .putBoolean("save_visible_in_gallery", it)
                                .apply()
                            statusText = if (it) {
                                "Новые записи будут видны в галерее"
                            } else {
                                "Новые записи будут храниться во внутренней библиотеке"
                            }
                        },
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                requiredPermissions(
                                    audioEnabled,
                                    saveVisibleInGallery
                                )
                            )
                        },
                        onOpenAppSettings = {
                            openAppSettings(context)
                        },
                        onOpenBatterySettings = {
                            openBatterySettings(context)
                        }
                    )
                }

                if (!legalAccepted) {
                    LegalDialog(
                        onAccept = {
                            legalAccepted = true
                            prefs.edit()
                                .putBoolean("legal_accepted", true)
                                .apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text("Используйте ответственно")
        },
        text = {
            Text(
                text = "LockLens записывает видео только после вашего нажатия. Используйте приложение только там, где запись разрешена законом и правилами приватности. Во время записи Android показывает системные индикаторы и уведомление."
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Я понимаю")
            }
        }
    )
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    isRecording: Boolean,
    statusText: String,
    selectedCamera: String,
    selectedQuality: String,
    audioEnabled: Boolean,
    saveVisibleInGallery: Boolean,
    onRecordingClick: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit
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
                                .background(
                                    if (isRecording) {
                                        LockLensBackground
                                    } else {
                                        LockLensRecording
                                    }
                                )
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
            StatusCard(
                title = "Камера",
                subtitle = selectedCamera,
                accent = LockLensPrimary,
                onClick = onOpenCamera
            )
            StatusCard(
                title = "Качество",
                subtitle = selectedQuality,
                accent = LockLensSecondary,
                onClick = onOpenCamera
            )
            StatusCard(
                title = "Звук",
                subtitle = if (audioEnabled) "Микрофон включён" else "Без звука",
                accent = LockLensSuccess,
                onClick = onOpenSettings
            )
            StatusCard(
                title = "Хранение",
                subtitle = if (saveVisibleInGallery) {
                    "Галерея / LockLens"
                } else {
                    "Внутренняя библиотека LockLens"
                },
                accent = LockLensSecondary,
                onClick = onOpenSettings
            )
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
private fun LibraryScreen(
    padding: PaddingValues,
    recordings: List<RecordingItem>,
    onRefresh: () -> Unit,
    onPlay: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onExport: (RecordingItem) -> Unit,
    onHide: (RecordingItem) -> Unit,
    onRename: (RecordingItem, String) -> Boolean
) {
    var renameTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Видео",
            color = LockLensTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Здесь отображаются записи LockLens из внутренней библиотеки и Галереи / LockLens.",
            color = LockLensTextSecondary,
            fontSize = 14.sp
        )

        Button(onClick = onRefresh) {
            Text("Обновить список")
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                color = LockLensSuccess,
                fontSize = 14.sp
            )
        }

        if (recordings.isEmpty()) {
            EmptyLibraryCard()
        } else {
            recordings.forEach { item ->
                RecordingCard(
                    item = item,
                    onPlay = onPlay,
                    onShare = onShare,
                    onDelete = onDelete,
                    onExport = onExport,
                    onHide = onHide,
                    onRenameClick = {
                        renameTarget = item
                        renameText = item.title.removeSuffix(".mp4")
                    }
                )
            }
        }
    }

    val target = renameTarget

    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                renameTarget = null
            },
            title = {
                Text("Переименовать запись")
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = {
                        Text("Новое имя")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ok = onRename(target, renameText)
                        message = if (ok) {
                            "Запись переименована"
                        } else {
                            "Не удалось переименовать запись"
                        }
                        renameTarget = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        renameTarget = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun EmptyLibraryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LockLensSurface),
        border = BorderStroke(1.dp, LockLensSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Записей пока нет",
                color = LockLensTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Нажмите «Начать запись» на главной. После остановки файл появится здесь.",
                color = LockLensTextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    onPlay: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onExport: (RecordingItem) -> Unit,
    onHide: (RecordingItem) -> Unit,
    onRenameClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LockLensSurface),
        border = BorderStroke(1.dp, LockLensSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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
                text = buildString {
                    append(if (item.visibleInGallery) "В галерее" else "Внутри LockLens")
                    append(" · ")
                    append(formatSize(item.sizeBytes))
                    append(" · ")
                    append(formatDate(item.createdAtEpochMs))
                },
                color = LockLensTextSecondary,
                fontSize = 13.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { onPlay(item) }) {
                    Text("Открыть")
                }

                TextButton(onClick = { onShare(item) }) {
                    Text("Поделиться")
                }

                TextButton(onClick = onRenameClick) {
                    Text("Имя")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.visibleInGallery) {
                    TextButton(onClick = { onHide(item) }) {
                        Text("Скрыть")
                    }
                } else {
                    TextButton(onClick = { onExport(item) }) {
                        Text("В галерею")
                    }
                }

                TextButton(onClick = { onDelete(item) }) {
                    Text("Удалить")
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
    val context = LocalContext.current
    val hasBackCamera =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    val hasFrontCamera =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

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
            text = "Выберите камеру и качество. Если выбранное качество недоступно на модуле камеры, CameraX автоматически возьмёт ближайшее доступное.",
            color = LockLensTextSecondary,
            fontSize = 14.sp
        )

        SelectableCard(
            title = "Задняя камера",
            subtitle = if (hasBackCamera) "Основная камера телефона" else "Не найдена",
            selected = selectedLensFacing == CameraSelector.LENS_FACING_BACK,
            enabled = hasBackCamera,
            onClick = { onSelectLens(CameraSelector.LENS_FACING_BACK) }
        )

        SelectableCard(
            title = "Фронтальная камера",
            subtitle = if (hasFrontCamera) "Камера для записи себя" else "Не найдена",
            selected = selectedLensFacing == CameraSelector.LENS_FACING_FRONT,
            enabled = hasFrontCamera,
            onClick = { onSelectLens(CameraSelector.LENS_FACING_FRONT) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Качество",
            color = LockLensTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        qualityOptions.forEach { option ->
            SelectableCard(
                title = option.title,
                subtitle = option.description,
                selected = selectedQuality == option.code,
                enabled = true,
                onClick = { onSelectQuality(option.code) }
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    audioEnabled: Boolean,
    saveVisibleInGallery: Boolean,
    permissionStatus: String,
    onAudioChanged: (Boolean) -> Unit,
    onGalleryChanged: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
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

        SwitchCard(
            title = "Запись звука",
            subtitle = if (audioEnabled) "Микрофон включён" else "Видео без звука",
            checked = audioEnabled,
            onCheckedChange = onAudioChanged
        )

        SwitchCard(
            title = "Показывать в галерее",
            subtitle = if (saveVisibleInGallery) {
                "Новые записи попадут в Галерею / LockLens"
            } else {
                "Новые записи будут только во внутренней библиотеке"
            },
            checked = saveVisibleInGallery,
            onCheckedChange = onGalleryChanged
        )

        StatusCard(
            title = "Разрешения",
            subtitle = permissionStatus,
            accent = LockLensPrimary
        )

        Button(onClick = onRequestPermissions) {
            Text("Запросить разрешения")
        }

        Button(onClick = onOpenAppSettings) {
            Text("Открыть настройки приложения")
        }

        Button(onClick = onOpenBatterySettings) {
            Text("Открыть настройки батареи")
        }

        StatusCard(
            title = "Samsung",
            subtitle = "Для длинной записи отключите ограничения батареи для LockLens",
            accent = LockLensSecondary
        )

        StatusCard(
            title = "Приватность",
            subtitle = "Запись запускается только после нажатия. Уведомление не скрывается.",
            accent = LockLensSuccess
        )

        StatusCard(
            title = "Тема",
            subtitle = "AMOLED Graphite",
            accent = LockLensPrimary
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
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
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
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                LockLensSurfaceElevated
            } else {
                LockLensSurface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                LockLensPrimary
            } else {
                LockLensSurfaceElevated
            }
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
                    color = if (enabled) LockLensTextPrimary else LockLensTextSecondary,
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
                text = when {
                    !enabled -> "Нет"
                    selected -> "Выбрано"
                    else -> "Выбрать"
                },
                color = if (selected) LockLensPrimary else LockLensTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
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

private fun requiredPermissions(
    audioEnabled: Boolean,
    saveVisibleInGallery: Boolean
): Array<String> {
    val permissions = mutableListOf(Manifest.permission.CAMERA)

    if (audioEnabled) {
        permissions += Manifest.permission.RECORD_AUDIO
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }

    if (
        saveVisibleInGallery &&
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    ) {
        permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    return permissions.toTypedArray()
}

private fun hasRequiredPermissions(
    context: Context,
    audioEnabled: Boolean,
    saveVisibleInGallery: Boolean
): Boolean {
    return requiredPermissions(audioEnabled, saveVisibleInGallery).all { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun permissionStatusText(
    context: Context,
    audioEnabled: Boolean,
    saveVisibleInGallery: Boolean
): String {
    val missing = requiredPermissions(
        audioEnabled,
        saveVisibleInGallery
    ).filter { permission ->
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }

    if (missing.isEmpty()) {
        return "Все нужные разрешения выданы"
    }

    return "Не выданы: " + missing.joinToString(", ") { permissionRuName(it) }
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
    saveVisibleInGallery: Boolean,
    quality: String
): String? {
    return try {
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START_RECORDING
            putExtra(RecordingForegroundService.EXTRA_LENS_FACING, lensFacing)
            putExtra(RecordingForegroundService.EXTRA_AUDIO_ENABLED, audioEnabled)
            putExtra(
                RecordingForegroundService.EXTRA_SAVE_VISIBLE_IN_GALLERY,
                saveVisibleInGallery
            )
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
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP_RECORDING
        }

        context.startService(intent)
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

private fun loadRecordings(context: Context): List<RecordingItem> {
    val result = mutableListOf<RecordingItem>()

    try {
        val dir = appVideoDir(context)

        dir.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        }?.forEach { file ->
            result += RecordingItem(
                title = file.name,
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                ),
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                createdAtEpochMs = file.lastModified(),
                visibleInGallery = false
            )
        }
    } catch (_: Exception) {
    }

    try {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
            )
        }

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
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                result += RecordingItem(
                    title = cursor.getString(nameIndex) ?: "LockLens_video.mp4",
                    uri = uri,
                    filePath = null,
                    sizeBytes = cursor.getLong(sizeIndex),
                    createdAtEpochMs = cursor.getLong(dateIndex) * 1000L,
                    visibleInGallery = true
                )
            }
        }
    } catch (_: Exception) {
    }

    return result.sortedByDescending { it.createdAtEpochMs }
}

private fun appVideoDir(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: context.filesDir

    val dir = File(baseDir, "LockLens")

    if (!dir.exists()) {
        dir.mkdirs()
    }

    try {
        File(dir, ".nomedia").createNewFile()
    } catch (_: Exception) {
    }

    return dir
}

private fun openRecording(
    context: Context,
    item: RecordingItem
): String {
    return try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Открыть видео"))
        "Открываем видео"
    } catch (_: ActivityNotFoundException) {
        "На телефоне нет приложения для открытия видео"
    } catch (error: Exception) {
        error.message ?: "Не удалось открыть видео"
    }
}

private fun shareRecording(
    context: Context,
    item: RecordingItem
): String {
    return try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Поделиться записью"))
        "Открываем меню отправки"
    } catch (error: Exception) {
        error.message ?: "Не удалось поделиться записью"
    }
}

private fun deleteRecording(
    context: Context,
    item: RecordingItem
): Boolean {
    return try {
        if (item.visibleInGallery) {
            context.contentResolver.delete(item.uri, null, null) > 0
        } else {
            val file = item.filePath?.let { File(it) }
            file?.delete() == true
        }
    } catch (_: Exception) {
        false
    }
}

private fun exportToGallery(
    context: Context,
    item: RecordingItem
): Boolean {
    if (item.visibleInGallery) return true

    val outputName = normalizeVideoName(item.title)

    return try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/LockLens"
                )
            }
        }

        val destinationUri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false

        context.contentResolver.openInputStream(item.uri).use { input ->
            context.contentResolver.openOutputStream(destinationUri).use { output ->
                if (input == null || output == null) {
                    context.contentResolver.delete(destinationUri, null, null)
                    return false
                }

                input.copyTo(output)
            }
        }

        true
    } catch (_: Exception) {
        false
    }
}

private fun hideFromGallery(
    context: Context,
    item: RecordingItem
): Boolean {
    if (!item.visibleInGallery) return true

    return try {
        val destination = uniqueFile(
            appVideoDir(context),
            normalizeVideoName(item.title)
        )

        context.contentResolver.openInputStream(item.uri).use { input ->
            FileOutputStream(destination).use { output ->
                if (input == null) return false
                input.copyTo(output)
            }
        }

        context.contentResolver.delete(item.uri, null, null) > 0
    } catch (_: Exception) {
        false
    }
}

private fun renameRecording(
    context: Context,
    item: RecordingItem,
    newName: String
): Boolean {
    val normalized = normalizeVideoName(newName)

    return try {
        if (item.visibleInGallery) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, normalized)
            }

            context.contentResolver.update(item.uri, values, null, null) > 0
        } else {
            val source = item.filePath?.let { File(it) } ?: return false
            val parent = source.parentFile ?: appVideoDir(context)
            val destination = uniqueFile(parent, normalized)

            source.renameTo(destination)
        }
    } catch (_: Exception) {
        false
    }
}

private fun normalizeVideoName(input: String): String {
    val cleaned = input
        .trim()
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .ifBlank { "LockLens_video" }

    return if (cleaned.endsWith(".mp4", ignoreCase = true)) {
        cleaned
    } else {
        "$cleaned.mp4"
    }
}

private fun uniqueFile(
    dir: File,
    fileName: String
): File {
    val normalized = normalizeVideoName(fileName)
    val base = normalized.removeSuffix(".mp4")
    var candidate = File(dir, normalized)
    var index = 1

    while (candidate.exists()) {
        candidate = File(dir, "${base}_$index.mp4")
        index++
    }

    return candidate
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 MB"

    val mb = sizeBytes / 1024.0 / 1024.0

    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

private fun formatDate(epochMs: Long): String {
    if (epochMs <= 0L) return "без даты"

    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale.getDefault()
    ).format(Date(epochMs))
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun openBatterySettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    } catch (_: Exception) {
        openAppSettings(context)
    }
}
