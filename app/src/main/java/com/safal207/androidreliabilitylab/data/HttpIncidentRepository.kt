package com.safal207.androidreliabilitylab.data

import com.safal207.androidreliabilitylab.domain.Incident
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

internal interface IncidentApi {
    @GET("incidents")
    suspend fun getIncidents(): List<IncidentDto>
}

class HttpIncidentRepository internal constructor(
    private val api: IncidentApi,
) : IncidentRepository {
    override suspend fun getIncidents(): List<Incident> =
        api.getIncidents().map(IncidentDto::toDomain)

    companion object {
        fun create(
            baseUrl: String,
            client: OkHttpClient = OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(10, TimeUnit.SECONDS)
                .build(),
        ): HttpIncidentRepository {
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(IncidentApi::class.java)
            return HttpIncidentRepository(api)
        }
    }
}
