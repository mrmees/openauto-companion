package org.openauto.companion.net.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import prodigy.api.v1.Api
import prodigy.api.v1.Common
import prodigy.api.v1.System as SystemProto

interface ApiRuntimeClient : AutoCloseable {
    val incoming: ReceiveChannel<Api.ApiMessage>

    suspend fun connect(): ApiSessionClient.ConnectResult

    suspend fun sendReadyMessage(message: Api.ApiMessage)

    suspend fun awaitClosed(): ApiSessionClient.ReadyClose
}

interface ReadyReportPublisher {
    suspend fun runReadySession(
        send: suspend (Api.ApiMessage) -> Unit,
        afterInitialReports: suspend () -> Unit = {}
    )
}

class ApiRuntimeLoop(
    private val clientFactory: () -> ApiRuntimeClient,
    private val reportPublisher: ReadyReportPublisher,
    private val storedServerId: String?,
    private val retryDelay: suspend (attempt: Int) -> Unit,
    private val onStateChanged: (State) -> Unit = {},
    private val persistSystemStatus: (SystemProto.SystemStatus) -> Unit = {},
    private val subscriptionRequestId: Long = 1L
) {
    sealed interface State {
        data class Connecting(val attempt: Int) : State
        data class Ready(val serverHello: Api.ServerHello) : State
        data class WaitingToRetry(val attempt: Int, val reason: String) : State
        data class RePairRequired(val reason: String) : State
        data class IdentityMismatch(val expected: String, val actual: String) : State
    }

    sealed interface Exit {
        data class RePairRequired(val reason: String) : Exit
        data class IdentityMismatch(val expected: String, val actual: String) : Exit
    }

    private sealed interface AttemptOutcome {
        data class Retry(val reason: String) : AttemptOutcome
        data class RePairRequired(val reason: String) : AttemptOutcome
        data class IdentityMismatch(val expected: String, val actual: String) : AttemptOutcome
    }

    init {
        require(subscriptionRequestId != 0L) { "Subscription request id must be nonzero" }
    }

    suspend fun run(): Exit {
        var retryAttempt = 0

        while (true) {
            onStateChanged(State.Connecting(retryAttempt))
            var client: ApiRuntimeClient? = null

            val outcome = try {
                try {
                    client = clientFactory()
                    when (val connectResult = client.connect()) {
                        is ApiSessionClient.ConnectResult.Disconnected -> {
                            AttemptOutcome.Retry(connectResult.reason)
                        }

                        is ApiSessionClient.ConnectResult.Rejected -> {
                            if (connectResult.errorCode.requiresRepair()) {
                                AttemptOutcome.RePairRequired(connectResult.reason)
                            } else {
                                AttemptOutcome.Retry(connectResult.reason)
                            }
                        }

                        is ApiSessionClient.ConnectResult.Ready -> {
                            val mismatch = identityMismatch(connectResult.serverHello)
                            if (mismatch != null) {
                                mismatch
                            } else {
                                retryAttempt = 0
                                onStateChanged(State.Ready(connectResult.serverHello))
                                when (val close = runReadySession(client)) {
                                    is ApiSessionClient.ReadyClose.Rejected -> {
                                        if (close.errorCode.requiresRepair()) {
                                            AttemptOutcome.RePairRequired(close.reason)
                                        } else {
                                            AttemptOutcome.Retry(close.reason)
                                        }
                                    }

                                    is ApiSessionClient.ReadyClose.Failed -> {
                                        AttemptOutcome.Retry(
                                            close.error.message ?: "API session failed"
                                        )
                                    }

                                    is ApiSessionClient.ReadyClose.PeerClosed -> {
                                        AttemptOutcome.Retry(close.reason)
                                    }

                                    ApiSessionClient.ReadyClose.ClientClosed -> {
                                        AttemptOutcome.Retry("API session closed")
                                    }
                                }
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    AttemptOutcome.Retry(error.message ?: "API session failed")
                }
            } finally {
                client?.close()
            }

            when (outcome) {
                is AttemptOutcome.RePairRequired -> {
                    return rePairRequired(outcome.reason)
                }

                is AttemptOutcome.IdentityMismatch -> {
                    onStateChanged(State.IdentityMismatch(outcome.expected, outcome.actual))
                    return Exit.IdentityMismatch(outcome.expected, outcome.actual)
                }

                is AttemptOutcome.Retry -> {
                    waitToRetry(retryAttempt, outcome.reason)
                    retryAttempt += 1
                }
            }
        }
    }

    private suspend fun runReadySession(
        client: ApiRuntimeClient
    ): ApiSessionClient.ReadyClose = coroutineScope {
        val publisherJob = launch(start = CoroutineStart.UNDISPATCHED) {
            reportPublisher.runReadySession(
                send = client::sendReadyMessage,
                afterInitialReports = {
                    client.sendReadyMessage(ApiRequests.subscribeSystem(subscriptionRequestId))
                }
            )
        }
        val incomingJob = launch(start = CoroutineStart.UNDISPATCHED) {
            for (message in client.incoming) {
                handleIncoming(message)
            }
        }

        try {
            val close = client.awaitClosed()
            incomingJob.join()
            close
        } finally {
            publisherJob.cancelAndJoin()
            incomingJob.cancelAndJoin()
        }
    }

    private fun handleIncoming(message: Api.ApiMessage) {
        if (message.payloadCase != Api.ApiMessage.PayloadCase.SYSTEM_STATUS) return

        val status = message.systemStatus
        if (!status.hasDisplayWidth() || !status.hasDisplayHeight()) return
        if (status.displayWidth <= 0 || status.displayHeight <= 0) return
        persistSystemStatus(status)
    }

    private fun identityMismatch(
        serverHello: Api.ServerHello
    ): AttemptOutcome.IdentityMismatch? {
        val expected = storedServerId?.takeIf { it.isNotBlank() } ?: return null
        val actual = if (serverHello.hasServerId()) {
            serverHello.serverId.takeIf { it.isNotBlank() }
        } else {
            null
        } ?: return null

        if (expected == actual) return null
        return AttemptOutcome.IdentityMismatch(expected, actual)
    }

    private fun rePairRequired(reason: String): Exit.RePairRequired {
        onStateChanged(State.RePairRequired(reason))
        return Exit.RePairRequired(reason)
    }

    private suspend fun waitToRetry(attempt: Int, reason: String) {
        onStateChanged(State.WaitingToRetry(attempt, reason))
        retryDelay(attempt)
    }

    private fun Common.ErrorCode?.requiresRepair(): Boolean =
        this == null ||
            this == Common.ErrorCode.ERROR_CODE_NOT_AUTHENTICATED ||
            this == Common.ErrorCode.ERROR_CODE_AUTH_FAILED
}
