package org.openauto.companion.net.api

import prodigy.api.v1.Api

interface ApiTransport : AutoCloseable {
    suspend fun connect()
    fun setReadTimeoutMillis(timeoutMs: Int) = Unit
    suspend fun send(message: Api.ApiMessage)
    suspend fun receive(): Api.ApiMessage?
}
