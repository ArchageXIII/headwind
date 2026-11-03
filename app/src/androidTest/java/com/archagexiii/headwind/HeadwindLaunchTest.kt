package com.archagexiii.headwind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.archagexiii.headwind.fan.Connection
import com.archagexiii.headwind.fan.FanStatus
import com.archagexiii.headwind.fan.FanUiState
import com.archagexiii.headwind.ui.HeadwindApp
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
                { actions += "plus" }, { actions += "minus" }, { actions += "off" })
        }
        compose.onNodeWithContentDescription("Increase fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Decrease fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Stop fan and leave it in standby").assertIsDisplayed().performClick()
        assertEquals(listOf("plus", "minus", "off"), actions)
    }
}
