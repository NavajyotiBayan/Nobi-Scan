package com.nobi.scan.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nobi.scan.ui.components.rememberNobiHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

internal class UpdateControls(private val context: Context, private val scope: CoroutineScope) {
    private val client = GitHubUpdates(context)
    private val preferences = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
    private var operation: Job? = null
    var visible by mutableStateOf(false)
        private set
    var available by mutableStateOf<GitHubUpdate?>(null)
        private set
    var progress by mutableStateOf<Float?>(null)
        private set
    var status by mutableStateOf("Check GitHub for a newer release.")
        private set
    var busy by mutableStateOf(false)
        private set
    var downloaded by mutableStateOf<File?>(null)
        private set

    fun check(manual: Boolean) {
        if (manual) visible = true
        if (busy) return
        if (manual && available != null) return
        val now = System.currentTimeMillis()
        val last = preferences.getLong("checked_at", 0)
        if (!manual && now - last in 0 until 86_400_000L) return
        preferences.edit { putLong("checked_at", now) }
        busy = true
        status = "Checking GitHub…"
        operation = scope.launch {
            try {
                val candidate = client.check()
                if (candidate != available) downloaded = null
                available = candidate
                status = if (available == null) "You’re up to date." else "Update available"
                if (!manual && shouldOfferUpdate(candidate?.versionCode, preferences.getLong("offered_code", 0))) {
                    visible = true
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: UpdateUnavailable) {
                status = exception.message.orEmpty()
            } catch (_: Exception) {
                status = "Could not check for updates. Try again later."
            } finally {
                busy = false
            }
        }
    }

    fun download(onReady: () -> Unit) {
        val update = available ?: return
        if (busy) return
        downloaded = null
        busy = true
        progress = 0f
        status = "Downloading update…"
        operation = scope.launch {
            try {
                downloaded = client.download(update) { progress = it }
                status = "Download verified. Ready to install."
                onReady()
            } catch (exception: CancellationException) {
                status = "Download cancelled."
                throw exception
            } catch (_: Exception) {
                status = "Could not verify or download the update."
            } finally {
                progress = null
                busy = false
            }
        }
    }

    fun install(activity: Context) {
        val apk = downloaded?.takeIf { it.isFile }
        if (apk == null) {
            downloaded = null
            status = "The cached APK is gone. Download the update again."
            return
        }
        try {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                activity.startActivity(settings)
                status = "After allowing installs from Nobi Scan, return and tap Install."
                return
            }
            val uri = FileProvider.getUriForFile(activity, "${context.packageName}.documents", apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            status = "Android’s package installer is unavailable."
        } catch (_: SecurityException) {
            status = "Android blocked installation from this source."
        }
    }

    fun markOffered(code: Long) {
        if (code > preferences.getLong("offered_code", 0)) preferences.edit { putLong("offered_code", code) }
    }

    fun close() {
        visible = false
        if (progress != null) operation?.cancel()
    }
}

internal fun shouldOfferUpdate(versionCode: Long?, offeredCode: Long): Boolean =
    versionCode != null && versionCode > offeredCode

@Composable
internal fun rememberUpdateControls(): UpdateControls {
    val owner = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controls = remember(context, scope) { UpdateControls(context, scope) }
    DisposableEffect(owner, controls) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) controls.check(manual = false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return controls
}

@Composable
internal fun UpdateDialog(controls: UpdateControls) {
    if (!controls.visible) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberNobiHaptics()
    val update = controls.available
    LaunchedEffect(update?.versionCode) {
        update?.let { controls.markOffered(it.versionCode) }
    }
    AlertDialog(
        onDismissRequest = haptics.onTap(controls::close),
        title = { Text(if (update == null) "App updates" else "Nobi Scan ${update.tag.removePrefix("v")} available") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(controls.status)
                if (update != null) {
                    Text("Download · ${Formatter.formatFileSize(context, update.bytes)}")
                    Text("What’s new", style = MaterialTheme.typography.titleSmall)
                    Text(update.notes.ifBlank { "This release has no changelog." })
                }
                controls.progress?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        dismissButton = {
            TextButton(onClick = haptics.onTap(controls::close)) {
                Text(when {
                    controls.progress != null -> "Cancel download"
                    update != null -> "Later"
                    else -> "Close"
                })
            }
        },
        confirmButton = {
            val ready = controls.downloaded != null
            TextButton(
                enabled = !controls.busy,
                onClick = haptics.onTap {
                    when {
                        ready -> controls.install(context)
                        update != null -> controls.download {
                            haptics.confirm()
                            controls.install(context)
                        }
                        else -> controls.check(manual = true)
                    }
                }
            ) { Text(if (ready || update != null) "Install now" else "Check again") }
        }
    )
}
