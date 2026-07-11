package com.example.examscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfirmDeleteButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletionCanBeCancelledOrConfirmed() {
        var deletes = 0
        compose.setContent { ExamScanTheme { ConfirmDeleteButton("Delete test") { deletes++ } } }
        compose.onNodeWithContentDescription("Delete test").performClick()
        compose.onNodeWithText("Confirm deletion").assertIsDisplayed()
        compose.onNodeWithTag("cancel_delete_button").performClick()
        assertEquals(0, deletes)
        compose.onNodeWithContentDescription("Delete test").performClick()
        compose.onNodeWithTag("confirm_delete_button").performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}
