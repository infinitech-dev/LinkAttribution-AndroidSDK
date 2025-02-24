package com.library.link_attribution.repository.event

import android.content.Context
import com.library.link_attribution.model.ApiError
import com.library.link_attribution.repository.event.local.EventLocalDatasource
import com.library.link_attribution.repository.event.local.model.EventEntity
import com.library.link_attribution.repository.event.local.model.EventEntity.Companion.toEntity
import com.library.link_attribution.repository.event.model.EventModel
import com.library.link_attribution.repository.event.remote.EventRemoteDatasource
import com.library.link_attribution.repository.event.remote.api.EventTrackRequest
import com.library.link_attribution.repository.event.remote.api.EventTrackResponse
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.TreeMap

class EventRepositoryImpl(
    private val localDatasource: EventLocalDatasource,
    private val remoteDatasource: EventRemoteDatasource
) : EventRepository {

    companion object {
        const val TAG = ">>>EventRepositoryImpl"
    }

    private var mEventsList: List<EventModel>? = null

    override suspend fun onAppDied() {
        mEventsList = null
    }

    private suspend fun onUnauthenticated() {
        mEventsList = null
    }

    override suspend fun onLoggedOut() {
        onUnauthenticated()
    }

    override suspend fun onTokenExpired() {
        onUnauthenticated()
    }

    override suspend fun setCacheEventList(events: List<EventModel>?) {
        mEventsList = events
        val entities = events?.mapNotNull { it.toEntity() }
        localDatasource.setCacheEventList(entities)
    }

    override suspend fun getCacheEventList(): List<EventModel>? {
        if (mEventsList != null) return mEventsList
        val events = localDatasource.getAllCacheEventList()?.map { it.toExternal() }
        mEventsList = events
        return events
    }

    override suspend fun rawTrack(request: EventTrackRequest?): HttpResponse {
        return remoteDatasource.track(request = request)
    }

    override suspend fun track(request: EventTrackRequest?): EventTrackResponse {
        val response = remoteDatasource.track(
            request = request,
        )
        if (!response.status.isSuccess()) {
            throw ApiError(response.bodyAsText())
        }
        return response.body<EventTrackResponse>()
    }

    override suspend fun reset() {
        mEventsList = null
    }

    override suspend fun isFirstTimeLaunch(
        context: Context?,
        nowInMillis: Long
    ): Boolean {
        return localDatasource.isFirstTimeLaunch(
            context,
            nowInMillis
        )
    }
}
