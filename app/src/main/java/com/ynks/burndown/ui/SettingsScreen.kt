package com.ynks.burndown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.Resolution

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    form: FormState,
    onEdit: ((FormState) -> FormState) -> Unit,
    onLoadProject: () -> Unit,
    onSave: () -> Unit,
    onResolution: (Resolution) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Section("GitHub access", "A personal access token with the read:project scope (plus repo for private repositories).") {
            OutlinedTextField(
                value = form.token,
                onValueChange = { v -> onEdit { it.copy(token = v) } },
                label = { Text(if (form.hasStoredToken) "Token (saved, leave blank to keep)" else "Personal access token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Project", "The organization or user that owns the project, and its number from the project URL.") {
            OutlinedTextField(
                value = form.owner,
                onValueChange = { v -> onEdit { it.copy(owner = v, project = null) } },
                label = { Text("Owner (org or user)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.projectNumber,
                onValueChange = { v -> onEdit { it.copy(projectNumber = v.filter(Char::isDigit), project = null) } },
                label = { Text("Project number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onLoadProject,
                    enabled = form.canLoadProject && !form.loadingProject,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (form.project == null) "Load project" else "Reload")
                }
                if (form.loadingProject) LoadingIndicator(Modifier.size(40.dp))
                form.project?.let { Text(it.title, style = MaterialTheme.typography.titleMediumEmphasized) }
            }
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        form.project?.let { project ->
            Section("Sprint", "The iteration field that defines your sprints. The sprint containing today is charted.") {
                Dropdown("Iteration field", form.iterationField, project.iterationFields) { v ->
                    onEdit { it.copy(iterationField = v) }
                }
                if (project.iterationFields.isEmpty()) {
                    Text("This project has no iteration field.", color = MaterialTheme.colorScheme.error)
                }
            }

            Section("Measure", "Chart the number of issues, or the sum of a number field such as story points.") {
                ChoiceGroup(
                    options = listOf(Metric.COUNT to "Issue count", Metric.POINTS to "Story points"),
                    selected = form.metric,
                    onSelect = { m -> onEdit { it.copy(metric = m) } },
                )
                if (form.metric == Metric.POINTS) {
                    Dropdown("Number field", form.pointsField, project.numberFields) { v ->
                        onEdit { it.copy(pointsField = v) }
                    }
                    if (project.numberFields.isEmpty()) {
                        Text("This project has no number field.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Section("Chart detail", "One data point per day, or per hour for a smoother curve.") {
            ChoiceGroup(
                options = listOf(Resolution.DAILY to "Per day", Resolution.HOURLY to "Per hour"),
                selected = form.resolution,
                onSelect = { r -> onResolution(r) },
            )
        }

        Button(
            onClick = onSave,
            enabled = form.canSave,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Text("Save and sync", style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

@Composable
private fun Section(title: String, hint: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
