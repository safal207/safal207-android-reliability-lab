package com.safal207.androidreliabilitylab.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safal207.androidreliabilitylab.data.local.IncidentDatabase
import com.safal207.androidreliabilitylab.data.local.PendingMutationEntity
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RetrySafetyTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = MockWebServer()
    private lateinit var context: Context
    private lateinit var database: IncidentDatabase
    private lateinit var databaseName: String
    private lateinit var ledgerName: String
    private lateinit var fixture: DurableReceiptFixture
    private lateinit var remote: HttpIncidentMutationSource
    private lateinit var mutator: PersistentIncidentStatusMutator
    private lateinit var repository: PersistentIncidentRepository
    private var generatedIds = 0
    private val pending = PendingMutationEntity("stable-action-005", "INC-API-001", "RESOLVED", 1)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        databaseName = "room-bead-005-${UUID.randomUUID()}.db"
        ledgerName = "fixture-bead-005-${UUID.randomUUID()}.db"
        database = IncidentDatabase.open(context, databaseName)
        val incidents = instrumentation.context.assets.open("incidents.json").bufferedReader().use { it.readText() }
        fixture = DurableReceiptFixture(context.getDatabasePath(ledgerName), incidents)
        server.dispatcher = fixture
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
        remote = HttpIncidentMutationSource(server.url("/").toString(), client)
        repository = PersistentIncidentRepository(HttpIncidentRepository.create(server.url("/").toString(), client), database.incidentDao())
        mutator = PersistentIncidentStatusMutator(remote, database.pendingMutationDao()) {
            assertEquals("Identity must be created before the first PUT", 1, server.requestCount)
            generatedIds++
            assertEquals(1, generatedIds)
            pending.mutationId
        }
    }

    @After fun tearDown() {
        server.shutdown()
        fixture.close()
        database.close()
        context.deleteDatabase(databaseName)
        context.deleteDatabase(ledgerName)
        Dispatchers.resetMain()
    }

    @Test fun vectorAOnlineSuccessStoresMatchingReceipt() = runTest(dispatcher) {
        val viewModel = loaded()
        act(viewModel, IncidentMutationUiState.ServerConfirmed) { viewModel.resolveDemoIncident() }
        request()
        assertEquals(1, generatedIds)
        assertEquals(1, fixture.attempts())
        assertEquals(1, fixture.effects())
        assertEquals("RESOLVED", fixture.status())
        assertConfirmedRows()
    }

    @Test fun vectorBAppliedThenLostResponseKeepsSamePendingIdentity() = runTest(dispatcher) {
        val viewModel = ambiguous()
        assertPendingRows()
        assertEquals(1, fixture.attempts())
        assertEquals(1, fixture.effects())
        assertEquals("RESOLVED", fixture.status())
        val receipt = fixture.receipt()
        assertEquals(pending.mutationId, receipt.actionId)
        // Independently reopen the fixture file after its commit, while no new request is sent.
        SQLiteDatabase.openDatabase(context.getDatabasePath(ledgerName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT receipt FROM effects WHERE actionId=?", arrayOf(pending.mutationId)).use {
                assertTrue(it.moveToFirst())
                assertEquals(receipt, com.google.gson.Gson().fromJson(it.getString(0), MutationReceipt::class.java))
            }
        }
        fixture.disconnectAfterApply = false
        withContext(Dispatchers.Default) { delay(2_000) }
        assertEquals(1, fixture.attempts())
        assertEquals(2, server.requestCount)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        assertEquals(IncidentMutationUiState.Pending(pending.mutationId), viewModel.mutationState.value)
    }

    @Test fun vectorCExplicitReplayReturnsSameReceiptWithoutSecondEffect() = runTest(dispatcher) {
        val viewModel = ambiguous()
        val firstReceipt = fixture.receipt()
        fixture.disconnectAfterApply = false
        act(viewModel, IncidentMutationUiState.ServerConfirmed) { viewModel.retryPendingIncident() }
        request()
        assertEquals(1, generatedIds)
        assertEquals(2, fixture.attempts())
        assertEquals(1, fixture.effects())
        assertEquals(firstReceipt, fixture.receipt())
        assertConfirmedRows()
        assertEquals(firstReceipt.toEntity(), database.pendingMutationDao().readReceipts().single())
    }

    @Test fun vectorDSameIdentityDifferentPayloadIs409WithoutCorruption() = runTest(dispatcher) {
        val viewModel = ambiguous()
        val firstReceipt = fixture.receipt()
        fixture.disconnectAfterApply = false
        try {
            remote.changeStatus(pending.mutationId, pending.incidentId, IncidentStatus.INVESTIGATING)
            fail("Conflicting payload must fail")
        } catch (failure: MutationHttpException) {
            assertEquals(409, failure.statusCode)
        }
        request("INVESTIGATING")
        assertPendingRows()
        assertEquals(IncidentMutationUiState.Pending(pending.mutationId), viewModel.mutationState.value)
        assertEquals(2, fixture.attempts())
        assertEquals(1, fixture.effects())
        assertEquals(firstReceipt, fixture.receipt())
        assertEquals("RESOLVED", fixture.status())
    }

    @Test fun vectorEMismatchedReceiptNeverClearsPendingOrConfirms() = runTest(dispatcher) {
        val viewModel = ambiguous()
        fixture.disconnectAfterApply = false
        val mismatches: List<(MutationReceipt) -> MutationReceipt> = listOf(
            { it.copy(actionId = "another-action") },
            { it.copy(incidentId = "INC-API-002") },
            { it.copy(targetStatus = "OPEN") },
        )
        for (mismatch in mismatches) {
            fixture.alterReceipt = mismatch
            act(viewModel, IncidentMutationUiState.RetryError(pending.mutationId)) { viewModel.retryPendingIncident() }
            request()
            assertPendingRows()
        }
        assertEquals(1, generatedIds)
        assertEquals(4, fixture.attempts())
        assertEquals(1, fixture.effects())
    }

    @Test fun vectorFFailedReceiptTransactionRollsBackAndRetainsDurablePending() = runTest(dispatcher) {
        val viewModel = ambiguous()
        fixture.disconnectAfterApply = false
        // Abort after receipt INSERT and pending DELETE have executed; both must roll back.
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER reject_confirmation
                AFTER DELETE ON pending_mutations BEGIN
                SELECT RAISE(ABORT, 'forced receipt transaction failure'); END""".trimIndent())
        }
        act(viewModel, IncidentMutationUiState.RetryError(pending.mutationId)) { viewModel.retryPendingIncident() }
        request()
        assertPendingRows()
        reopen()
        assertPendingRows()
        assertEquals(2, fixture.attempts())
        assertEquals(1, fixture.effects())
    }

    @Test fun vectorGReceiptSurvivesFileBackedCloseAndFreshReopen() = runTest(dispatcher) {
        val viewModel = ambiguous()
        fixture.disconnectAfterApply = false
        act(viewModel, IncidentMutationUiState.ServerConfirmed) { viewModel.retryPendingIncident() }
        request()
        val receipt = fixture.receipt()
        assertConfirmedRows()
        reopen()
        assertConfirmedRows()
        assertEquals(receipt.toEntity(), database.pendingMutationDao().readReceipts().single())
        assertEquals(2, fixture.attempts())
        assertEquals(1, fixture.effects())
    }

    @Test fun versionTwoMigrationPreservesPendingWithoutReplay() = runTest(dispatcher) {
        database.close()
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE incidents (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, status TEXT NOT NULL)")
            old.execSQL("CREATE TABLE pending_mutations (mutationId TEXT NOT NULL PRIMARY KEY, incidentId TEXT NOT NULL, targetStatus TEXT NOT NULL, createdOrder INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            old.execSQL("INSERT INTO room_master_table VALUES (42, '3b63c7312cf33a2f9078e3cbb394fd59')")
            old.execSQL("INSERT INTO incidents VALUES ('INC-API-001', 'HTTP boundary received', 'RESOLVED')")
            old.execSQL("INSERT INTO pending_mutations VALUES (?, ?, ?, ?)", arrayOf(pending.mutationId, pending.incidentId, pending.targetStatus, pending.createdOrder))
            old.version = 2
        }
        database = IncidentDatabase.open(context, databaseName)
        assertPendingRows()
        assertEquals(3, database.openHelper.readableDatabase.version)
        assertEquals(0, generatedIds)
        assertEquals(0, server.requestCount)
    }

    private suspend fun loaded(): IncidentListViewModel {
        val viewModel = IncidentListViewModel(repository, mutator)
        val state = withContext(Dispatchers.Default) {
            withTimeout(15_000) { viewModel.uiState.first { it !is IncidentUiState.Loading } }
        }
        assertTrue(state is IncidentUiState.Content)
        assertEquals("GET", server.takeRequest(1, TimeUnit.SECONDS)?.method)
        return viewModel
    }

    private suspend fun TestScope.ambiguous(): IncidentListViewModel {
        val viewModel = loaded()
        fixture.disconnectAfterApply = true
        act(viewModel, IncidentMutationUiState.Pending(pending.mutationId)) { viewModel.resolveDemoIncident() }
        request()
        assertPendingRows()
        return viewModel
    }

    private suspend fun TestScope.act(viewModel: IncidentListViewModel, expected: IncidentMutationUiState, action: () -> Unit) {
        val states = mutableListOf<IncidentMutationUiState>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.mutationState.collect { states.add(it) } }
        val before = viewModel.mutationState.value
        action()
        assertEquals(IncidentMutationUiState.Sending, viewModel.mutationState.value)
        val actual = withContext(Dispatchers.Default) {
            withTimeout(15_000) { viewModel.mutationState.first { it != IncidentMutationUiState.Sending } }
        }
        assertEquals(expected, actual)
        assertEquals(listOf(before, IncidentMutationUiState.Sending, expected), states)
        collector.cancel()
    }

    private fun request(status: String = "RESOLVED") {
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("PUT", request.method)
        assertEquals("/incidents/INC-API-001/status", request.path)
        assertEquals(pending.mutationId, request.getHeader("Idempotency-Key"))
        assertEquals("{\"status\":\"$status\"}", request.body.readUtf8())
    }

    private suspend fun assertPendingRows() {
        assertEquals(listOf(pending), database.pendingMutationDao().readAll())
        assertTrue(database.pendingMutationDao().readReceipts().isEmpty())
        assertEquals("RESOLVED", database.incidentDao().readAll().single { it.id == pending.incidentId }.status)
    }

    private suspend fun assertConfirmedRows() {
        assertTrue(database.pendingMutationDao().readAll().isEmpty())
        assertEquals(listOf(fixture.receipt().toEntity()), database.pendingMutationDao().readReceipts())
        assertEquals("RESOLVED", database.incidentDao().readAll().single { it.id == pending.incidentId }.status)
    }

    private fun reopen() {
        val file = context.getDatabasePath(databaseName)
        assertTrue(file.isFile)
        val old = database
        old.close()
        assertFalse(old.isOpen)
        database = IncidentDatabase.open(context, databaseName)
        assertNotSame(old, database)
        assertEquals(file.canonicalPath, context.getDatabasePath(databaseName).canonicalPath)
    }
}
