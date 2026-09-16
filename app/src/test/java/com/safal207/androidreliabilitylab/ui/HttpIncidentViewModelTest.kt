package com.safal207.androidreliabilitylab.ui

import com.safal207.androidreliabilitylab.data.HttpIncidentRepository
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HttpIncidentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `HTTP 200 maps every field and transitions Loading to Content`() = runTest(dispatcher) {
        val fixture = requireNotNull(javaClass.getResource("/incidents.json")).readText()
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(fixture))
        val viewModel = IncidentListViewModel(HttpIncidentRepository.create(server.url("/").toString()))
        val states = mutableListOf<IncidentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        // Real HTTP runs outside the virtual-time scheduler; bound the real wait.
        val result = withContext(Dispatchers.Default) {
            withTimeout(5_000) { viewModel.uiState.first { it !is IncidentUiState.Loading } }
        }
        val expected = IncidentUiState.Content(listOf(
            Incident("INC-API-001", "HTTP boundary received", IncidentStatus.OPEN),
            Incident("INC-API-002", "DTO mapping checked", IncidentStatus.INVESTIGATING),
            Incident("INC-API-003", "API content rendered", IncidentStatus.RESOLVED),
        ))
        assertEquals(expected, result)
        assertEquals(listOf(IncidentUiState.Loading, expected), states)
        assertSingleGet()
    }

    @Test
    fun `HTTP 500 transitions Loading to renderable Error without fake fallback`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal fixture failure"))
        val viewModel = IncidentListViewModel(HttpIncidentRepository.create(server.url("/").toString()))
        val states = mutableListOf<IncidentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        val result = withContext(Dispatchers.Default) {
            withTimeout(5_000) { viewModel.uiState.first { it !is IncidentUiState.Loading } }
        }
        val expected = IncidentUiState.Error("Unable to load incidents.")
        assertEquals(expected, result)
        assertEquals(listOf(IncidentUiState.Loading, expected), states)
        assertSingleGet()
    }

    private fun assertSingleGet() {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/incidents", request.path)
        assertEquals(1, server.requestCount)
    }
}
