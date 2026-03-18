/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.apkeditor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amaze.filemanager.R
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.utils.DatapointParcelable
import com.amaze.filemanager.utils.ObtainableServiceBinder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service for long-running APK editor operations.
 * Handles decompilation, recompilation, and signing in background threads.
 */
class ApkEditorService : Service() {

    private val log: Logger = LoggerFactory.getLogger(ApkEditorService::class.java)

    private val binder = ObtainableServiceBinder(this)
    private val isCancelled = AtomicBoolean(false)
    private var currentOperation: ApkEditorOperation? = null
    private var progressListener: ProgressListener? = null
    private var currentProgress: Int = 0

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var customSmallContentViews: RemoteViews? = null
    private var customBigContentViews: RemoteViews? = null

    companion object {
        const val CHANNEL_ID = "apk_editor_channel"
        const val NOTIFICATION_ID = 1001

        // Intent extras
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_PROJECT_DIR = "project_dir"
        const val EXTRA_KEYSTORE_PATH = "keystore_path"
        const val EXTRA_KEYSTORE_PASSWORD = "keystore_password"
        const val EXTRA_KEY_ALIAS = "key_alias"
        const val EXTRA_KEY_PASSWORD = "key_password"

        // Broadcast actions
        const val ACTION_CANCEL = "com.amaze.filemanager.apkeditor.CANCEL"
        const val ACTION_PROGRESS = "com.amaze.filemanager.apkeditor.PROGRESS"
        const val ACTION_COMPLETE = "com.amaze.filemanager.apkeditor.COMPLETE"

        // Broadcast extras
        const val EXTRA_PROGRESS_PERCENT = "progress_percent"
        const val EXTRA_CURRENT_FILE = "current_file"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_RESULT_APK_INFO = "apk_info"

        private var serviceRef: WeakReference<ApkEditorService>? = null

        fun getService(): ApkEditorService? = serviceRef?.get()
    }

    override fun onCreate() {
        super.onCreate()
        serviceRef = WeakReference(this)
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        setupNotificationBuilder()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelOperation()
            }
            else -> {
                val operation = intent?.getSerializableExtra(EXTRA_OPERATION) as? ApkEditorOperation
                when (operation) {
                    ApkEditorOperation.DECOMPILE -> {
                        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: return START_NOT_STICKY
                        val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return START_NOT_STICKY
                        startDecompile(apkPath, outputPath)
                    }
                    ApkEditorOperation.RECOMPILE -> {
                        val projectDir = intent.getStringExtra(EXTRA_PROJECT_DIR) ?: return START_NOT_STICKY
                        val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return START_NOT_STICKY
                        startRecompile(projectDir, outputPath)
                    }
                    ApkEditorOperation.SIGN -> {
                        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: return START_NOT_STICKY
                        val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return START_NOT_STICKY
                        val keystorePath = intent.getStringExtra(EXTRA_KEYSTORE_PATH)
                        val keystorePassword = intent.getStringExtra(EXTRA_KEYSTORE_PASSWORD) ?: ApkSigner.TEST_KEYSTORE_PASSWORD
                        val keyAlias = intent.getStringExtra(EXTRA_KEY_ALIAS) ?: ApkSigner.TEST_KEY_ALIAS
                        val keyPassword = intent.getStringExtra(EXTRA_KEY_PASSWORD) ?: ApkSigner.TEST_KEY_PASSWORD
                        startSign(apkPath, outputPath, keystorePath, keystorePassword, keyAlias, keyPassword)
                    }
                    ApkEditorOperation.GET_INFO -> {
                        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: return START_NOT_STICKY
                        startGetInfo(apkPath)
                    }
                    else -> {
                        log.warn("Unknown operation: $operation")
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelOperation()
        serviceRef = null
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.apk_editor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.apk_editor_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Setup notification builder
     */
    private fun setupNotificationBuilder() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        customSmallContentViews = RemoteViews(packageName, R.layout.notification_service_small)
        customBigContentViews = RemoteViews(packageName, R.layout.notification_service_big)

        val cancelIntent = Intent(ACTION_CANCEL).apply { setPackage(packageName) }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            this,
            1234,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zip_box_grey)
            .setContentTitle(getString(R.string.apk_editor_service_title))
            .setContentIntent(pendingIntent)
            .setCustomContentView(customSmallContentViews)
            .setCustomBigContentView(customBigContentViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), cancelPendingIntent)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Update notification with progress
     */
    private fun updateNotification(progress: Int, currentFile: String) {
        currentProgress = progress
        customSmallContentViews?.apply {
            setProgressBar(R.id.notification_service_progressBar_small, 100, progress, false)
            setTextViewText(R.id.notification_service_textView_filename_small, currentFile)
        }
        customBigContentViews?.apply {
            setProgressBar(R.id.notification_service_progressBar_big, 100, progress, false)
            setTextViewText(R.id.notification_service_textView_filename_big, currentFile)
            setTextViewText(
                R.id.notification_service_textView_written_big,
                "$progress%"
            )
        }
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Show completion notification
     */
    private fun showCompletionNotification(success: Boolean, message: String) {
        notificationBuilder.apply {
            setOngoing(false)
            setAutoCancel(true)
            setContentText(message)
            setProgress(0, 0, false)
            mActions.clear()
        }
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Start decompilation operation
     */
    private fun startDecompile(apkPath: String, outputDir: String) {
        currentOperation = ApkEditorOperation.DECOMPILE
        isCancelled.set(false)
        updateNotification(0, getString(R.string.apk_editor_decompiling))

        Thread {
            try {
                val engine = ApkEditorEngine(applicationContext)
                engine.setProgressCallback { progress ->
                    if (isCancelled.get()) return@setProgressCallback
                    updateNotification(progress.progress, progress.currentFile)
                    broadcastProgress(progress)

                    progressListener?.onProgressUpdate(
                        progress.progress,
                        progress.currentFile
                    )
                }

                val success = engine.decompileApk(apkPath, outputDir)

                val result = ApkEditorResult(
                    success = success,
                    operation = ApkEditorOperation.DECOMPILE,
                    message = if (success) getString(R.string.apk_editor_decompile_success) else getString(R.string.apk_editor_decompile_failed),
                    outputPath = outputDir
                )

                broadcastComplete(result)
                showCompletionNotification(success, result.message)

            } catch (e: Exception) {
                log.error("Decompilation failed", e)
                val result = ApkEditorResult(
                    success = false,
                    operation = ApkEditorOperation.DECOMPILE,
                    message = "${getString(R.string.apk_editor_decompile_failed)}: ${e.message}"
                )
                broadcastComplete(result)
                showCompletionNotification(false, result.message)
            }
        }.start()
    }

    /**
     * Start recompilation operation
     */
    private fun startRecompile(projectDir: String, outputPath: String) {
        currentOperation = ApkEditorOperation.RECOMPILE
        isCancelled.set(false)
        updateNotification(0, getString(R.string.apk_editor_recompiling))

        Thread {
            try {
                val engine = ApkEditorEngine(applicationContext)
                engine.setProgressCallback { progress ->
                    if (isCancelled.get()) return@setProgressCallback
                    updateNotification(progress.progress, progress.currentFile)
                    broadcastProgress(progress)

                    progressListener?.onProgressUpdate(
                        progress.progress,
                        progress.currentFile
                    )
                }

                val success = engine.recompileApk(projectDir, outputPath)

                val result = ApkEditorResult(
                    success = success,
                    operation = ApkEditorOperation.RECOMPILE,
                    message = if (success) getString(R.string.apk_editor_recompile_success) else getString(R.string.apk_editor_recompile_failed),
                    outputPath = outputPath
                )

                broadcastComplete(result)
                showCompletionNotification(success, result.message)

            } catch (e: Exception) {
                log.error("Recompilation failed", e)
                val result = ApkEditorResult(
                    success = false,
                    operation = ApkEditorOperation.RECOMPILE,
                    message = "${getString(R.string.apk_editor_recompile_failed)}: ${e.message}"
                )
                broadcastComplete(result)
                showCompletionNotification(false, result.message)
            }
        }.start()
    }

    /**
     * Start signing operation
     */
    private fun startSign(
        apkPath: String,
        outputPath: String,
        keystorePath: String?,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        currentOperation = ApkEditorOperation.SIGN
        isCancelled.set(false)
        updateNotification(0, getString(R.string.apk_editor_signing))

        Thread {
            try {
                val engine = ApkEditorEngine(applicationContext)
                engine.setProgressCallback { progress ->
                    if (isCancelled.get()) return@setProgressCallback
                    updateNotification(progress.progress, progress.currentFile)
                    broadcastProgress(progress)
                }

                val success = engine.signApk(
                    apkPath,
                    outputPath,
                    keystorePath,
                    keystorePassword,
                    keyAlias,
                    keyPassword
                )

                val result = ApkEditorResult(
                    success = success,
                    operation = ApkEditorOperation.SIGN,
                    message = if (success) getString(R.string.apk_editor_sign_success) else getString(R.string.apk_editor_sign_failed),
                    outputPath = outputPath
                )

                broadcastComplete(result)
                showCompletionNotification(success, result.message)

            } catch (e: Exception) {
                log.error("Signing failed", e)
                val result = ApkEditorResult(
                    success = false,
                    operation = ApkEditorOperation.SIGN,
                    message = "${getString(R.string.apk_editor_sign_failed)}: ${e.message}"
                )
                broadcastComplete(result)
                showCompletionNotification(false, result.message)
            }
        }.start()
    }

    /**
     * Start get info operation
     */
    private fun startGetInfo(apkPath: String) {
        currentOperation = ApkEditorOperation.GET_INFO
        isCancelled.set(false)
        updateNotification(0, getString(R.string.apk_editor_getting_info))

        Thread {
            try {
                val engine = ApkEditorEngine(applicationContext)
                val apkInfo = engine.getApkInfo(apkPath)

                val result = ApkEditorResult(
                    success = true,
                    operation = ApkEditorOperation.GET_INFO,
                    message = getString(R.string.apk_editor_info_retrieved),
                    apkInfo = apkInfo
                )

                broadcastComplete(result)
                showCompletionNotification(true, result.message)

            } catch (e: Exception) {
                log.error("Get info failed", e)
                val result = ApkEditorResult(
                    success = false,
                    operation = ApkEditorOperation.GET_INFO,
                    message = "${getString(R.string.apk_editor_get_info_failed)}: ${e.message}"
                )
                broadcastComplete(result)
                showCompletionNotification(false, result.message)
            }
        }.start()
    }

    /**
     * Cancel current operation
     */
    fun cancelOperation() {
        isCancelled.set(true)
        currentOperation = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Set progress listener
     */
    fun setProgressListener(listener: ProgressListener?) {
        this.progressListener = listener
    }

    /**
     * Broadcast progress update
     */
    private fun broadcastProgress(progress: ApkEditorProgress) {
        val intent = Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS_PERCENT, progress.progress)
            putExtra(EXTRA_CURRENT_FILE, progress.currentFile)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcast operation complete
     */
    private fun broadcastComplete(result: ApkEditorResult) {
        val intent = Intent(ACTION_COMPLETE).apply {
            putExtra(EXTRA_SUCCESS, result.success)
            putExtra(EXTRA_MESSAGE, result.message)
            putExtra(EXTRA_OPERATION, result.operation)
            putExtra(EXTRA_OUTPUT_PATH, result.outputPath)
            putExtra(EXTRA_RESULT_APK_INFO, result.apkInfo)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        progressListener?.onOperationComplete(result)
    }

    /**
     * Check if operation is cancelled
     */
    fun isOperationCancelled(): Boolean = isCancelled.get()

    /**
     * Get current progress
     */
    fun getCurrentProgress(): Int = currentProgress

    /**
     * Get current operation
     */
    fun getCurrentOperation(): ApkEditorOperation? = currentOperation

    /**
     * Progress listener interface
     */
    interface ProgressListener {
        fun onProgressUpdate(progress: Int, currentFile: String)
        fun onOperationComplete(result: ApkEditorResult)
    }
}
