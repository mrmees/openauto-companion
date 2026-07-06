package org.openauto.companion.net.api

import kotlinx.coroutines.CancellationException
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

        data class Terminal(val reason: String) : ConnectResult()
    }

    val incoming: Channel<Api.ApiMessage> = Channel(Channel.BUFFERED)

    private var readerJob: Job? = null

    suspend fun connect(): ConnectResult {
        transport.connect()
        transport.send(handshake.start())

        while (true) {
            val message = transport.receive()
                ?: return terminal("Connection closed during handshake")

            when (val result = handshake.handle(message)) {
                is ApiHandshake.Result.Send -> transport.send(result.message)
                is ApiHandshake.Result.Ready -> {
                    startReadyReader()
                    return ConnectResult.Ready(
                        serverHello = result.serverHello,
                        pairedCredentials = result.pairedCredentials
                    )
                }
                is ApiHandshake.Result.Terminal -> return terminal(result.reason)
            }
        }
    }

    suspend fun send(message: Api.ApiMessage) {
        handshake.requireReadyForReports()
        transport.send(message)
    }

    override fun close() {
        readerJob?.cancel()
        readerJob = null
        transport.close()
        incoming.close()
    }

    private fun startReadyReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                while (isActive) {
                    val message = transport.receive() ?: break
                    if (message.payloadCase == Api.ApiMessage.PayloadCase.AUTH_REJECT ||
                        message.payloadCase == Api.ApiMessage.PayloadCase.ERROR
                    ) {
                        transport.close()
                        break
                    }
                    incoming.send(message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                incoming.close(error)
                return@launch
            }
            incoming.close()
        }
    }

    private fun terminal(reason: String): ConnectResult.Terminal {
        transport.close()
        incoming.close()
        return ConnectResult.Terminal(reason)
    }
}
