package org.openauto.companion.net.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import prodigy.api.v1.Api

class ApiSessionClient(
    private val transport: ApiTransport,
    private val handshake: ApiHandshake,
    private val scope: CoroutineScope
) : AutoCloseable {
    sealed class ConnectResult {
        data class Ready(
            val serverHello: Api.ServerHello,
            val pairedCredentials: ApiHandshake.PairedCredentials?
        ) : ConnectResult()

        data class Rejected(val reason: String) : ConnectResult()

        data class Disconnected(
            val reason: String,
            val cause: Throwable? = null
        ) : ConnectResult()
    }

    sealed class ReadyClose {
        data class PeerClosed(val reason: String) : ReadyClose()
        data class Rejected(val reason: String) : ReadyClose()
        data class Failed(val error: Throwable) : ReadyClose()
        data object ClientClosed : ReadyClose()
    }

    val incoming: Channel<Api.ApiMessage> = Channel(Channel.BUFFERED)

    private var readerJob: Job? = null
    private val readyClose = CompletableDeferred<ReadyClose>()
    private var connectStarted = false
    private var closedByClient = false

    suspend fun connect(): ConnectResult {
        check(!connectStarted) { "ApiSessionClient is single-use" }
        check(!closedByClient) { "ApiSessionClient is closed" }
        connectStarted = true

        try {
            transport.connect()
            transport.send(handshake.start())

            while (true) {
                val message = transport.receive()
                    ?: return disconnected("Connection closed during handshake")

                when (val result = handshake.handle(message)) {
                    is ApiHandshake.Result.Send -> transport.send(result.message)
                    is ApiHandshake.Result.Ready -> {
                        startReadyReader()
                        return ConnectResult.Ready(
                            serverHello = result.serverHello,
                            pairedCredentials = result.pairedCredentials
                        )
                    }
                    is ApiHandshake.Result.Terminal -> return rejected(result.reason)
                }
            }
        } catch (error: CancellationException) {
            transport.close()
            incoming.close(error)
            readyClose.complete(ReadyClose.Failed(error))
            throw error
        } catch (error: Throwable) {
            return disconnected(
                reason = error.message ?: "Connection failed during handshake",
                cause = error
            )
        }
    }

    suspend fun sendReadyMessage(message: Api.ApiMessage) {
        handshake.requireReadyForReports()
        transport.send(message)
    }

    suspend fun awaitClosed(): ReadyClose = readyClose.await()

    override fun close() {
        if (closedByClient) return
        closedByClient = true
        readerJob?.cancel()
        readerJob = null
        transport.close()
        incoming.close()
        readyClose.complete(ReadyClose.ClientClosed)
    }

    private fun startReadyReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                while (isActive) {
                    val message = transport.receive()
                    if (message == null) {
                        transport.close()
                        incoming.close()
                        readyClose.complete(ReadyClose.PeerClosed("Connection closed"))
                        return@launch
                    }
                    when (message.payloadCase) {
                        Api.ApiMessage.PayloadCase.AUTH_REJECT -> {
                            transport.close()
                            incoming.close()
                            readyClose.complete(ReadyClose.Rejected(message.authReject.reason))
                            return@launch
                        }
                        Api.ApiMessage.PayloadCase.ERROR -> {
                            transport.close()
                            incoming.close()
                            readyClose.complete(ReadyClose.Rejected(message.error.message))
                            return@launch
                        }
                        else -> incoming.send(message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                transport.close()
                incoming.close(error)
                readyClose.complete(ReadyClose.Failed(error))
            }
        }
    }

    private fun rejected(reason: String): ConnectResult.Rejected {
        transport.close()
        incoming.close()
        readyClose.complete(ReadyClose.Rejected(reason))
        return ConnectResult.Rejected(reason)
    }

    private fun disconnected(reason: String, cause: Throwable? = null): ConnectResult.Disconnected {
        transport.close()
        incoming.close(cause)
        if (cause == null) {
            readyClose.complete(ReadyClose.PeerClosed(reason))
        } else {
            readyClose.complete(ReadyClose.Failed(cause))
        }
        return ConnectResult.Disconnected(reason = reason, cause = cause)
    }
}
