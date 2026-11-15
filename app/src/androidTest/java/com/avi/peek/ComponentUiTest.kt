package com.avi.peek

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.theme.PeekTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for reusable surface pieces. Requires a device/emulator
 * (`connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class ComponentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyStateShowsTitleMessageAndAction() {
        var clicked = false
        composeRule.setContent {
            PeekTheme(PeekSettings(dynamicColor = false)) {
                EmptyState(
                    title = "No private posts yet",
                    message = "Follow an account to fill this space.",
                    icon = Icons.Rounded.Lock,
                    actionLabel = "Find profiles",
                    onAction = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithText("No private posts yet").assertIsDisplayed()
        composeRule.onNodeWithText("Follow an account to fill this space.").assertIsDisplayed()
        composeRule.onNodeWithText("Find profiles").performClick()
        assertTrue(clicked)
    }

    @Test
    fun topBarBackButtonCallsBack() {
        var backed = false
        composeRule.setContent {
            PeekTheme(PeekSettings(dynamicColor = false)) {
                SimpleTopBar(title = "Security center", onBack = { backed = true })
            }
        }
        val back = composeRule.onNodeWithContentDescription("Back")
        back.assertWidthIsAtLeast(48.dp)
        back.assertHeightIsAtLeast(48.dp)
        back.performClick()
        assertTrue(backed)
    }

    @Test
    fun componentTreeCapturesToAnImage() {
        composeRule.setContent {
            PeekTheme(PeekSettings(dynamicColor = false)) {
                Box(Modifier.size(width = 360.dp, height = 240.dp)) {
                    EmptyState(
                        title = "Private vault",
                        message = "Sealed on this device.",
                        icon = Icons.Rounded.Lock,
                    )
                }
            }
        }
        val image = composeRule.onRoot().captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
    }

    @Test
    fun topBarRendersInRtl() {
        composeRule.setContent {
            PeekTheme(PeekSettings(dynamicColor = false)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SimpleTopBar(title = "مركز الأمان", subtitle = "الخصوصية", onBack = {})
                }
            }
        }
        composeRule.onNodeWithText("مركز الأمان").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun secureScreenAddsFlagSecure() {
        composeRule.setContent {
            PeekTheme(PeekSettings(dynamicColor = false)) {
                SecureScreen()
            }
        }
        composeRule.waitForIdle()
        val flags = composeRule.activity.window.attributes.flags
        assertTrue((flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
    }
}
