package com.avi.peek

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.local.ThemeMode
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.ui.theme.PeekTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Measures real Compose layout and semantics on the JVM, so the 48dp touch-target
 * rule is enforced by a plain `./gradlew :app:testDebugUnitTest` instead of only by
 * an instrumented test on a device.
 *
 * Material3 lays an [androidx.compose.material3.IconButton] out at 40dp and only
 * widens its *parent box* to 48dp through `minimumInteractiveComponentSize`, so the
 * clickable node - the one TalkBack and `assertWidthIsAtLeast` see, and the one that
 * actually receives touches - stays 40dp unless the call site asks for 48dp itself.
 * That is why every icon button in this app carries an explicit `Modifier.size(48.dp)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class IconTouchTargetJvmTest {

    @Test
    fun topBarBackButtonIsA48DpTouchTarget() {
        val (root, density) = render {
            SimpleTopBar(title = "Saved", subtitle = "24 posts", onBack = {})
        }

        val back = root.descendantsAndSelf().filter { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Back")
        }
        assertEquals("the top bar must expose exactly one 'Back' control", 1, back.size)

        val button = back.single()
        val widthDp = button.size.width / density
        val heightDp = button.size.height / density
        assertTrue(
            "the top bar back button must be at least 48dp wide, was ${widthDp}dp",
            widthDp >= 48f,
        )
        assertTrue(
            "the top bar back button must be at least 48dp tall, was ${heightDp}dp",
            heightDp >= 48f,
        )
    }

    /**
     * The other 58 icon buttons live in screens that need a ViewModel and a network
     * cache, so they cannot be composed here. Their fix is a literal, so assert the
     * literal: every `IconButton(` call site must ask for a 48dp target itself.
     */
    @Test
    fun everyIconButtonAsksForA48DpTarget() {
        val source = listOf("src/main/java", "app/src/main/java")
            .map { java.io.File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("cannot find app/src/main/java from " + java.io.File(".").absolutePath)

        val offenders = source.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                callSites(text).mapNotNull { (line, args) ->
                    if ("48.dp" in args) null else file.name + ":" + line
                }.asSequence()
            }
            .toList()

        assertTrue(
            "IconButton call sites without an explicit 48dp touch target: $offenders",
            offenders.isEmpty(),
        )
    }

    /** Argument text of every `IconButton(...)` call, with the call's line number. */
    private fun callSites(text: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var from = 0
        while (true) {
            val start = text.indexOf("IconButton(", from)
            if (start < 0) return out
            var depth = 0
            var i = start + "IconButton(".length
            var inString = false
            while (i < text.length) {
                val c = text[i]
                when {
                    inString && c == '\\' -> i++
                    inString -> if (c == '"') inString = false
                    c == '"' -> inString = true
                    c == '(' || c == '[' || c == '{' -> depth++
                    c == ')' || c == ']' || c == '}' -> {
                        if (depth == 0) break
                        depth--
                    }
                }
                i++
            }
            out += (text.take(start).count { it == '\n' } + 1) to
                text.substring(start + "IconButton(".length, i)
            from = i
        }
    }

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit): Pair<SemanticsNode, Float> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            PeekTheme(settings = PeekSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
                content()
            }
        }
        val root = activity.window.decorView
        val density = root.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (640 * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val composeView = findComposeView(root) ?: error("no AndroidComposeView in the decor view")
        // AndroidComposeView is internal to Compose, so the public getter is reached
        // reflectively; it is the same semantics tree TalkBack consumes.
        val owner = composeView.javaClass.getMethod("getSemanticsOwner")
            .invoke(composeView) as SemanticsOwner
        return owner.rootSemanticsNode to density
    }

    private fun findComposeView(view: View): View? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposeView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun SemanticsNode.descendantsAndSelf(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.descendantsAndSelf() }
}
