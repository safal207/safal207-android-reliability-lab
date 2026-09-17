package com.safal207.androidreliabilitylab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus

@Composable
fun IncidentListScreen(
    viewModel: IncidentListViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val mutationState by viewModel.mutationState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Android Reliability Lab",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            when (val state = uiState) {
                IncidentUiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                is IncidentUiState.Content -> IncidentList(
                    state.incidents, mutationState, viewModel::resolveDemoIncident, viewModel::retryPendingIncident,
                )
                is IncidentUiState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun IncidentList(
    incidents: List<Incident>,
    mutationState: IncidentMutationUiState,
    onResolve: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(incidents, key = { it.id }) { incident ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = incident.id,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = incident.title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = incident.status.name,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (incident.id == IncidentListViewModel.DEMO_INCIDENT_ID) {
                    val pending = mutationState is IncidentMutationUiState.Pending || mutationState is IncidentMutationUiState.RetryError
                    Button(
                        onClick = if (pending) onRetry else onResolve,
                        enabled = pending || (mutationState == IncidentMutationUiState.Idle && incident.status == IncidentStatus.OPEN),
                    ) { Text(if (pending) "Retry pending change" else "Resolve incident") }
                    when (mutationState) {
                        IncidentMutationUiState.Idle -> Unit
                        IncidentMutationUiState.Sending -> Text("Sending change…")
                        IncidentMutationUiState.ServerConfirmed -> Text("Resolved on server")
                        is IncidentMutationUiState.Pending -> Text("Pending synchronization")
                        is IncidentMutationUiState.RetryError -> Text(
                            "Change is still pending. Confirmation failed.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        IncidentMutationUiState.Error -> Text(
                            "Unable to change incident status.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
