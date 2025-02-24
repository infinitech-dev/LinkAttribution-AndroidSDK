package com.library.link_attribution

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.window.layout.WindowMetricsCalculator
import com.library.link_attribution.di.linkAttributeModule
import com.library.link_attribution.extension.getDeviceModel
import com.library.link_attribution.extension.getDeviceName
import com.library.link_attribution.extension.getIP4Address
import com.library.link_attribution.extension.getIP6Address
import com.library.link_attribution.extension.getManufacturer
import com.library.link_attribution.extension.getOsVersion
import com.library.link_attribution.extension.getSdkVersion
import com.library.link_attribution.lifecycle.AppLifecycleMonitor
import com.library.link_attribution.listener.LinkInitListener
import com.library.link_attribution.logger.LALogger
import com.library.link_attribution.model.configs.ConfigsModel
import com.library.link_attribution.repository.event.EventRepository
import com.library.link_attribution.repository.event.model.EventModel
import com.library.link_attribution.repository.event.remote.api.EventTrackRequest
import com.library.link_attribution.repository.link.LinkRepository
import com.library.link_attribution.repository.link.model.link.LinkDataModel
import com.library.link_attribution.repository.link.remote.api.click.LinkClickRequest
import com.library.link_attribution.repository.link.remote.api.track.LinkTrackRequest
import com.library.link_attribution.utils.DateTimeUtils
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Calendar

class LinkAttribution(
    private val application: Application,
    private val appId: String?,
) : KoinComponent {

    private val eventRepository: EventRepository by inject()
    private val linkRepository: LinkRepository by inject()

    private lateinit var mKronosClock: KronosClock

    private var isAppInitialed: Boolean? = null

    private var mEventTrackingJob: Job? = null

    private var mLastLink: LinkDataModel? = null


    companion object {
        const val TAG = ">>>LinkAttribution"
        const val ENDPOINT = "jw4xix6q44.execute-api.us-east-1.amazonaws.com/dev"

        @SuppressLint("StaticFieldLeak")
        private var instance: LinkAttribution? = null
        private var mConfigs: ConfigsModel? = null
        var isLoggingEnabled = true

        private var mInitAppJob: Job? = null
        private var mGetLinkJob: Job? = null

        private var mLastUri: Uri? = null

        @SuppressLint("StaticFieldLeak")
        private var mLastActivity: Activity? = null
        private var mLastListener: LinkInitListener? = null
        private var isReInitializing: Boolean? = null

        fun getConfigs(): ConfigsModel? {
            return mConfigs
        }

        fun isAppInitializing(): Boolean {
            return mInitAppJob?.isActive == true
        }

        fun initApp(
            application: Application,
            appId: String?,
            apiKey: String?,
        ) {
            LALogger.d(TAG, "initApp: appId=$appId, apiKey=$apiKey")
            if (mInitAppJob?.isActive == true) {
                mInitAppJob?.cancel()
                mInitAppJob = null
            }
            mInitAppJob = CoroutineScope(Dispatchers.IO).launch {
                mConfigs = ConfigsModel(appId = appId, apiKey = apiKey)
                if (instance == null) {
                    instance = LinkAttribution(
                        application = application,
                        appId = appId,
                    ).apply {
                        startInject()
                        mKronosClock = AndroidClockFactory.createKronosClock(application)
                        mKronosClock.sync()
                    }
                }
                instance?.startInitializingApp()
                if (mLastUri != null) {
                    instance?.init(mLastActivity, mLastUri)
                } else {
                    mLastListener?.onInitFinished(null, null)
                }
                instance?.initListener()
            }
        }

        fun init(
            activity: Activity?,
            uri: Uri?,
            listener: LinkInitListener
        ) {
            LALogger.d(TAG, "init: uri=$uri")
            if (mGetLinkJob?.isActive == true) {
                mGetLinkJob?.cancel()
            }
            mGetLinkJob = CoroutineScope(Dispatchers.IO).launch {
                isReInitializing = false
                mLastUri = uri
                mLastActivity = activity
                mLastListener = listener
                if (isAppInitializing()) return@launch
                instance?.init(activity = activity, uri = uri)
            }
        }

        fun reInit(
            activity: Activity?,
            uri: Uri?,
            listener: LinkInitListener
        ) {
            LALogger.d(TAG, "reInit: uri=$uri")
            if (mGetLinkJob?.isActive == true) {
                mGetLinkJob?.cancel()
            }
            mGetLinkJob = CoroutineScope(Dispatchers.IO).launch {
                isReInitializing = true
                mLastUri = uri
                mLastActivity = activity
                mLastListener = listener
                if (isAppInitializing()) return@launch
                instance?.reInit(activity = activity, uri = uri)
            }
        }
    }

    private fun isKoinStarted(): Boolean {
        return GlobalContext.getOrNull() != null
    }

    private fun startInject() {
        if (isKoinStarted()) {
            loadKoinModules(linkAttributeModule)
            return
        }
        startKoin {
            androidLogger()
            androidContext(application)
            modules(linkAttributeModule)
        }
    }

    suspend fun startInitializingApp() {
        reset()
        var shouldRetry = true
        do {
            try {
                val now = Calendar.getInstance().apply {
                    timeInMillis = mKronosClock.getCurrentTimeMs()
                }
                val launchEvent = EventModel(
                    organizationUnid = appId,
                    eventName = EventModel.Type.APP_LAUNCH,
                    eventTime = DateTimeUtils.calendarToString(
                        source = now,
                        format = LinkAttributionConstants.DateTime.DEFAULT_DATE_FORMAT,
                        timeZone = LinkAttributionConstants.DateTime.utcTimeZone,
                    ),
                    data = mutableMapOf()
                )
                val request = EventTrackRequest.from(launchEvent)
                val response = eventRepository.rawTrack(request)
                if (response.status.isSuccess()) {
                    LALogger.d(TAG, "startInitializingApp: successful ✅")
                    isAppInitialed = true
                    shouldRetry = false
                }
                if (response.status.value == 403) {
                    LALogger.d(TAG, "startInitializingApp: ⛔⛔⛔ INVALID appId or xApiKey! ⛔⛔⛔")
                    shouldRetry = false
                }
            } catch (throwable: Throwable) {
                when (throwable) {
                    is ConnectException -> {
                        // Handle connection refused or other connection issues (no internet)
                        LALogger.d(
                            TAG,
                            "startInitializingApp: ⛔No internet connection + retry 🔁 ex=$throwable"
                        )
                        shouldRetry = true
                        delay(1000)
                    }

                    is UnknownHostException -> {
                        // Handle DNS resolution failures (no internet or incorrect URL)
                        LALogger.d(
                            TAG,
                            "startInitializingApp: ⛔Unknown host + retry 🔁 ex=$throwable"
                        )
                        shouldRetry = true
                        delay(1000)
                    }

                    else -> {
                        // Handle other exceptions (e.g., server errors, JSON parsing)
                        LALogger.d(
                            TAG,
                            "startInitializingApp: ⛔⛔⛔An error occurred ⛔⛔⛔ ex=$throwable"
                        )
                        shouldRetry = false
                    }
                }
            }
        } while (shouldRetry)
    }

    private fun initListener() {
        CoroutineScope(Dispatchers.Main).launch {
            application.registerActivityLifecycleCallbacks(
                AppLifecycleMonitor(object : AppLifecycleMonitor.Listener {

                    override fun onAppForegrounded() {
                        LALogger.d(TAG, "onAppForegrounded:")
                        instance?.trackEvent(
                            type = EventModel.Type.APP_OPEN,
                            data = mutableMapOf()
                        )
                    }

                    override fun onAppBackgrounded() {
                        LALogger.d(TAG, "onAppBackgrounded:")
                        instance?.trackEvent(
                            type = EventModel.Type.APP_CLOSE,
                            data = mutableMapOf()
                        )
                    }

                    override fun onAppFirstActivityCreated() {
//                    Logger.d(TAG, "onAppFirstActivityCreated")

                    }

                    override fun onAppOpenWhenApplicationAlive() {
//                    Logger.d(TAG, "onAppOpenWhenApplicationAlive")

                    }

                    override fun onAppGoToForeground() {
//                    Logger.d(TAG, "onAppGoToForeground")
//                    instance?.trackEvent(
//                        type = EventModel.Type.APP_OPEN,
//                        data = mutableMapOf()
//                    )
                    }

                    override fun onAppGoToBackgroundViaHomeButton(foregroundActivity: Activity?) {
//                    Logger.d(
//                        TAG,
//                        "onAppGoToBackgroundViaHomeButton: foregroundActivity=$foregroundActivity"
//                    )
//                    instance?.trackEvent(
//                        type = EventModel.Type.APP_CLOSE,
//                        data = mutableMapOf()
//                    )
                    }

                    override fun onAppGoToBackgroundViaBackLastActivity(lastActivity: Activity?) {
//                    Logger.d(TAG, "onAppGoToBackgroundViaBackLastActivity: lastActivity=$lastActivity")
//                    instance?.trackEvent(
//                        type = EventModel.Type.APP_TERMINATE,
//                        data = mutableMapOf()
//                    )
                    }

                })
            )
        }
    }

    private suspend fun reset() {
        eventRepository.reset()
        linkRepository.reset()
    }

    fun trackEvent(
        @EventModel.Type type: String?,
        data: Map<String, String?>?
    ) {
        trackEvent(
            type = type,
            time = Calendar.getInstance().apply {
                timeInMillis = mKronosClock.getCurrentTimeMs()
            },
            data = data
        )
    }

    fun trackEvent(
        @EventModel.Type type: String?,
        time: Calendar,
        data: Map<String, String?>?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val event = EventModel(
                organizationUnid = appId,
                eventName = type,
//                eventTime = DateTimeUtils.calendarToString(
//                    source = time,
//                    format = LinkAttributionConstants.DateTime.DEFAULT_DATE_FORMAT,
//                ),
                eventTime = DateTimeUtils.calendarToString(
                    source = time,
                    format = LinkAttributionConstants.DateTime.DEFAULT_DATE_FORMAT,
                    timeZone = LinkAttributionConstants.DateTime.utcTimeZone,
                ),
                data = data
            )
            val eventList = eventRepository.getCacheEventList()?.toMutableList() ?: mutableListOf()
            eventList.add(event)
            eventRepository.setCacheEventList(eventList)
            startTrackingQueueIfNeeded()
        }
    }

    private fun startTrackingQueueIfNeeded() {
        if (mEventTrackingJob?.isActive == true) return
        mEventTrackingJob = CoroutineScope(Dispatchers.IO).launch {
            eventRepository.getCacheEventList()?.let { eventList ->
                if (eventList.isEmpty()) return@launch
                eventList.toMutableList().map { event ->
                    async {
                        try {
                            val request = EventTrackRequest.from(event)
                            val response = eventRepository.rawTrack(request)
                            if (response.status.isSuccess()) {
                                LALogger.d(
                                    TAG,
                                    "startTrackingQueueIfNeeded: successful ✅, event=$event"
                                )
                                val latestEventList =
                                    eventRepository.getCacheEventList()?.toMutableList()
                                latestEventList?.remove(event)
                                eventRepository.setCacheEventList(latestEventList)
                            } else {
                                val latestEventList =
                                    eventRepository.getCacheEventList()?.toMutableList()
                                latestEventList?.remove(event)
                                eventRepository.setCacheEventList(latestEventList)
                            }
                        } catch (throwable: Throwable) {
                            LALogger.d(
                                TAG,
                                "startTrackingQueueIfNeeded: ⛔error: ex=$throwable, event=$event"
                            )
                            when (throwable) {
                                is ConnectException -> {
                                    // Handle connection refused or other connection issues (no internet)
                                }

                                is UnknownHostException -> {
                                    // Handle DNS resolution failures (no internet or incorrect URL)
                                }

                                else -> {
                                    val latestEventList =
                                        eventRepository.getCacheEventList()?.toMutableList()
                                    latestEventList?.remove(event)
                                    eventRepository.setCacheEventList(latestEventList)
                                }
                            }
                        }
                    }
                }.awaitAll()

                startTrackingQueueIfNeeded()
            }
        }
    }

    suspend fun init(activity: Activity?, uri: Uri?) {
        handleFetchLinkData(activity = activity, uri = mLastUri)
    }

    suspend fun reInit(activity: Activity?, uri: Uri?) {
        handleFetchLinkData(activity = activity, uri = mLastUri)
    }

    private suspend fun handleFetchLinkData(activity: Activity?, uri: Uri?) {
        if (activity == null) return
        val domain = uri?.host
        if (domain?.endsWith(LinkAttributionConstants.Configuration.DOMAIN_SUFFIX) != true) {
            LALogger.d(TAG, "handleFetchLinkData: Invalid domain! domain=$domain")
            mLastListener?.onInitFinished(null, null)
            return
        }
        val subDomain = domain.replace(LinkAttributionConstants.Configuration.DOMAIN_SUFFIX, "")
        val path = uri.path?.replace("/", "")
        val isFirstTimeLaunch = eventRepository.isFirstTimeLaunch(
            activity,
            mKronosClock.getCurrentTimeMs()
        )
        val clickTime = Calendar.getInstance().apply {
            timeInMillis = mKronosClock.getCurrentTimeMs()
        }
        if (!uri.path.isNullOrEmpty()) {
            try {
                val getLinkResponse = linkRepository.fetchLinkData(
                    domain = subDomain,
                    slug = path
                )
                mLastLink = getLinkResponse.data?.sdkLinkData?.toExternal()

                val trackRequest = LinkTrackRequest(
                    clickTime = DateTimeUtils.calendarToString(
                        clickTime,
                        LinkAttributionConstants.DateTime.DEFAULT_DATE_FORMAT,
                        LinkAttributionConstants.DateTime.utcTimeZone,
                    ),
                    domain = subDomain,
                    slug = path,
                    fingerprint = LinkTrackRequest.Fingerprint.ANDROID_SDK,
                    trackType = LinkTrackRequest.TrackType.APP_CLICK,
                    deviceData = mutableMapOf(),
                    additionalData = mutableMapOf(),
                )
                val clid = uri.getQueryParameter("__clid")
                if (clid.isNullOrEmpty()) {
                    val trackResponse = linkRepository.track(trackRequest)
                    val linkClickUnid = trackResponse.data?.linkClick?.unid
                    val request = LinkClickRequest(sdkUsed = true)
                    linkRepository.linkClick(linkClickUnid, request)
                } else {
                    val request = LinkClickRequest(sdkUsed = true)
                    linkRepository.linkClick(clid, request)
                }
                mLastListener?.onInitFinished(mLastLink?.data, null)
            } catch (throwable: Throwable) {
                mLastListener?.onInitFinished(null, throwable)
            }
            return
        }
        if (isFirstTimeLaunch && uri.path.isNullOrEmpty()) {
            val windowMetrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(activity)
            val width = windowMetrics.bounds.width()
            val height = windowMetrics.bounds.height()

            // Get density using Resources
            val metrics = activity.resources?.displayMetrics
            val density = metrics?.density
            val densityDpi = metrics?.densityDpi

            LALogger.d(
                TAG, "initSession: " +
                        "\nIP4Address=${application.getIP4Address()}" +
                        "\nIP6Address=${application.getIP6Address()}" +
                        "\nosVersion=${application.getOsVersion()}" +
                        "\nsdkVersion=${application.getSdkVersion()}" +
                        "\ndeviceModel=${application.getDeviceModel()}" +
                        "\nmanufacturer=${application.getManufacturer()}" +
                        "\ndeviceName=${application.getDeviceName()}" +
                        "\nwindow.width=${width}" +
                        "\nwindow.height=${height}" +
                        "\nwindow.density=${density}" +
                        "\nwindow.densityDpi=${densityDpi}"
            )
//                linkRepository.fetchLinkMatches()
//                getLinkByMatching()
        }
    }

    private fun onInternetConnectionChanged(connected: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            if (connected) {
                if (isAppInitialed == true) {
                    startTrackingQueueIfNeeded()
                    return@launch
                }
                if (isAppInitializing()) return@launch
                startInitializingApp()
            }
        }
    }
}