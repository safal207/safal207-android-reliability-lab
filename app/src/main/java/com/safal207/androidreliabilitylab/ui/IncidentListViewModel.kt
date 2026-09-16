package com.safal207.androidreliabilitylab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safal207.androidreliabilitylab.data.FakeIncidentRepository
import com.safal207.androidreliabilitylab.data.IncidentRepository
import com.safal207.androidreliabilitylab.domain.Incident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data class Content(val incidents: List<Incident>) : IncidentUiState
}

class IncidentListViewModel(
    private val repository: IncidentRepository = FakeIncidentRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<IncidentUiState>(IncidentUiState.Loading)
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = IncidentUiState.Content(repository.getIncidents())
        }
    }
}
