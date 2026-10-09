package com.ynks.burndown.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ynks.burndown.data.settings.AppSettings
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.widget.WidgetData

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BurndownApp(
    form: FormState,
    settings: AppSettings?,
    dashboard: WidgetData?,
    syncing: Boolean,
    onEdit: ((FormState) -> FormState) -> Unit,
    onLoadProject: () -> Unit,
    onSave: (onDone: () -> Unit) -> Unit,
    onResolution: (Resolution) -> Unit,
    onSync: () -> Unit,
    // only set by tests
    initialSettingsOpen: Boolean? = null,
) {
    var showSettings by rememberSaveable { mutableStateOf(initialSettingsOpen) }
    val configured = settings?.isConfigured
    val onSettings = showSettings ?: (configured == false)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (onSettings) "Settings" else settings?.projectTitle?.ifBlank { null } ?: "Burndown",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    if (onSettings && configured == true) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!onSettings) {
                        if (configured == true) {
                            IconButton(onClick = onSync) {
                                if (syncing) LoadingIndicator(Modifier.size(24.dp))
                                else Icon(Icons.Rounded.Refresh, contentDescription = "Sync now")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (onSettings) {
            SettingsScreen(
                form = form,
                onEdit = onEdit,
                onLoadProject = onLoadProject,
                onSave = { onSave { showSettings = false } },
                onResolution = onResolution,
                contentPadding = padding,
            )
        } else {
            DashboardScreen(
                data = dashboard,
                resolution = settings?.resolution ?: form.resolution,
                onResolution = onResolution,
                onOpenSettings = { showSettings = true },
                contentPadding = padding,
            )
        }
    }
}
