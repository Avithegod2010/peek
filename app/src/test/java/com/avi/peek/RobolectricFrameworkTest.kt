package com.avi.peek

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.avi.peek.util.NetworkMonitor
import com.avi.peek.work.PeekNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Proves the Android framework itself runs on the JVM under Robolectric: real
 * SharedPreferences storage, real resources, a real NotificationManager and a
 * real ConnectivityManager driving [NetworkMonitor]. No device or emulator
 * involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RobolectricFrameworkTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createsTheNewPostsNotificationChannel() {
        PeekNotifications.createChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = shadowOf(manager).notificationChannels.singleOrNull { it.id == "new_posts" }
            ?: throw AssertionError("the New posts channel was never registered")
        assertEquals("New posts", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun sharedPreferencesRoundTripThroughFrameworkStorage() {
        val name = "peek-robolectric-smoke"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("vault", "sealed").commit())

        val reopened = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertEquals("sealed", reopened.getString("vault", null))
    }

    @Test
    fun resourcesAreResolvedFromTheMergedResources() {
        assertEquals("Peek", context.getString(R.string.app_name))
        assertEquals("Search people", context.getString(R.string.shortcut_search))
    }

    @Test
    fun networkMonitorReadsConnectivityManagerCapabilities() {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        assertNotNull(manager)
        val network = manager.activeNetwork
        assertNotNull("Robolectric provides a default active network", network)

        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(manager).setNetworkCapabilities(network, caps)

        val monitor = NetworkMonitor(context)
        assertTrue(monitor.online.value)
        assertTrue(monitor.wifi.value)

        shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).removeTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        monitor.refresh()
        assertFalse(monitor.online.value)
        assertFalse(monitor.wifi.value)
    }
}
