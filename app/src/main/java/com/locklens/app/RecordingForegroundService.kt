package com.locklens.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class OutputSpec {
    data class FileSpec(
        val options: FileOutputOptions,
        val file: File
    ) : OutputSpec()

    data class MediaStoreSpec(
        val options: MediaStoreOutputOptions
    ) : OutputSpec()

    data class SafSpec(
        val options: FileDescriptorOutputOptions,
        val uri: Uri
    ) : OutputSpec()
}

class RecordingForegroundService : LifecycleService() {

    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentPrivateFile: File? = null
    private var currentSafUri: Uri? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_RECORDING -> startLockLensRecording(intent)
            ACTION_STOP_RECORDING -> stopLockLensRecording()
            else -> stopSelf()
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLockLensRecording(intent: Intent) {
        if (activeRecording != null) {
            sendState(STATE_RECORDING, "Запись уже идёт")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            sendState(STATE_FAILED, "Нет разрешения камеры")
            stopSelf()
            return
        }

        val lensFacing = intent.getIntExtra(EXTRA_LENS_FACING, CameraSelector.LENS_FACING_BACK)
        val audioEnabled = intent.getBooleanExtra(EXTRA_AUDIO_ENABLED, true)
        val storageMode = intent.getStringExtra(EXTRA_STORAGE_MODE) ?: STORAGE_PRIVATE
        val customFolderUri = intent.getStringExtra(EXTRA_CUSTOM_FOLDER_URI)
        val qualityCode = intent.getStringExtra(EXTRA_QUALITY) ?: QUALITY_FHD

        val audioAllowed =
            audioEnabled &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        startForegroundCompat(audioAllowed)
        updateNotification("Подготовка камеры...")
        sendState(STATE_PREPARING, "Подготовка камеры...")

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.fromOrderedList(
                                orderedQualities(qualityCode),
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                            )
                        )
                        .build()

                    val videoCapture = VideoCapture.withOutput(recorder)
                    val outputSpec = createOutputSpec(storageMode, customFolderUri)

                    provider.unbindAll()
                    provider.bindToLifecycle(this, cameraSelector, videoCapture)

                    var pendingRecording: PendingRecording = when (outputSpec) {
                        is OutputSpec.FileSpec -> recorder.prepareRecording(this, outputSpec.options)
                        is OutputSpec.MediaStoreSpec -> recorder.prepareRecording(this, outputSpec.options)
                        is OutputSpec.SafSpec -> recorder.prepareRecording(this, outputSpec.options)
                    }

                    if (audioAllowed) {
                        pendingRecording = pendingRecording.withAudioEnabled()
                    }

                    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                updateNotification("Идёт запись. Экран можно выключить.")
                                sendState(STATE_RECORDING, "Идёт запись. Экран можно выключить.")
                            }

                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    currentPrivateFile?.delete()
                                    sendState(STATE_FAILED, "Ошибка записи: ${event.error}")
                                } else {
                                    sendState(
                                        STATE_COMPLETED,
                                        when (storageMode) {
                                            STORAGE_GALLERY -> "Запись сохранена в Галерею / LockLens"
                                            STORAGE_CUSTOM -> "Запись сохранена в выбранную папку"
                                            else -> "Запись сохранена во внутреннюю библиотеку"
                                        }
                                    )
                                }

                                cleanupAfterRecording()
                            }
                        }
                    }
                } catch (error: Exception) {
                    currentPrivateFile?.delete()
                    sendState(STATE_FAILED, error.message ?: "Не удалось запустить запись")
                    cleanupAfterRecording()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun stopLockLensRecording() {
        val recording = activeRecording

        if (recording == null) {
            cleanupAfterRecording()
            return
        }

        updateNotification("Останавливаем и сохраняем...")
        sendState(STATE_SAVING, "Останавливаем и сохраняем...")
        activeRecording = null
        recording.stop()
    }

    private fun createOutputSpec(storageMode: String, customFolderUri: String?): OutputSpec {
        val fileName = "LockLens_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            ".mp4"

        if (storageMode == STORAGE_CUSTOM && !customFolderUri.isNullOrBlank()) {
            val treeUri = Uri.parse(customFolderUri)
            val tree = DocumentFile.fromTreeUri(this, treeUri)
                ?: error("Не удалось открыть выбранную папку")

            val document = tree.createFile("video/mp4", fileName)
                ?: error("Не удалось создать файл в выбранной папке")

            val pfd = contentResolver.openFileDescriptor(document.uri, "rw")
                ?: error("Нет доступа к выбранной папке")

            currentSafUri = document.uri

            return OutputSpec.SafSpec(
                FileDescriptorOutputOptions.Builder(pfd).build(),
                document.uri
            )
        }

        if (storageMode == STORAGE_GALLERY) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LockLens")
                }
            }

            val options = MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            return OutputSpec.MediaStoreSpec(options)
        }

        val baseDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val lockLensDir = File(baseDir, "LockLens")

        if (!lockLensDir.exists()) {
            lockLensDir.mkdirs()
        }

        try {
            File(lockLensDir, ".nomedia").createNewFile()
        } catch (_: Exception) {
        }

        val outputFile = File(lockLensDir, fileName)
        currentPrivateFile = outputFile

        return OutputSpec.FileSpec(
            FileOutputOptions.Builder(outputFile).build(),
            outputFile
        )
    }

    private fun startForegroundCompat(audioAllowed: Boolean) {
        val notification = buildNotification("Запуск записи...", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

            if (audioAllowed) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun cleanupAfterRecording() {
        try {
            activeRecording?.close()
        } catch (_: Exception) {
        }

        activeRecording = null

        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }

        cameraProvider = null
        currentPrivateFile = null
        currentSafUri = null

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Активная запись LockLens",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Показывается, пока LockLens записывает видео"
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, ongoing: Boolean): Notification {
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE

        val openIntent = android.app.PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            flags
        )

        val stopIntent = android.app.PendingIntent.getService(
            this,
            20,
            Intent(this, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP_RECORDING
            },
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LockLens записывает")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(R.drawable.ic_notification, "Стоп", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, true))
    }

    private fun sendState(state: String, message: String) {
        val intent = Intent(BROADCAST_RECORDING_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_MESSAGE, message)
        }

        sendBroadcast(intent)
    }

    override fun onDestroy() {
        try {
            activeRecording?.close()
        } catch (_: Exception) {
        }

        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RECORDING = "com.locklens.app.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.locklens.app.STOP_RECORDING"

        const val BROADCAST_RECORDING_STATE = "com.locklens.app.RECORDING_STATE"

        const val EXTRA_STATE = "extra_state"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_LENS_FACING = "extra_lens_facing"
        const val EXTRA_AUDIO_ENABLED = "extra_audio_enabled"
        const val EXTRA_STORAGE_MODE = "extra_storage_mode"
        const val EXTRA_CUSTOM_FOLDER_URI = "extra_custom_folder_uri"
        const val EXTRA_QUALITY = "extra_quality"

        const val STATE_PREPARING = "preparing"
        const val STATE_RECORDING = "recording"
        const val STATE_SAVING = "saving"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"

        const val STORAGE_PRIVATE = "private"
        const val STORAGE_GALLERY = "gallery"
        const val STORAGE_CUSTOM = "custom"

        const val QUALITY_SD = "SD"
        const val QUALITY_HD = "HD"
        const val QUALITY_FHD = "FHD"
        const val QUALITY_UHD = "UHD"

        private const val CHANNEL_ID = "locklens_recording"
        private const val NOTIFICATION_ID = 1001
    }
}

private fun orderedQualities(code: String): List<Quality> {
    return when (code) {
        RecordingForegroundService.QUALITY_UHD -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        RecordingForegroundService.QUALITY_HD -> listOf(Quality.HD, Quality.SD, Quality.FHD, Quality.UHD)
        RecordingForegroundService.QUALITY_SD -> listOf(Quality.SD, Quality.HD, Quality.FHD, Quality.UHD)
        else -> listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.UHD)
    }
}
