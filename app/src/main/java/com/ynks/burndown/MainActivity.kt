package com.ynks.burndown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ynks.burndown.ui.BurndownApp
import com.ynks.burndown.ui.BurndownTheme
import com.ynks.burndown.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BurndownTheme {
                val form by viewModel.form.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
                val syncing by viewModel.syncing.collectAsStateWithLifecycle()
                BurndownApp(
                    form = form,
                    settings = settings,
                    dashboard = dashboard,
                    syncing = syncing,
                    onEdit = viewModel::edit,
                    onLoadProject = { viewModel.loadProject() },
                    onSave = viewModel::save,
                    onResolution = viewModel::setResolution,
                    onSync = viewModel::syncNow,
                )
            }
        }
    }
}
