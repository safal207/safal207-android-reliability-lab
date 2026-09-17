package com.safal207.androidreliabilitylab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safal207.androidreliabilitylab.ui.IncidentListScreen
import com.safal207.androidreliabilitylab.ui.IncidentListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = IncidentListViewModel.factory(
            (application as IncidentApplication).incidentRepository,
            (application as IncidentApplication).incidentStatusMutator,
        )
        setContent {
            MaterialTheme {
                IncidentListScreen(viewModel(factory = factory))
            }
        }
    }
}
