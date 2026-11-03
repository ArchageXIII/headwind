package com.archagexiii.headwind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadwindLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherDisplaysHeadwind() {
        composeRule.onNodeWithText("Headwind").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to Headwind").assertIsDisplayed()
    }
}
