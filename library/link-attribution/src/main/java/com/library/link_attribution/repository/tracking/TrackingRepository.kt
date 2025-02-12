package com.library.link_attribution.repository.tracking

import com.library.link_attribution.repository.tracking.model.TrackClickModel
import com.library.link_attribution.repository.tracking.remote.api.click.TrackClickRequest
import com.library.link_attribution.repository.tracking.remote.api.event.TrackEventRequest
import com.library.link_attribution.repository.tracking.remote.api.link.TrackLinkRequest
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    fun onAppDied()
    fun onLoggedOut()
    fun onTokenExpired()

    fun getCacheTrackClick(): TrackClickModel?
    fun setTrackClick(link: TrackClickModel?)
    fun trackClick(
        appUnid: String?,
        apiKey: String?,
        request: TrackClickRequest
    ): Flow<TrackClickModel?>

    fun trackEvent(request: TrackEventRequest): Flow<Unit>

    fun trackLink(request: TrackLinkRequest): Flow<Unit>
}
