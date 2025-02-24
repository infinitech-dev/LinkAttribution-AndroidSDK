package com.library.link_attribution.repository.event.local

import android.content.Context
import com.library.link_attribution.repository.event.local.model.EventEntity

interface EventLocalDatasource {

    suspend fun setCacheEventList(events: List<EventEntity>?)
    suspend fun getAllCacheEventList(): List<EventEntity>?

//    fun getEvents(): TreeMap<Long, List<EventEntity>?>?
//    fun setEvents(events: TreeMap<Long, List<EventEntity>?>?)

    suspend fun isFirstTimeLaunch(
        context: Context?,
        nowInMillis: Long
    ): Boolean

}
