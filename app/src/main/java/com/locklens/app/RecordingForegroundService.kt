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
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
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
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OutputSpec(
    val options: OutputOptions,
    val outputFile: File?
)

class RecordingForegroundService : LifecycleService() {

    private var activeRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentOutputFile: File? = null

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
            sendState(STATE_RECORDING, message = "Запись уже идёт")
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            sendState(STATE_FAILED, message = "Нет разрешения камеры")
            stopSelf()
            return
        }

        val lensFacing = intent.getIntExtra(
            EXTRA_LENS_FACING,
            CameraSelector.LENS_FACING_BACK
        )
        val audioEnabled = intent.getBooleanExtra(EXTRA_AUDIO_ENABLED, true)
        val saveVisibleInGallery = intent.getBooleanExtra(
            EXTRA_SAVE_VISIBLE_IN_GALLERY,
            false
        )
        val qualityCode = intent.getStringExtra(EXTRA_QUALITY) ?: QUALITY_FHD

        val audioAllowed =
            audioEnabled &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

        startForegroundCompat(audioAllowed)
        updateNotification("Подготовка камеры...")
        sendState(STATE_PREPARING, message = "Подготовка камеры...")

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    val qualitySelector = QualitySelector.fromOrderedList(
                        orderedQualities(qualityCode),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )

                    val recorder = Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build()

                    val videoCapture = VideoCapture.withOutput(recorder)
                    val outputSpec = createOutputSpec(saveVisibleInGallery)

                    currentOutputFile = outputSpec.outputFile

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        cameraSelector,
                        videoCapture
                    )

                    var pendingRecording: PendingRecording =
                        recorder.prepareRecording(this, outputSpec.options)

                    if (audioAllowed) {
                        pendingRecording = pendingRecording.withAudioEnabled()
                    }

                    activeRecording = pendingRecording.start(
                        ContextCompat.getMainExecutor(this)
                    ) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                updateNotification("Идёт запись. Экран можно выключить.")
                                sendState(
                                    STATE_RECORDING,
                                    message = "Идёт запись. Экран можно выключить."
                                )
                            }

                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    currentOutputFile?.delete()

                                    sendState(
                                        STATE_FAILED,
                                        message = "Ошибка записи: ${event.error}"
                                    )
                                } else {
                                    sendState(
                                        STATE_COMPLETED,
                                        uri = event.outputResults.outputUri.toString(),
                                        path = currentOutputFile?.absolutePath,
                                        message = if (saveVisibleInGallery) {
                                            "Запись сохранена в Галерею / LockLens"
                                        } else {
                                            "Запись сохранена во внутреннюю библиотеку LockLens"
                                        }
                                    )
                                }

                                cleanupAfterRecording()
                            }
                        }
                    }
                } catch (error: Exception) {
                    currentOutputFile?.delete()

                    sendState(
                        STATE_FAILED,
                        message = error.message ?: "Не удалось запустить запись"
                    )

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

        updateNotification("Останавливаем и сохраняем запись...")
        sendState(STATE_SAVING, message = "Останавливаем и сохраняем запись...")

        activeRecording = null
        recording.stop()
    }

    private fun createOutputSpec(saveVisibleInGallery: Boolean): OutputSpec {
        val fileName = "LockLens_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            ".mp4"

        if (saveVisibleInGallery) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/LockLens"
                    )
                }
            }

            val options = MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            return OutputSpec(options, null)
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

        return OutputSpec(
            options = FileOutputOptions.Builder(outputFile).build(),
            outputFile = outputFile
        )
    }

    private fun startForegroundCompat(audioAllowed: Boolean) {
        val notification = buildNotification(
            text = "Запуск записи...",
            ongoing = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

            if (audioAllowed) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                serviceType
            )
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
        currentOutputFile = null

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

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(
        text: String,
        ongoing: Boolean
    ): Notification {
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            android.app.PendingIntent.FLAG_IMMUTABLE

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
            .notify(
                NOTIFICATION_ID,
                buildNotification(text = text, ongoing = true)
            )
    }

    private fun sendState(
        state: String,
        uri: String? = null,
        path: String? = null,
        message: String? = null
    ) {
        val intent = Intent(BROADCAST_RECORDING_STATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_OUTPUT_URI, uri)
            putExtra(EXTRA_OUTPUT_PATH, path)
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
        const val EXTRA_OUTPUT_URI = "extra_output_uri"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_LENS_FACING = "extra_lens_facing"
        const val EXTRA_AUDIO_ENABLED = "extra_audio_enabled"
        const val EXTRA_SAVE_VISIBLE_IN_GALLERY = "extra_save_visible_in_gallery"
        const val EXTRA_QUALITY = "extra_quality"

        const val STATE_PREPARING = "preparing"
        const val STATE_RECORDING = "recording"
        const val STATE_SAVING = "saving"
        const val STATE_COMPLETED = "completed"
        const val STATE_FAILED = "failed"

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
        RecordingForegroundService.QUALITY_UHD -> listOf(
            Quality.UHD,
            Quality.FHD,
            Quality.HD,
            Quality.SD
        )

        RecordingForegroundService.QUALITY_HD -> listOf(
            Quality.HD,
            Quality.SD,
            Quality.FHD,
            Quality.UHD
        )

        RecordingForegroundService.QUALITY_SD -> listOf(
            Quality.SD,
            Quality.HD,
            Quality.FHD,
            Quality.UHD
        )

        else -> listOf(
            Quality.FHD,
            Quality.HD,
            Quality.SD,
            Quality.UHD
        )
    }
}
