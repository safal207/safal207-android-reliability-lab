package com.safal207.androidreliabilitylab.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.safal207.androidreliabilitylab.domain.IncidentStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

sealed interface MutationDelivery {
    data class Confirmed(val receipt: MutationReceipt) : MutationDelivery
    data object TransportFailure : MutationDelivery
}

internal data class StatusChangeDto(val status: String)

class MutationHttpException(val statusCode: Int) : Exception("Mutation HTTP $statusCode")

class HttpIncidentMutationSource(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val origin = baseUrl.toHttpUrl()

    suspend fun changeStatus(actionId: String, incidentId: String, status: IncidentStatus): MutationDelivery =
        withContext(Dispatchers.IO) {
            require(actionId.isNotBlank()) { "Missing stable action identity" }
            val url = origin.newBuilder().addPathSegment("incidents")
                .addPathSegment(incidentId).addPathSegment("status").build()
            val bytes = Gson().toJson(StatusChangeDto(status.name)).toByteArray()
            val body = object : RequestBody() {
                override fun contentType() = "application/json; charset=utf-8".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) { sink.write(bytes) }
                // Also prohibit HTTP follow-up retransmission (e.g. 503 Retry-After: 0).
                override fun isOneShot() = true
            }
            val request = Request.Builder().url(url).header("Idempotency-Key", actionId).put(body).build()
            // No HTTP response is an ambiguous transport failure. HTTP errors and
            // syntactically invalid/mismatched receipts remain explicit failures.
            val response = try {
                client.newCall(request).execute()
            } catch (failure: IOException) {
                ensureActive()
                return@withContext MutationDelivery.TransportFailure
            }
            response.use {
                ensureActive()
                if (!it.isSuccessful) throw MutationHttpException(it.code)
                // Losing the body of a successful response is also ambiguous. Catch
                // only I/O here; malformed JSON is not a transport failure.
                val json = try {
                    requireNotNull(it.body).string()
                } catch (failure: IOException) {
                    ensureActive()
                    return@withContext MutationDelivery.TransportFailure
                }
                val dto = JsonParser.parseString(json).asJsonObject
                fun string(name: String): String {
                    val value = requireNotNull(dto.get(name)) { "Missing receipt field $name" }
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid receipt field $name" }
                    return value.asString
                }
                fun number(name: String): Long {
                    val value = requireNotNull(dto.get(name)) { "Missing receipt field $name" }
                    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Invalid receipt field $name" }
                    return value.asBigDecimal.longValueExact()
                }
                require(number("receiptVersion") == 1L) { "Unsupported receipt version" }
                val receipt = MutationReceipt(
                    string("actionId"), string("incidentId"), string("targetStatus"), string("effectId"),
                    1, number("effectSequence"),
                ).requireMatch(actionId, incidentId, status)
                MutationDelivery.Confirmed(receipt)
            }
        }
}
