package com.archagexiii.headwind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.archagexiii.headwind.fan.Connection
import com.archagexiii.headwind.fan.FanStatus
import com.archagexiii.headwind.fan.FanUiState
import com.archagexiii.headwind.fan.FanDevice
import com.archagexiii.headwind.ui.HeadwindApp
import com.archagexiii.headwind.ui.FanSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests use a fake state and never connect to or operate a real fan. */
class HeadwindLaunchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeLargeControlsDispatchTheCorrectActions() {
        val actions = mutableListOf<String>()
        compose.setContent {
            HeadwindApp(FanUiState(Connection.READY, FanStatus(1, 0)),
                { actions += "plus" }, { actions += "minus" }, { actions += "off" },
                onSettings = { actions += "settings" })
        }
        compose.onNodeWithContentDescription("Increase fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Decrease fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Stop fan and leave it in standby").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Fan settings").assertIsDisplayed().performClick()
        assertEquals(listOf("plus", "minus", "off", "settings"), actions)
    }

    @Test fun settingsDistinguishesFansAndProvidesScanForgetAndClose() {
        val first = FanDevice("HEADWIND SAME", "AA:BB:CC:00:00:01")
        val second = FanDevice("HEADWIND SAME", "AA:BB:CC:00:00:02")
        val actions = mutableListOf<String>()
        compose.setContent {
            FanSettings(first, listOf(first, second), false, "Choose a fan", "Connected", false,
                { actions += "scan" }, { actions += it }, { actions += "forget" }, { actions += "close" })
        }
        compose.onNodeWithText("00:00:02").performScrollTo().performClick()
        compose.onNodeWithText("Scan again").performScrollTo().performClick()
        compose.onNodeWithText("Forget fan").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf(second.address, "scan", "forget", "close"), actions)
    }
}
