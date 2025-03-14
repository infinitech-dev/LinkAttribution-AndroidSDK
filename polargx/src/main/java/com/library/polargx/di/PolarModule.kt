package com.library.polargx.di

import android.content.Context
import com.library.polargx.Polar
import com.library.polargx.Polar.Companion.TAG
import com.library.polargx.PolarConstants
import com.library.polargx.configuration.Configuration
import com.library.polargx.logger.PolarLogger
import com.library.polargx.model.ApiError
import com.library.polargx.repository.event.EventRepository
import com.library.polargx.repository.event.EventRepositoryImpl
import com.library.polargx.repository.event.local.EventLocalDatasource
import com.library.polargx.repository.event.local.EventLocalDatasourceImpl
import com.library.polargx.repository.event.remote.EventRemoteDatasource
import com.library.polargx.repository.event.remote.EventRemoteDatasourceImpl
import com.library.polargx.repository.link.LinkRepository
import com.library.polargx.repository.link.LinkRepositoryImpl
import com.library.polargx.repository.link.local.LinkLocalDatasource
import com.library.polargx.repository.link.local.LinkLocalDatasourceImpl
import com.library.polargx.repository.link.remote.LinkRemoteDatasource
import com.library.polargx.repository.link.remote.LinkRemoteDatasourceImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.append
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val polarModule = module {
    singleOf(::LinkLocalDatasourceImpl) { bind<LinkLocalDatasource>() }
    single {
        LinkRemoteDatasourceImpl(
            client = get(named(PolarConstants.Koin.APP_HTTP_CLIENT)),
        )
    } bind LinkRemoteDatasource::class
    singleOf(::LinkRepositoryImpl) bind LinkRepository::class

    singleOf(::EventLocalDatasourceImpl) bind EventLocalDatasource::class
    single {
        EventRemoteDatasourceImpl(
            client = get(named(PolarConstants.Koin.APP_HTTP_CLIENT)),
        )
    } bind EventRemoteDatasource::class
    singleOf(::EventRepositoryImpl) bind EventRepository::class


    single {
        androidApplication().getSharedPreferences(
            "PolarGX.file",
            Context.MODE_PRIVATE
        )
    }

    single(named(PolarConstants.Koin.APP_HTTP_CLIENT)) {
        val client = HttpClient(Android) {
            engine {
                socketTimeout = 60_000
                connectTimeout = 60_000
            }
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = Configuration.Env.server
                }
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    append("x-api-key", Polar.getConfigs()?.apiKey ?: "")
                }
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(0)
                exponentialDelay()
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        PolarLogger.i("HttpClient", message)
                    }
                }
                level = LogLevel.ALL
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(HttpCache)
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
            }

            HttpResponseValidator {
                validateResponse { response ->
                    PolarLogger.d(TAG, "validateResponse: response=$response")
                    if (!response.status.isSuccess()) {
                        throw ClientRequestException(response, "")
                    }
                }

                handleResponseExceptionWithRequest { cause, request ->
                    PolarLogger.d(
                        TAG,
                        "handleResponseExceptionWithRequest: cause=$cause, request=$request"
                    )
                    try {
                        if (cause !is ClientRequestException) throw cause
                        val errorData = cause.response.bodyAsText()
                        val error = ApiError(errorData)
                        PolarLogger.d(
                            TAG,
                            "handleResponseExceptionWithRequest: error=${error}, errorData=${errorData}"
                        )
                        when (error.code) {

                        }
                        throw ApiError(errorData)
                    } catch (ex: Throwable) {
                        PolarLogger.d(TAG, "response: ex=${ex}")
                        throw ex
                    }
                }
            }

        }
        client
    }
}