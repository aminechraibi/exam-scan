package com.example.examscan.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.examscan.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

enum class DiagnosticCategory(val title: String) {
    RUNTIME("App exceptions and freezes"),
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
    private const val SESSION_ACTIVE = "session_active"
    private lateinit var app: Application
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "ExamScan-Diagnostics") }
    private val watchdog = Executors.newSingleThreadScheduledExecutor { Thread(it, "ExamScan-Watchdog") }
    private val mainHeartbeat = AtomicLong(System.currentTimeMillis())
    private val stallReported = AtomicBoolean(false)

    fun initialize(application: Application) {
        app = application
        val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousSessionInterrupted = prefs.getBoolean(SESSION_ACTIVE, false)
        prefs.edit().putBoolean(SESSION_ACTIVE, true).apply()
        installExceptionHandler()
        startMainThreadWatchdog()
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
        if (previousSessionInterrupted) log(DiagnosticCategory.RUNTIME, "previous_process_ended_without_shutdown", runtimeSnapshot())
    }

    fun isEnabled(context: Context = app): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)
    fun setEnabled(enabled: Boolean) { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply() }
    fun isCategoryEnabled(category: DiagnosticCategory): Boolean = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(category.name, true)
    fun setCategoryEnabled(category: DiagnosticCategory, enabled: Boolean) { app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(category.name, enabled).apply() }

    fun log(category: DiagnosticCategory, event: String, details: Map<String, Any?> = emptyMap(), error: Throwable? = null) {
        if (!::app.isInitialized || !isEnabled() || !isCategoryEnabled(category)) return
        val record = createRecord(category, event, details, error)
        writer.execute { writeNow(record) }
    }

    fun operationStarted(category: DiagnosticCategory, operation: String, details: Map<String, Any?> = emptyMap()): Long {
        val id = System.nanoTime()
        log(category, "operation_started", details + mapOf("operation" to operation, "operation_id" to id))
        return id
    }

    fun operationFinished(category: DiagnosticCategory, operation: String, id: Long, startedAtMillis: Long, details: Map<String, Any?> = emptyMap()) {
        val duration = System.currentTimeMillis() - startedAtMillis
        log(category, if (duration >= 3000) "slow_operation_finished" else "operation_finished", details + mapOf("operation" to operation, "operation_id" to id, "duration_ms" to duration) + runtimeSnapshot())
    }

    fun operationFailed(category: DiagnosticCategory, operation: String, id: Long, startedAtMillis: Long, error: Throwable) {
        log(category, "operation_failed", mapOf("operation" to operation, "operation_id" to id, "duration_ms" to (System.currentTimeMillis() - startedAtMillis)) + runtimeSnapshot(), error)
    }

    private fun createRecord(category: DiagnosticCategory, event: String, details: Map<String, Any?>, error: Throwable?): String = JSONObject().apply {
            put("timestamp", utcNow())
            put("category", category.name)
            put("event", event.take(80))
            put("details", JSONObject(details.mapValues { sanitize(it.value) }))
            error?.let {
                put("error_type", it.javaClass.simpleName.take(80))
                put("error_message", sanitize(it.message))
                put("stack_trace", sanitize(it.stackTraceToString()))
            }
        }.toString()

    fun checkpoint(category: DiagnosticCategory) = log(category, "manual_checkpoint", deviceSnapshot())

    fun exportUri(): Uri {
        flush()
        val exportDir = File(app.cacheDir, "diagnostics").apply { mkdirs() }
        val export = File(exportDir, "ExamScan-diagnostics-${System.currentTimeMillis()}.jsonl")
        val header = JSONObject(mapOf("type" to "diagnostic_header", "generated_at" to utcNow(), "device" to JSONObject(deviceSnapshot()))).toString()
        export.writeText(header + "\n")
        if (logFile().exists()) export.appendBytes(logFile().readBytes())
        return FileProvider.getUriForFile(app, "${app.packageName}.files", export)
    }

    fun clear() { flush(); logFile().delete(); File(logFile().parentFile,"events.previous.jsonl").delete() }
    fun logSize(): Long { flush(); return logFile().takeIf { it.exists() }?.length() ?: 0 }

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

    fun runtimeSnapshot(): Map<String, Any?> {
        val runtime = Runtime.getRuntime()
        val stat = StatFs(app.filesDir.absolutePath)
        return mapOf(
            "heap_used_bytes" to (runtime.totalMemory() - runtime.freeMemory()),
            "heap_free_bytes" to runtime.freeMemory(),
            "heap_max_bytes" to runtime.maxMemory(),
            "available_storage_bytes" to stat.availableBytes,
            "thread_count" to Thread.getAllStackTraces().size
        )
    }

    private fun installExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (isEnabled() && isCategoryEnabled(DiagnosticCategory.RUNTIME)) {
                writeNow(createRecord(DiagnosticCategory.RUNTIME,"uncaught_exception",runtimeSnapshot()+mapOf("thread" to thread.name),error))
            }
            previous?.uncaughtException(thread,error)
        }
    }

    private fun startMainThreadWatchdog() {
        val main = Handler(Looper.getMainLooper())
        watchdog.scheduleAtFixedRate({
            main.post { mainHeartbeat.set(System.currentTimeMillis()); if(stallReported.getAndSet(false)) log(DiagnosticCategory.RUNTIME,"main_thread_recovered",runtimeSnapshot()) }
            val delay = System.currentTimeMillis() - mainHeartbeat.get()
            if(delay >= 5000 && stallReported.compareAndSet(false,true)) {
                val stack = Looper.getMainLooper().thread.stackTrace.joinToString("\n").take(6000)
                log(DiagnosticCategory.RUNTIME,"main_thread_stall",runtimeSnapshot()+mapOf("blocked_ms" to delay,"main_thread_stack" to stack))
            }
        },2,2,TimeUnit.SECONDS)
    }

    @Synchronized private fun writeNow(record: String) { rotateIfNeeded(); logFile().appendText(record+"\n") }
    private fun flush() { if(::app.isInitialized) writer.submit {}.get(5,TimeUnit.SECONDS) }

    private fun sanitize(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean -> value
        else -> value.toString().replace(Regex("(?:content|file)://\\S+"), "[uri]").replace(Regex("/[^ ]+"), "[path]").take(240)
    }
    private fun logFile() = File(app.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }
    private fun rotateIfNeeded() { if (logFile().let { it.exists() && it.length() >= MAX_BYTES }) { File(logFile().parentFile, "events.previous.jsonl").also { logFile().copyTo(it, overwrite = true) }; logFile().delete() } }
    private fun utcNow() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date())
}
