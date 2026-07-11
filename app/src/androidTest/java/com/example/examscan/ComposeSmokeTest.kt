package com.example.examscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    }
}
