package com.safal207.androidreliabilitylab.data

import com.google.gson.Gson
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

enum class MutationDelivery { CONFIRMED, TRANSPORT_FAILURE }

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

    suspend fun changeStatus(incidentId: String, status: IncidentStatus): MutationDelivery =
        withContext(Dispatchers.IO) {
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
            val request = Request.Builder().url(url).put(body).build()
            // Only failure to obtain an HTTP response is queueable. Do not parse a
            // response body here: an HTTP error or malformed payload is not offline.
            val response = try {
                client.newCall(request).execute()
            } catch (failure: IOException) {
                ensureActive()
                return@withContext MutationDelivery.TRANSPORT_FAILURE
            }
            response.use {
                ensureActive()
                if (!it.isSuccessful) throw MutationHttpException(it.code)
            }
            MutationDelivery.CONFIRMED
        }
}
