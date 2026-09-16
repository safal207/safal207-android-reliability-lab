package com.safal207.androidreliabilitylab.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safal207.androidreliabilitylab.data.local.IncidentDatabase
import com.safal207.androidreliabilitylab.data.local.toEntity
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import com.safal207.androidreliabilitylab.ui.IncidentListViewModel
import com.safal207.androidreliabilitylab.ui.IncidentUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPersistenceTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = MockWebServer()
    private lateinit var context: Context
    private lateinit var database: IncidentDatabase
    private lateinit var databaseName: String
    private lateinit var repository: PersistentIncidentRepository
    private lateinit var fixture: String

    private val expected = listOf(
        Incident("INC-API-001", "HTTP boundary received", IncidentStatus.OPEN),
        Incident("INC-API-002", "DTO mapping checked", IncidentStatus.INVESTIGATING),
        Incident("INC-API-003", "API content rendered", IncidentStatus.RESOLVED),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        fixture = instrumentation.context.assets.open("incidents.json").bufferedReader().use { it.readText() }
        databaseName = "room-bead-003-${UUID.randomUUID()}.db"
        database = IncidentDatabase.open(context, databaseName)

        // Test-local HTTPS leaves the app's existing cleartext host policy unchanged.
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        repository = PersistentIncidentRepository(
            HttpIncidentRepository.create(server.url("/").toString(), client),
            database.incidentDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun http200PersistsAllFieldsBeforeContent() = runTest(dispatcher) {
        enqueueSuccess(fixture)
        val viewModel = IncidentListViewModel(repository)
        val states = mutableListOf<IncidentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }
        val result = awaitResult(viewModel)

        assertEquals(IncidentUiState.Content(expected), result)
        assertEquals(listOf(IncidentUiState.Loading, result), states)
        assertEquals(expected, readRows())
        assertEquals(3, database.incidentDao().readAll().size)
        assertRequests(1)
    }

    @Test
    fun fileBackedDataSurvivesCloseAndFreshReopen() = runBlocking {
        enqueueSuccess(fixture)
        assertEquals(expected, repository.getIncidents())
        val file = context.getDatabasePath(databaseName)
        assertTrue(file.isFile)
        val previous = database
        previous.close()
        assertFalse(previous.isOpen)

        database = IncidentDatabase.open(context, databaseName)
        assertNotSame(previous, database)
        assertEquals(file.canonicalPath, context.getDatabasePath(databaseName).canonicalPath)
        assertEquals(expected, readRows())
        // Reopen reads only Room; there is no second HTTP fetch to repopulate it.
        assertRequests(1)
    }

    @Test
    fun secondSuccessfulRefreshReplacesRemovedAndChangedRows() = runBlocking {
        enqueueSuccess(fixture)
        repository.getIncidents()
        enqueueSuccess("""[
            {"id":"INC-API-002","title":"Updated by second response","status":"RESOLVED"},
            {"id":"INC-API-004","title":"New snapshot row","status":"OPEN"}
        ]""")
        val replacement = listOf(
            Incident("INC-API-002", "Updated by second response", IncidentStatus.RESOLVED),
            Incident("INC-API-004", "New snapshot row", IncidentStatus.OPEN),
        )

        assertEquals(replacement, repository.getIncidents())
        assertEquals(replacement, readRows())
        assertEquals(listOf("INC-API-002", "INC-API-004"), database.incidentDao().readAll().map { it.id })
        assertRequests(2)
    }

    @Test
    fun http500ReturnsErrorWithoutServingExistingRoomRows() = runTest(dispatcher) {
        database.incidentDao().replaceAll(expected.map(Incident::toEntity))
        server.enqueue(MockResponse().setResponseCode(500).setBody("fixture failure"))
        val viewModel = IncidentListViewModel(repository)
        val states = mutableListOf<IncidentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        val error = IncidentUiState.Error("Unable to load incidents.")
        assertEquals(error, awaitResult(viewModel))
        assertEquals(listOf(IncidentUiState.Loading, error), states)
        assertEquals(expected, readRows())
        assertRequests(1)
    }

    @Test
    fun failedRoomWriteRollsBackAndNeverPublishesContent() = runTest(dispatcher) {
        database.incidentDao().replaceAll(expected.map(Incident::toEntity))
        enqueueSuccess("""[
            {"id":"DUPLICATE","title":"First","status":"OPEN"},
            {"id":"DUPLICATE","title":"Second","status":"RESOLVED"}
        ]""")
        val viewModel = IncidentListViewModel(repository)
        val states = mutableListOf<IncidentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states.add(it) }
        }

        val error = IncidentUiState.Error("Unable to load incidents.")
        assertEquals(error, awaitResult(viewModel))
        assertEquals(listOf(IncidentUiState.Loading, error), states)
        assertEquals(expected, readRows())
        assertRequests(1)
    }

    private fun enqueueSuccess(body: String) {
        server.enqueue(MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json").setBody(body))
    }

    private suspend fun readRows(): List<Incident> = database.incidentDao().readAll().map { it.toDomain() }

    private suspend fun awaitResult(viewModel: IncidentListViewModel): IncidentUiState =
        withContext(Dispatchers.Default) {
            withTimeout(15_000) { viewModel.uiState.first { it !is IncidentUiState.Loading } }
        }

    private fun assertRequests(count: Int) {
        repeat(count) {
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("/incidents", request.path)
        }
        assertEquals(count, server.requestCount)
    }
}
