package com.fiangg20.wavelength.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fiangg20.wavelength.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun restartApp(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        // The person can reopen the app by hand.
    }
    android.os.Process.killProcess(android.os.Process.myPid())
}

/** Save everything the app remembers to a file, and bring it back later, even on a new install. */
@Composable
fun BackupScreen(onBack: () -> Unit, bottomPad: Dp) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val text = Backup.export(context)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                busy = false
                Toast.makeText(context, if (ok) "Backup saved" else "Could not save the backup", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) }
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                if (text == null || !text.trimStart().startsWith("{")) {
                    Toast.makeText(context, "That is not a Wavelength backup", Toast.LENGTH_LONG).show()
                } else {
                    pendingRestore = text
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Backup & restore", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "A backup keeps your settings, playlists, favourites, play history, edited song info, " +
                    "equalizer, downloader settings and saved lyrics in one small file. Your songs are not included.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
            BigAction(
                title = "Save a backup",
                subtitle = "Choose where to put the file, such as Downloads or Drive",
                icon = Icons.Rounded.Download,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
                enabled = !busy,
            ) {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                exportLauncher.launch("wavelength-backup-$stamp.json")
            }
            BigAction(
                title = "Restore from a backup",
                subtitle = "Replaces what is on this phone with the file's contents",
                icon = Icons.Rounded.Restore,
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
                enabled = !busy,
            ) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            }
            Text(
                "Tip: save a backup before you uninstall Wavelength or change phones. " +
                    "Playlists point at songs by their place in the phone's library, so they match best on the same phone.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }

    val text = pendingRestore
    if (text != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text("Your current settings, playlists and history will be replaced. Wavelength closes and you open it again to finish.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Backup.restore(context, text) }
                        if (result == null) {
                            Toast.makeText(context, "That is not a Wavelength backup", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Restored, restarting", Toast.LENGTH_SHORT).show()
                            restartApp(context)
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BigAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = content)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
        }
    }
}
