package com.safal207.androidreliabilitylab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safal207.androidreliabilitylab.data.IncidentRepository
import com.safal207.androidreliabilitylab.data.IncidentStatusMutator
import com.safal207.androidreliabilitylab.data.MutationResult
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.logging.Level
import java.util.logging.Logger

sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data class Content(val incidents: List<Incident>) : IncidentUiState
    data class Error(val message: String) : IncidentUiState
}

sealed interface IncidentMutationUiState {
    data object Idle : IncidentMutationUiState
    data object Sending : IncidentMutationUiState
    data object ServerConfirmed : IncidentMutationUiState
    data class Pending(val mutationId: String) : IncidentMutationUiState
    data object Error : IncidentMutationUiState
}

class IncidentListViewModel(
    private val repository: IncidentRepository,
    private val mutations: IncidentStatusMutator? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<IncidentUiState>(IncidentUiState.Loading)
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()
    private val _mutationState = MutableStateFlow<IncidentMutationUiState>(IncidentMutationUiState.Idle)
    val mutationState: StateFlow<IncidentMutationUiState> = _mutationState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                IncidentUiState.Content(repository.getIncidents())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.getLogger(IncidentListViewModel::class.java.name)
                    .log(Level.WARNING, "Incident fetch failed", failure)
                IncidentUiState.Error("Unable to load incidents.")
            }
        }
    }

    fun resolveDemoIncident() {
        val mutator = mutations ?: return
        val loaded = _uiState.value as? IncidentUiState.Content ?: return
        if (_mutationState.value != IncidentMutationUiState.Idle) return
        if (loaded.incidents.none { it.id == DEMO_INCIDENT_ID && it.status == IncidentStatus.OPEN }) return
        _mutationState.value = IncidentMutationUiState.Sending
        viewModelScope.launch {
            try {
                val result = mutator.changeStatus(DEMO_INCIDENT_ID, IncidentStatus.RESOLVED)
                _uiState.value = IncidentUiState.Content(loaded.incidents.map {
                    if (it.id == DEMO_INCIDENT_ID) it.copy(status = IncidentStatus.RESOLVED) else it
                })
                _mutationState.value = when (result) {
                    MutationResult.ServerConfirmed -> IncidentMutationUiState.ServerConfirmed
                    is MutationResult.Pending -> IncidentMutationUiState.Pending(result.mutationId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.getLogger(IncidentListViewModel::class.java.name)
                    .log(Level.WARNING, "Incident mutation failed", failure)
                _mutationState.value = IncidentMutationUiState.Error
            }
        }
    }

    companion object {
        const val DEMO_INCIDENT_ID = "INC-API-001"

        fun factory(
            repository: IncidentRepository,
            mutations: IncidentStatusMutator? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { IncidentListViewModel(repository, mutations) }
        }
    }
}
