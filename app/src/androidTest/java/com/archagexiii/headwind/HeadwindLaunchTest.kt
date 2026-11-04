package com.archagexiii.headwind

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    @Test fun controlsAreSeparateAndSpeedButtonsProvideHaptics() {
        val actions = mutableListOf<String>()
        val effects = mutableListOf<HapticFeedbackType>()
        val fanState = mutableStateOf(FanUiState(Connection.READY, FanStatus(1, 0)))
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { effects += hapticFeedbackType }
            }) {
                HeadwindApp(fanState.value,
                    { actions += "plus" }, { actions += "minus" }, {
                        actions += "power"
                        fanState.value = fanState.value.copy(requestedSpeed = fanState.value.toggledPowerSpeed)
                    }, onSettings = { actions += "settings" })
            }
        }
        compose.onNodeWithContentDescription("Increase fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Decrease fan speed").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Fan power").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("20%", substring = false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Fan power").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Fan settings").assertIsDisplayed().performClick()
        assertEquals(listOf("plus", "minus", "power", "power", "settings"), actions)
        assertEquals(listOf(HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentTick), effects)
    }

    @Test fun bottomDisplayIgnoresTapsAndUpwardSwipes() {
        val actions = mutableListOf<String>()
        compose.setContent {
            HeadwindApp(FanUiState(Connection.READY, FanStatus(4, 40)),
                { actions += "plus" }, { actions += "minus" }, { actions += "power" },
                fanName = "HEADWIND")
        }
        compose.onNodeWithText("Connected · HEADWIND").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fan status").assertHasNoClickAction().performTouchInput {
            click(center)
            swipe(Offset(center.x, height * 0.9f), Offset(center.x, height * 0.1f), 300)
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        // Optional documentation capture: real UI with synthetic, non-identifying fan data.
        if (InstrumentationRegistry.getArguments().getString("captureReadme") == "true") {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "headwind-readme.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
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
