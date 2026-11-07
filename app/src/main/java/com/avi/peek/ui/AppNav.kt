package com.avi.peek.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.avi.peek.data.directory.PackTransfer
import com.avi.peek.data.local.PeekSettings
import com.avi.peek.data.repo.SyncEvent
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.LocalReduceMotion
import com.avi.peek.ui.components.LocalSnackbarHostState
import com.avi.peek.ui.screens.AudioScreen
import com.avi.peek.ui.screens.ExploreScreen
import com.avi.peek.ui.screens.DownloadsScreen
import com.avi.peek.ui.screens.FollowingScreen
import com.avi.peek.ui.screens.HomeScreen
import com.avi.peek.ui.screens.LibraryScreen
import com.avi.peek.ui.screens.OnboardingScreen
import com.avi.peek.ui.screens.PostScreen
import com.avi.peek.ui.screens.ProfileScreen
import com.avi.peek.ui.screens.ReelsScreen
import com.avi.peek.ui.screens.LikedScreen
import com.avi.peek.ui.screens.PackScreen
import com.avi.peek.ui.screens.RecapScreen
import com.avi.peek.ui.screens.SavedScreen
import com.avi.peek.ui.screens.SearchScreen
import com.avi.peek.ui.screens.SettingsScreen
import com.avi.peek.ui.theme.PeekTheme
import com.avi.peek.util.DeepLinkBus
import com.avi.peek.util.LocalPeekHaptics
import com.avi.peek.util.PeekHaptics
import kotlinx.coroutines.launch

private data class TopTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topTabs = listOf(
    TopTab("home", "Home", Icons.Rounded.Home),
    TopTab("reels", "Reels", Icons.Rounded.Movie),
    TopTab("discover", "Explore", Icons.Rounded.Explore),
    TopTab("library", "Library", Icons.Rounded.CollectionsBookmark),
)

@Composable
fun PeekRoot(settings: PeekSettings) {
    val context = LocalContext.current
    val haptics = remember(settings.haptics) { PeekHaptics(context, settings.haptics) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var startDestination by rememberSaveable { mutableStateOf("home") }

    PeekTheme(settings) {
        CompositionLocalProvider(
            LocalPeekHaptics provides haptics,
            LocalReduceMotion provides settings.reduceMotion,
            LocalSnackbarHostState provides snackbar,
        ) {
            if (!settings.onboarded) {
                OnboardingScreen(
                    onFinish = { destination ->
                        startDestination = destination
                        scope.launch { ServiceLocator.settings.setOnboarded(true) }
                    },
                )
            } else {
                MainScaffold(
                    settings = settings,
                    startDestination = startDestination,
                    snackbar = snackbar,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScaffold(
    settings: PeekSettings,
    startDestination: String,
    snackbar: SnackbarHostState,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val windowSizeClass = (context as? Activity)?.let { calculateWindowSizeClass(it) }
    val useRail = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = topTabs.any { it.route == currentRoute }
    val link by DeepLinkBus.link.collectAsStateWithLifecycle()
    val syncState by ServiceLocator.repository.syncState.collectAsStateWithLifecycle()
    val online by ServiceLocator.network.online.collectAsStateWithLifecycle()
    val following by ServiceLocator.repository.following.collectAsStateWithLifecycle(emptyList())
    val downloadQueue by ServiceLocator.downloadQueue.tasks.collectAsStateWithLifecycle(emptyList())
    val unreadCount = following.count {
        (it.latestReelMedia ?: 0L) > ((it.lastViewedAt ?: 0L) / 1000L)
    }
    val activeDownloads = downloadQueue.count { it.status != com.avi.peek.data.repo.DownloadStatus.DONE }
    val scope = rememberCoroutineScope()

    fun navigateTab(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openProfile(username: String) {
        navController.navigate("profile/$username")
    }

    fun openPost(code: String) {
        navController.navigate("post/$code")
    }

    fun openSearch(query: String = "") {
        val route = if (query.isBlank()) "search" else "search?q=${Uri.encode(query)}"
        navController.navigate(route)
    }

    fun openPack(id: String) {
        navController.navigate("pack/$id")
    }

    LaunchedEffect(Unit) {
        if (settings.syncOnOpen) {
            ServiceLocator.repository.syncAll(force = false)
        }
    }

    LaunchedEffect(Unit) {
        ServiceLocator.repository.syncEvents.collect { event ->
            when (event) {
                is SyncEvent.Completed -> {
                    if (event.newPosts > 0) {
                        snackbar.showSnackbar("${event.newPosts} new ${if (event.newPosts == 1) "post" else "posts"} cached")
                    } else if (event.errors > 0) {
                        snackbar.showSnackbar("Circle refreshed with ${event.errors} hiccups")
                    }
                }
                is SyncEvent.Failed -> snackbar.showSnackbar(event.message)
                SyncEvent.NoConnection -> snackbar.showSnackbar("You're offline. Showing what's cached.")
            }
        }
    }

    LaunchedEffect(link, settings.onboarded) {
        val value = link ?: return@LaunchedEffect
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        if (uri != null) {
            when (uri.host) {
                "profile" -> uri.pathSegments.firstOrNull()?.let { openProfile(it) }
                "post" -> uri.pathSegments.firstOrNull()?.let { openPost(it) }
                "home" -> navigateTab("home")
                "search" -> openSearch()
                "saved" -> navController.navigate("saved")
                "downloads" -> navController.navigate("downloads")
                "share" -> uri.getQueryParameter("text")?.let { shared ->
                    openSearch(shared)
                }
                "pack" -> uri.getQueryParameter("usernames")?.let { raw ->
                    val names = raw.split(',').map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }
                    if (names.isNotEmpty()) {
                        PackTransfer.title = "Shared pack"
                        PackTransfer.usernames = names
                        openPack("imported")
                    }
                }
            }
        }
        DeepLinkBus.consume()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (settings.oneHanded && currentRoute == "home") {
                ExtendedFloatingActionButton(
                    onClick = { openSearch() },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    text = { Text("Search people") },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = showBar && !useRail, enter = fadeIn(), exit = fadeOut()) {
                PeekBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { navigateTab(it) },
                    badges = mapOf("home" to unreadCount, "library" to activeDownloads),
                )
            }
        },
    ) { innerPadding ->
        Row(Modifier.padding(innerPadding).fillMaxSize()) {
        if (showBar && useRail) {
            PeekNavigationRail(
                currentRoute = currentRoute,
                onSelect = { navigateTab(it) },
                badges = mapOf("home" to unreadCount, "library" to activeDownloads),
            )
        }
        SharedTransitionLayout(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val sharedScope = this
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it / 12 }, animationSpec = tween(280)) +
                        fadeIn(animationSpec = tween(220))
                },
                exitTransition = { fadeOut(animationSpec = tween(160)) },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 12 }, animationSpec = tween(280)) +
                        fadeIn(animationSpec = tween(220))
                },
                popExitTransition = { fadeOut(animationSpec = tween(160)) },
            ) {
            composable("home") {
                val visibilityScope = this
                val avatarModifiers = following.take(24).associate { profile ->
                    profile.username to with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "avatar_${profile.username}"),
                            animatedVisibilityScope = visibilityScope,
                        )
                    }
                }
                HomeScreen(
                    settings = settings,
                    online = online,
                    syncState = syncState,
                    onOpenPost = { openPost(it) },
                    onOpenProfile = { openProfile(it) },
                    onOpenDiscover = { navigateTab("discover") },
                    onOpenSearch = { openSearch() },
                    storyAvatarModifier = { username -> avatarModifiers[username] ?: Modifier },
                )
            }
            composable("reels") {
                ReelsScreen(onOpenProfile = { openProfile(it) })
            }
            composable("discover") {
                ExploreScreen(
                    onOpenSearch = { openSearch() },
                    onOpenProfile = { openProfile(it) },
                    onOpenFollowing = { navController.navigate("following") },
                    onOpenPack = { openPack(it) },
                )
            }
            composable(
                route = "search?q={q}",
                arguments = listOf(
                    navArgument("q") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val initialQuery = entry.arguments?.getString("q").orEmpty()
                SearchScreen(
                    initialQuery = initialQuery,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { openProfile(it) },
                    onOpenPost = { openPost(it) },
                )
            }
            composable("library") {
                LibraryScreen(
                    settings = settings,
                    onOpenFollowing = { navController.navigate("following") },
                    onOpenSaved = { navController.navigate("saved") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenLiked = { navController.navigate("liked") },
                    onOpenRecap = { navController.navigate("recap") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenPost = { openPost(it) },
                    onOpenDiscover = { navigateTab("discover") },
                )
            }
            composable("profile/{username}") { entry ->
                val username = entry.arguments?.getString("username").orEmpty()
                val visibilityScope = this
                ProfileScreen(
                    username = username,
                    onBack = { navController.popBackStack() },
                    onOpenPost = { openPost(it) },
                    onOpenProfile = { openProfile(it) },
                    headerAvatarModifier = with(sharedScope) {
                        Modifier.sharedElement(
                            rememberSharedContentState(key = "avatar_$username"),
                            animatedVisibilityScope = visibilityScope,
                        )
                    },
                )
            }
            composable("post/{code}") { entry ->
                val code = entry.arguments?.getString("code").orEmpty()
                PostScreen(
                    code = code,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { openProfile(it) },
                    onOpenAudio = { title -> navController.navigate("audio?title=${Uri.encode(title)}") },
                )
            }
            composable(
                route = "audio?title={title}",
                arguments = listOf(
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                AudioScreen(
                    title = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenPost = { openPost(it) },
                )
            }
            composable("pack/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                PackScreen(
                    packId = id,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { openProfile(it) },
                )
            }
            composable("liked") {
                LikedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPost = { openPost(it) },
                )
            }
            composable("recap") {
                RecapScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { openProfile(it) },
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("saved") {
                SavedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPost = { openPost(it) },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPost = { openPost(it) },
                )
            }
            composable("following") {
                FollowingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { openProfile(it) },
                    onOpenDiscover = { navigateTab("discover") },
                )
            }
            }
        }
        }
    }
}

@Composable
private fun PeekNavigationRail(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    badges: Map<String, Int> = emptyMap(),
) {
    val haptics = LocalPeekHaptics.current
    NavigationRail(
        modifier = Modifier.fillMaxHeight().navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Text(
                "Peek",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        },
    ) {
        Spacer(Modifier.weight(0.15f))
        topTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationRailItem(
                selected = selected,
                onClick = {
                    haptics.tap()
                    onSelect(tab.route)
                },
                icon = {
                    Box {
                        Icon(tab.icon, contentDescription = tab.label)
                        val badge = badges[tab.route] ?: 0
                        if (badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (badge > 9) "9+" else badge.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PeekBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    badges: Map<String, Int> = emptyMap(),
) {
    val haptics = LocalPeekHaptics.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 74.dp)
                .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val interaction = remember { MutableInteractionSource() }
                val pillColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabPill",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
                    label = "tabScale",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptics.tap()
                            onSelect(tab.route)
                        }
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                            val count = badges[tab.route] ?: 0
                            if (count > 0) {
                                stateDescription = if (tab.route == "library") {
                                    "$count active downloads"
                                } else {
                                    "$count unread"
                                }
                            }
                        }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(pillColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        val badge = badges[tab.route] ?: 0
                        if (badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(15.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (badge > 9) "9+" else badge.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
