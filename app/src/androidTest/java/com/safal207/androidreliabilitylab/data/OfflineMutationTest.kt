package com.safal207.androidreliabilitylab.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.safal207.androidreliabilitylab.data.local.IncidentDatabase
import com.safal207.androidreliabilitylab.data.local.PendingMutationEntity
import com.safal207.androidreliabilitylab.domain.Incident
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import com.safal207.androidreliabilitylab.ui.IncidentListViewModel
import com.safal207.androidreliabilitylab.ui.IncidentMutationUiState
import com.safal207.androidreliabilitylab.ui.IncidentUiState
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMutationTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = MockWebServer()
    private lateinit var context: Context
    private lateinit var database: IncidentDatabase
    private lateinit var databaseName: String
    private lateinit var repository: PersistentIncidentRepository
    private lateinit var mutator: PersistentIncidentStatusMutator
    private lateinit var fixture: String
    private val expected = listOf(
        Incident("INC-API-001", "HTTP boundary received", IncidentStatus.OPEN),
        Incident("INC-API-002", "DTO mapping checked", IncidentStatus.INVESTIGATING),
        Incident("INC-API-003", "API content rendered", IncidentStatus.RESOLVED),
    )
    private val pending = PendingMutationEntity("local-test-001", "INC-API-001", "RESOLVED", 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        fixture = instrumentation.context.assets.open("incidents.json").bufferedReader().use { it.readText() }
        databaseName = "room-bead-004-${UUID.randomUUID()}.db"
        database = IncidentDatabase.open(context, databaseName)
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .callTimeout(10, TimeUnit.SECONDS).build()
        repository = PersistentIncidentRepository(
            HttpIncidentRepository.create(server.url("/").toString(), client), database.incidentDao(),
        )
        mutator = PersistentIncidentStatusMutator(
            HttpIncidentMutationSource(server.url("/").toString(), client),
            database.pendingMutationDao(),
            newMutationId = { pending.mutationId },
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
    fun onlineMutationConfirmsWithoutPendingOrReplay() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        server.enqueue(MockResponse().setResponseCode(204))
        assertMutationStates(viewModel, IncidentMutationUiState.ServerConfirmed)
        assertResolved(viewModel)
        assertTrue(database.pendingMutationDao().readAll().isEmpty())
        assertMutationRequest()
        observeNoReplay()
    }

    @Test
    fun transportFailureQueuesOneIntentWithoutReplay() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertMutationStates(viewModel, IncidentMutationUiState.Pending(pending.mutationId))
        assertResolved(viewModel)
        assertEquals(listOf(pending), database.pendingMutationDao().readAll())
        assertMutationRequest()
        // If anything replays after the failure, it receives a success response and
        // changes the request count/state. This test never asks the app to replay.
        server.enqueue(MockResponse().setResponseCode(204))
        observeNoReplay()
        assertEquals(IncidentMutationUiState.Pending(pending.mutationId), viewModel.mutationState.value)
        assertEquals(listOf(pending), database.pendingMutationDao().readAll())
    }

    @Test
    fun pendingIntentSurvivesCloseAndFreshReopen() = runBlocking {
        enqueueList()
        assertEquals(expected, repository.getIncidents())
        assertGetRequest()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(MutationResult.Pending(pending.mutationId), mutator.changeStatus("INC-API-001", IncidentStatus.RESOLVED))
        assertMutationRequest()
        val file = context.getDatabasePath(databaseName)
        assertTrue(file.isFile)
        val previous = database
        previous.close()
        assertFalse(previous.isOpen)
        database = IncidentDatabase.open(context, databaseName)
        assertNotSame(previous, database)
        assertEquals(file.canonicalPath, context.getDatabasePath(databaseName).canonicalPath)
        assertEquals(listOf(pending), database.pendingMutationDao().readAll())
        assertEquals(resolvedIncidents(), readRows())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun http500MutationIsErrorWithoutQueueOrLocalChange() = runTest(dispatcher) {
        assertHttpError(500)
    }

    @Test
    fun http400MutationIsErrorWithoutQueueOrLocalChange() = runTest(dispatcher) {
        assertHttpError(400)
    }

    @Test
    fun http503RetryAfterZeroDoesNotReplayMutation() = runTest(dispatcher) {
        assertHttpError(503)
    }

    @Test
    fun failedPendingWriteRollsBackWithoutPublishingPending() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_pending
                BEFORE INSERT ON pending_mutations BEGIN
                SELECT RAISE(ABORT, 'forced pending write failure'); END""".trimIndent())
        }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertMutationStates(viewModel, IncidentMutationUiState.Error)
        assertEquals(IncidentUiState.Content(expected), viewModel.uiState.value)
        assertEquals(expected, readRows())
        assertTrue(database.pendingMutationDao().readAll().isEmpty())
        assertMutationRequest()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun versionOneMigrationPreservesIncidentRows() = runBlocking {
        database.close()
        // Recreate the actual Bead 003 schema, including its recorded Room identity.
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE incidents (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, status TEXT NOT NULL)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, '649460a7ebfed46a61f0ab7ee33725a3')")
            expected.forEach { old.execSQL("INSERT INTO incidents VALUES (?, ?, ?)", arrayOf(it.id, it.title, it.status.name)) }
            old.version = 1
        }
        database = IncidentDatabase.open(context, databaseName)
        assertEquals(expected, readRows())
        assertTrue(database.pendingMutationDao().readAll().isEmpty())
        assertEquals(2, database.openHelper.readableDatabase.version)
        database.pendingMutationDao().queue(pending.incidentId, pending.targetStatus, pending.mutationId)
        assertEquals(listOf(pending), database.pendingMutationDao().readAll())
        assertEquals(resolvedIncidents(), readRows())
        assertEquals(0, server.requestCount)
    }

    private suspend fun TestScope.assertHttpError(code: Int) {
        val viewModel = loadedViewModel()
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "0")
            .setBody("server rejected mutation"))
        server.enqueue(MockResponse().setResponseCode(204))
        assertMutationStates(viewModel, IncidentMutationUiState.Error)
        assertEquals(IncidentUiState.Content(expected), viewModel.uiState.value)
        assertEquals(expected, readRows())
        assertTrue(database.pendingMutationDao().readAll().isEmpty())
        assertMutationRequest()
        observeNoReplay()
    }

    private suspend fun loadedViewModel(): IncidentListViewModel {
        enqueueList()
        val viewModel = IncidentListViewModel(repository, mutator)
        val state = withContext(Dispatchers.Default) {
            withTimeout(15_000) { viewModel.uiState.first { it !is IncidentUiState.Loading } }
        }
        assertEquals(IncidentUiState.Content(expected), state)
        assertGetRequest()
        return viewModel
    }

    private suspend fun TestScope.assertMutationStates(viewModel: IncidentListViewModel, result: IncidentMutationUiState) {
        val states = mutableListOf<IncidentMutationUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.mutationState.collect { states.add(it) }
        }
        viewModel.resolveDemoIncident()
        val actual = withContext(Dispatchers.Default) {
            withTimeout(15_000) {
                viewModel.mutationState.first { it != IncidentMutationUiState.Idle && it != IncidentMutationUiState.Sending }
            }
        }
        assertEquals(result, actual)
        assertEquals(listOf(IncidentMutationUiState.Idle, IncidentMutationUiState.Sending, result), states)
    }

    private suspend fun assertResolved(viewModel: IncidentListViewModel) {
        assertEquals(IncidentUiState.Content(resolvedIncidents()), viewModel.uiState.value)
        assertEquals(resolvedIncidents(), readRows())
    }

    private fun resolvedIncidents() = expected.map {
        if (it.id == "INC-API-001") it.copy(status = IncidentStatus.RESOLVED) else it
    }

    private suspend fun readRows() = database.incidentDao().readAll().map { it.toDomain() }

    private fun enqueueList() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(fixture))
    }

    private fun assertGetRequest() {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/incidents", request.path)
    }

    private fun assertMutationRequest() {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("PUT", request.method)
        assertEquals("/incidents/INC-API-001/status", request.path)
        assertNull(request.getHeader("Idempotency-Key"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("status"), body.keySet())
        assertEquals("RESOLVED", body.get("status").asString)
    }

    private suspend fun observeNoReplay() {
        // Real elapsed time, not runTest's virtual clock.
        withContext(Dispatchers.Default) { delay(2_000) }
        assertEquals(2, server.requestCount) // one initial GET, one explicit PUT
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }
}
