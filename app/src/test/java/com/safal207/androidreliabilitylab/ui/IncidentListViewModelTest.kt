package com.safal207.androidreliabilitylab.ui

import com.safal207.androidreliabilitylab.data.FakeIncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentListViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading transitions to deterministic incident content`() = runTest(dispatcher) {
        val viewModel = IncidentListViewModel(FakeIncidentRepository())

        assertTrue(viewModel.uiState.value is IncidentUiState.Loading)

        advanceUntilIdle()

        val content = viewModel.uiState.value as IncidentUiState.Content
        assertEquals(3, content.incidents.size)
        assertEquals("INC-001", content.incidents[0].id)
        assertEquals("Payment retry", content.incidents[0].title)
        assertEquals("INC-003", content.incidents[2].id)
    }
}
