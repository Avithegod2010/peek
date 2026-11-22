package com.avi.peek.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avi.peek.data.repo.RequestBudgetState
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.components.SecureScreen
import com.avi.peek.ui.components.SimpleTopBar
import com.avi.peek.util.relativeTime
import kotlinx.coroutines.launch

@Composable
fun RateLimitScreen(onBack: () -> Unit) {
    // Request outcomes name profiles and endpoints; treat them as private.
    SecureScreen()
    val events by ServiceLocator.requestLog.events.collectAsStateWithLifecycle()
    val paused by ServiceLocator.downloadQueue.pausedAll.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val budget by ServiceLocator.requestBudget.state.collectAsStateWithLifecycle(
        initialValue = RequestBudgetState(),
    )
    val scope = rememberCoroutineScope()
    val rateLimited = events.count { it.rateLimited }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(
            title = "Rate limits",
            subtitle = "What Peek asked for, and when to pause",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("This session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${events.size} requests · $rateLimited rate-limit ${if (rateLimited == 1) "hit" else "hits"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (events.isEmpty()) "No public requests logged yet."
                            else "Last request ${(events.last().at / 1000L).relativeTime()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "budget") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Request budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${budget.usedThisHour} / ${settings?.requestBudgetPerHour ?: 120} requests this hour" +
                                if (budget.windowResetsAt > 0L) {
                                    " · resets ${(budget.windowResetsAt / 1000L).relativeTime()}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (budget.paused) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Paused until ${(budget.pauseUntil / 1000L).relativeTime()}" +
                                    (budget.lastError?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (budget.challengeUntil > System.currentTimeMillis()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Background requests paused after an Instagram challenge until " +
                                    "${(budget.challengeUntil / 1000L).relativeTime()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    scope.launch { ServiceLocator.requestBudget.clearPause() }
                                },
                            ) { Text("Resume now") }
                            TextButton(
                                onClick = {
                                    scope.launch { ServiceLocator.requestBudget.resetWindow() }
                                },
                            ) { Text("Reset hour") }
                            TextButton(
                                onClick = {
                                    val current = settings?.requestBudgetPerHour ?: 120
                                    val next = when (current) {
                                        60 -> 120
                                        120 -> 240
                                        240 -> 480
                                        else -> 60
                                    }
                                    scope.launch { ServiceLocator.settings.setRequestBudgetPerHour(next) }
                                },
                            ) {
                                Text("Limit ${settings?.requestBudgetPerHour ?: 120}")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Quiet hours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${settings?.quietStartHour ?: 23}:00 – ${settings?.quietEndHour ?: 7}:00 · background sync waits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings?.quietHoursEnabled == true,
                                onCheckedChange = { on ->
                                    scope.launch { ServiceLocator.settings.setQuietHours(on) }
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val start = settings?.quietStartHour ?: 23
                                    scope.launch { ServiceLocator.settings.setQuietStartHour((start + 1) % 24) }
                                },
                            ) { Text("Start ${settings?.quietStartHour ?: 23}:00") }
                            TextButton(
                                onClick = {
                                    val end = settings?.quietEndHour ?: 7
                                    scope.launch { ServiceLocator.settings.setQuietEndHour((end + 1) % 24) }
                                },
                            ) { Text("End ${settings?.quietEndHour ?: 7}:00") }
                        }
                    }
                }
            }

            item(key = "controls") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Pause all downloads", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Queued and running posts wait until you resume",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = paused,
                                onCheckedChange = {
                                    if (it) ServiceLocator.downloadQueue.pauseAll() else ServiceLocator.downloadQueue.resumeAll()
                                },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Background sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Turn off scheduled checks while Instagram is pushing back",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings?.backgroundSync == true,
                                onCheckedChange = { on ->
                                    scope.launch { ServiceLocator.settings.setBackgroundSync(on) }
                                },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { ServiceLocator.requestLog.clear() }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Clear log")
                        }
                    }
                }
            }

            if (events.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Profile reads, page loads, post details and downloads will show up here. Peek keeps the last 150 events on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(events.asReversed(), key = { "${it.at}-${it.label}-${it.outcome}" }) { event ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (event.rateLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    event.outcome,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (event.rateLimited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                (event.at / 1000L).relativeTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
