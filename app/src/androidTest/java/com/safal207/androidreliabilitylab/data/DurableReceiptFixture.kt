package com.safal207.androidreliabilitylab.data

import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

/** Test server state is independent of the client's Room database. Commit before disconnect. */
internal class DurableReceiptFixture(file: File, private val incidents: String) : Dispatcher(), AutoCloseable {
    private val ledger = SQLiteDatabase.openOrCreateDatabase(file.apply { parentFile?.mkdirs() }, null).apply {
        execSQL("PRAGMA synchronous=FULL")
        execSQL("CREATE TABLE effects (actionId TEXT PRIMARY KEY, payload TEXT NOT NULL, receipt TEXT NOT NULL)")
        execSQL("CREATE TABLE attempts (actionId TEXT NOT NULL, payload TEXT NOT NULL)")
        execSQL("CREATE TABLE incident_state (id TEXT PRIMARY KEY, status TEXT NOT NULL)")
        execSQL("INSERT INTO incident_state VALUES ('INC-API-001', 'OPEN')")
    }
    @Volatile var disconnectAfterApply = false
    @Volatile var alterReceipt: ((MutationReceipt) -> MutationReceipt)? = null

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.method == "GET" && request.path == "/incidents") return MockResponse().setBody(incidents)
        require(request.method == "PUT" && request.path == "/incidents/INC-API-001/status")
        val actionId = requireNotNull(request.getHeader("Idempotency-Key"))
        val body = JsonParser.parseString(request.body.clone().readUtf8()).asJsonObject
        require(body.keySet() == setOf("status"))
        val status = body.get("status").asString
        val payload = "INC-API-001:$status"
        var receipt: MutationReceipt? = null
        var conflict = false
        ledger.beginTransaction()
        try {
            ledger.execSQL("INSERT INTO attempts VALUES (?, ?)", arrayOf(actionId, payload))
            ledger.rawQuery("SELECT payload, receipt FROM effects WHERE actionId=?", arrayOf(actionId)).use {
                if (it.moveToFirst()) {
                    conflict = it.getString(0) != payload
                    receipt = Gson().fromJson(it.getString(1), MutationReceipt::class.java)
                }
            }
            if (receipt == null) {
                val sequence = effects().toLong() + 1
                receipt = MutationReceipt(actionId, "INC-API-001", status, "effect-$sequence", 1, sequence)
                ledger.execSQL("UPDATE incident_state SET status=? WHERE id='INC-API-001'", arrayOf(status))
                ledger.execSQL("INSERT INTO effects VALUES (?, ?, ?)", arrayOf(actionId, payload, Gson().toJson(receipt)))
            }
            ledger.setTransactionSuccessful()
        } finally {
            ledger.endTransaction()
        }
        if (conflict) return MockResponse().setResponseCode(409)
        if (disconnectAfterApply) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val original = requireNotNull(receipt)
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(Gson().toJson(alterReceipt?.invoke(original) ?: original))
    }

    @Synchronized fun effects(): Int = count("effects")
    @Synchronized fun attempts(): Int = count("attempts")
    private fun count(table: String): Int = ledger.rawQuery("SELECT COUNT(*) FROM $table", null).use {
        check(it.moveToFirst()); it.getInt(0)
    }
    @Synchronized fun receipt(): MutationReceipt = ledger.rawQuery("SELECT receipt FROM effects", null).use {
        check(it.moveToFirst()); Gson().fromJson(it.getString(0), MutationReceipt::class.java)
    }
    @Synchronized fun status(): String = ledger.rawQuery("SELECT status FROM incident_state", null).use {
        check(it.moveToFirst()); it.getString(0)
    }
    @Synchronized override fun close() = ledger.close()
}
