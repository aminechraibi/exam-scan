package com.example.examscan.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun reset() {
        Diagnostics.clear()
        Diagnostics.setEnabled(false)
        DiagnosticCategory.entries.forEach { Diagnostics.setCategoryEnabled(it, true) }
    }

    @Test fun disabledDiagnosticsDoNotWriteEvents() {
        Diagnostics.clear()
        Diagnostics.setEnabled(false)
        Diagnostics.log(DiagnosticCategory.ML_KIT, "ignored")
        assertTrue(Diagnostics.logSize() == 0L)
    }

    @Test fun enabledCategoryWritesSanitizedShareableJsonLines() {
        Diagnostics.clear()
        Diagnostics.setEnabled(true)
        Diagnostics.log(
            DiagnosticCategory.STORAGE,
            "write_failed",
            mapOf("uri" to "content://private/scan/1", "path" to "/data/user/0/private.jpg"),
            IllegalStateException("failed at /data/user/0/private.jpg")
        )
        assertTrue(Diagnostics.logSize() > 0)
        val exported = context.contentResolver.openInputStream(Diagnostics.exportUri())!!.bufferedReader().use { it.readText() }
        assertTrue(exported.contains("write_failed"))
        assertTrue(exported.contains("IllegalStateException"))
        assertFalse(exported.contains("content://private"))
        assertFalse(exported.contains("/data/user/0"))
    }

    @Test fun disabledCategoryIsNotRecorded() {
        Diagnostics.clear()
        Diagnostics.setEnabled(true)
        Diagnostics.setCategoryEnabled(DiagnosticCategory.SHARING, false)
        Diagnostics.log(DiagnosticCategory.SHARING, "hidden")
        assertTrue(Diagnostics.logSize() == 0L)
    }
}
