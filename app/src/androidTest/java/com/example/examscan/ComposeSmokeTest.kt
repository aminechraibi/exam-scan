package com.example.examscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createExamButtonOpensValidatedForm() {
        compose.onNodeWithTag("create_exam_button").assertIsDisplayed().performClick()
        compose.onNodeWithText("Create exam").assertIsDisplayed()
        compose.onNodeWithText("Exam name").assertIsDisplayed()
        compose.onNodeWithText("Pages per paper").assertIsDisplayed()
        compose.onNodeWithTag("confirm_create_exam_button").assertIsNotEnabled()
        compose.onNodeWithTag("exam_name_input").performTextInput("Math")
        compose.onNodeWithTag("confirm_create_exam_button").assertIsEnabled()
        compose.onNodeWithTag("pages_per_paper_input").performTextClearance()
        compose.onNodeWithTag("pages_per_paper_input").performTextInput("0")
        compose.onNodeWithTag("confirm_create_exam_button").assertIsNotEnabled()
    }

    @Test fun homeScreenSurvivesActivityRecreation() {
        compose.onNodeWithTag("create_exam_button").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("create_exam_button").assertIsDisplayed()
    }

    @Test fun diagnosticsSettingsAreReachable() {
        compose.onNodeWithTag("diagnostics_settings_button").performClick()
        compose.onNodeWithText("Diagnostics").assertIsDisplayed()
        compose.onNodeWithTag("diagnostics_master_switch").assertIsDisplayed()
        compose.onNodeWithTag("share_diagnostics_button").assertIsNotEnabled()
    }

}
