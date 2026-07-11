package com.example.examscan.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.examscan.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticCategory(val title: String) {
    ML_KIT("Physical-phone ML Kit"),
    DOCUMENT_QUALITY("Real document quality"),
    STORAGE("Storage and low-space"),
    SHARING("External sharing"),
    LIFECYCLE("Reboot, lock, and lifecycle"),
    RELEASE("Production release signing")
}

object Diagnostics {
    private const val PREFS = "diagnostic_settings"
    private const val ENABLED = "enabled"
    private const val MAX_BYTES = 1_000_000L
    private lateinit var app: Application

    fun initialize(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = log(DiagnosticCategory.LIFECYCLE, "activity_created", mapOf("restored" to (state != null)))
            override fun onActivityStarted(activity: Activity) = log(DiagnosticCategory.LIFECYCLE, "activity_started")
            override fun onActivityResumed(activity: Activity) = log(DiagnosticCategory.LIFECYCLE, "activity_resumed")
            override fun onActivityPaused(activity: Activity) = log(DiagnosticCategory.LIFECYCLE, "activity_paused")
            override fun onActivityStopped(activity: Activity) = log(DiagnosticCategory.LIFECYCLE, "activity_stopped")
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = log(DiagnosticCategory.LIFECYCLE, "state_saved")
            override fun onActivityDestroyed(activity: Activity) = log(DiagnosticCategory.LIFECYCLE, "activity_destroyed", mapOf("changing_configuration" to activity.isChangingConfigurations))
        })
        log(DiagnosticCategory.RELEASE, "app_started", deviceSnapshot())
    }

    fun isEnabled(context: Context = app): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun setEnabled(enabled: Boolean) { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply() }
    fun isCategoryEnabled(category: DiagnosticCategory): Boolean = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(category.name, true)
    fun setCategoryEnabled(category: DiagnosticCategory, enabled: Boolean) { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(category.name, enabled).apply() }

    @Synchronized fun log(category: DiagnosticCategory, event: String, details: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        if (!::app.isInitialized || !isEnabled() || !isCategoryEnabled(category)) return
        rotateIfNeeded()
        val record = JSONObject().apply {
            put("timestamp", utcNow())
            put("category", category.name)
            put("event", event.take(80))
            put("details", JSONObject(details.mapValues { sanitize(it.value) }))
            error?.let {
                put("error_type", it.javaClass.simpleName.take(80))
                put("error_message", sanitize(it.message))
            }
        }
        logFile().appendText(record.toString() + "\n")
    }

    fun checkpoint(category: DiagnosticCategory) = log(category, "manual_checkpoint", deviceSnapshot())

    fun exportUri(): Uri {
        val exportDir = File(app.cacheDir, "diagnostics").apply { mkdirs() }
        val export = File(exportDir, "ExamScan-diagnostics-${System.currentTimeMillis()}.jsonl")
        val header = JSONObject(mapOf("type" to "diagnostic_header", "generated_at" to utcNow(), "device" to JSONObject(deviceSnapshot()))).toString()
        export.writeText(header + "\n")
        if (logFile().exists()) export.appendBytes(logFile().readBytes())
        return FileProvider.getUriForFile(app, "${app.packageName}.files", export)
    }

    fun clear() { logFile().delete() }
    fun logSize(): Long = logFile().takeIf { it.exists() }?.length() ?: 0

    fun share(context: Context) {
        val uri = exportUri()
        log(DiagnosticCategory.SHARING, "diagnostic_share_opened")
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Share diagnostics"))
    }

    private fun deviceSnapshot(): Map<String, Any?> {
        val stat = StatFs(app.filesDir.absolutePath)
        val signing = if (Build.VERSION.SDK_INT >= 28) {
            val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (info?.hasMultipleSigners() == true) "multiple" else if (info != null) "present" else "missing"
        } else "legacy_present"
        return mapOf(
            "app_version" to BuildConfig.VERSION_NAME,
            "version_code" to BuildConfig.VERSION_CODE,
            "debug_build" to BuildConfig.DEBUG,
            "signing" to signing,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "api" to Build.VERSION.SDK_INT,
            "available_storage_bytes" to stat.availableBytes,
            "total_storage_bytes" to stat.totalBytes
        )
    }

    private fun sanitize(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean -> value
        else -> value.toString().replace(Regex("(?:content|file)://\\S+"), "[uri]").replace(Regex("/[^ ]+"), "[path]").take(240)
    }
    private fun logFile() = File(app.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }
    private fun rotateIfNeeded() { if (logFile().let { it.exists() && it.length() >= MAX_BYTES }) { File(logFile().parentFile, "events.previous.jsonl").also { logFile().copyTo(it, overwrite = true) }; logFile().delete() } }
    private fun utcNow() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date())
}
