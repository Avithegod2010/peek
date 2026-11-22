package com.avi.peek

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.ThemeMode
import com.avi.peek.ui.components.AddPeopleHint
import com.avi.peek.ui.components.EmptyState
import com.avi.peek.ui.components.SectionHeader
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.theme.PeekTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden-image tests for the shared UI: the real composables are rendered by
 * Robolectric's native graphics stack at a fixed 360x640dp phone window, then
 * compared pixel-for-pixel with the PNGs committed in `app/src/test/screenshots`.
 *
 * The committed PNGs are recorded on the Linux CI runner and that environment is
 * the source of truth. They are skipped everywhere else (see [screenshot]).
 *
 * Recording is opt-in (`-Proborazzi.test.record=true`); a plain
 * `./gradlew :app:testDebugUnitTest` verifies. Nothing here needs a device, a
 * clock, or an animation frame.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ScreenshotBaselineTest {

    @Test
    fun topBarWithBackButtonAndSubtitle() {
        screenshot("simple-top-bar-light") {
            Frame(dark = false) {
                SimpleTopBar(
                    title = "Saved",
                    subtitle = "24 posts · synced 2h ago",
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun emptyStateWithAction() {
        screenshot("empty-state-light") {
            Frame(dark = false) {
                EmptyState(
                    title = "Nothing saved yet",
                    message = "Save a post from your feed and it lands here, offline and searchable.",
                    icon = Icons.Rounded.BookmarkBorder,
                    modifier = Modifier.weight(1f),
                    actionLabel = "Browse your feed",
                    onAction = {},
                )
            }
        }
    }

    @Test
    fun settingsSection() {
        screenshot("settings-section-light") {
            SettingsSection()
        }
    }

    @Test
    fun emptyStateWithActionInDarkTheme() {
        screenshot("empty-state-dark") {
            Frame(dark = true) {
                EmptyState(
                    title = "Nothing saved yet",
                    message = "Save a post from your feed and it lands here, offline and searchable.",
                    icon = Icons.Rounded.BookmarkBorder,
                    modifier = Modifier.weight(1f),
                    actionLabel = "Browse your feed",
                    onAction = {},
                )
            }
        }
    }

    @Test
    fun settingsSectionInDarkTheme() {
        screenshot("settings-section-dark") {
            SettingsSection(dark = true)
        }
    }

    /**
     * Settings surfaces are built from these two shared pieces: a section
     * header (titleMedium title plus the primary-coloured action) and the
     * rounded `surfaceContainer` info row. Rendering them together pins the
     * theme's surface containers and type scale.
     */
    @Composable
    private fun SettingsSection(dark: Boolean = false) {
        Frame(dark = dark) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionHeader(title = "Sync & storage", action = "Manage", onAction = {})
                AddPeopleHint(Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun Frame(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
        PeekTheme(
            settings = PeekSettings(
                themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                // Robolectric has no wallpaper palette, so dynamic colour is
                // pinned off; the golden covers Peek's own palettes.
                dynamicColor = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = { Column(Modifier.fillMaxSize(), content = content) },
            )
        }
    }

    private fun screenshot(name: String, content: @Composable () -> Unit) {
        // The committed goldens were recorded on the Linux CI runner. Windows and
        // Linux rasterise the same composable to different pixels (font metrics and
        // Skia rasterisation differ), so a local Windows run can only produce a
        // false mismatch. Skip instead - CI verifies and records these.
        assumeTrue(
            "Screenshot baselines are recorded and verified on Linux CI; " +
                "Windows and Linux rasterise differently",
            System.getenv("CI") != null,
        )
        // Roborazzi no-ops when no task type is set, which would let these
        // tests pass without touching an image at all.
        assertTrue(
            "Screenshot capture is disabled: run with -Proborazzi.test.record=true to " +
                "record baselines, or without it to verify them",
            roborazziSystemPropertyTaskType().isEnabled(),
        )
        captureRoboImage("src/test/screenshots/$name.png") { content() }
    }
}
