package com.safal207.androidreliabilitylab.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safal207.androidreliabilitylab.data.IncidentRepository
import com.safal207.androidreliabilitylab.domain.Incident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data class Content(val incidents: List<Incident>) : IncidentUiState
    data class Error(val message: String) : IncidentUiState
}

class IncidentListViewModel(
    private val repository: IncidentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<IncidentUiState>(IncidentUiState.Loading)
    val uiState: StateFlow<IncidentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                IncidentUiState.Content(repository.getIncidents())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                IncidentUiState.Error("Unable to load incidents.")
            }
        }
    }

    companion object {
        fun factory(repository: IncidentRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { IncidentListViewModel(repository) }
        }
    }
}
