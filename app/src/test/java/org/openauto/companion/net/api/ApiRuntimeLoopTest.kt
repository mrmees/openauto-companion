package org.openauto.companion.net.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common
import prodigy.api.v1.System as SystemProto

class ApiRuntimeLoopTest {
    @Test
    fun transientDisconnectsRetryInSequenceAndCloseBeforeReplacement() = runTest {
        var activeClients = 0
        var maxActiveClients = 0
        val clients = ArrayDeque(
            listOf(
                FakeRuntimeClient(
                    connectResult = ApiSessionClient.ConnectResult.Disconnected("early EOF"),
                    onConnect = {
                        activeClients += 1
                        maxActiveClients = maxOf(maxActiveClients, activeClients)
                    },
                    onClose = { activeClients -= 1 }
                ),
                FakeRuntimeClient(
                    connectResult = ApiSessionClient.ConnectResult.Disconnected("socket reset"),
                    onConnect = {
                        activeClients += 1
                        maxActiveClients = maxOf(maxActiveClients, activeClients)
                    },
                    onClose = { activeClients -= 1 }
                ),
                FakeRuntimeClient(
                    connectResult = ApiSessionClient.ConnectResult.Rejected("unknown client"),
                    onConnect = {
                        activeClients += 1
                        maxActiveClients = maxOf(maxActiveClients, activeClients)
                    },
                    onClose = { activeClients -= 1 }
                )
            )
        )
        val retryAttempts = mutableListOf<Int>()

        val exit = runtime(
            clientFactory = { clients.removeFirst() },
            retryDelay = {
                assertEquals(0, activeClients)
                retryAttempts += it
            }
        ).run()

        assertEquals(listOf(0, 1), retryAttempts)
        assertEquals(1, maxActiveClients)
        assertEquals(0, activeClients)
        assertEquals(ApiRuntimeLoop.Exit.RePairRequired("unknown client"), exit)
    }

    @Test
    fun readySessionResetsBackoffBeforeNextTransientFailure() = runTest {
        val clients = ArrayDeque(
            listOf(
                disconnectedClient("first"),
                disconnectedClient("second"),
                readyClient(readyClose = ApiSessionClient.ReadyClose.PeerClosed("gone")),
                rejectedClient("re-pair")
            )
        )
        val retryAttempts = mutableListOf<Int>()

        runtime(
            clientFactory = { clients.removeFirst() },
            retryDelay = { retryAttempts += it }
        ).run()

        assertEquals(listOf(0, 1, 0), retryAttempts)
    }

    @Test
    fun explicitRejectionStopsWithoutRetryAndReportsRepairState() = runTest {
        val states = mutableListOf<ApiRuntimeLoop.State>()
        var factoryCalls = 0

        val exit = runtime(
            clientFactory = {
                factoryCalls += 1
                rejectedClient("authentication rejected")
            },
            onStateChanged = { states += it }
        ).run()

        assertEquals(1, factoryCalls)
        assertEquals(ApiRuntimeLoop.Exit.RePairRequired("authentication rejected"), exit)
        assertEquals(
            ApiRuntimeLoop.State.RePairRequired("authentication rejected"),
            states.last()
        )
    }

    @Test
    fun nonAuthHandshakeErrorRetriesBeforeAuthRejectionRequiresRepair() = runTest {
        val clients = ArrayDeque(
            listOf(
                rejectedClient(
                    "unsupported version",
                    Common.ErrorCode.ERROR_CODE_UNSUPPORTED_VERSION
                ),
                rejectedClient("unknown client")
            )
        )
        val retryAttempts = mutableListOf<Int>()

        val exit = runtime(
            clientFactory = { clients.removeFirst() },
            retryDelay = { retryAttempts += it }
        ).run()

        assertEquals(listOf(0), retryAttempts)
        assertEquals(ApiRuntimeLoop.Exit.RePairRequired("unknown client"), exit)
    }

    @Test
    fun rejectionAfterReadyStopsWithoutRetry() = runTest {
        var factoryCalls = 0
        var retryCalls = 0

        val exit = runtime(
            clientFactory = {
                factoryCalls += 1
                readyClient(
                    readyClose = ApiSessionClient.ReadyClose.Rejected("credentials revoked")
                )
            },
            retryDelay = { retryCalls += 1 }
        ).run()

        assertEquals(1, factoryCalls)
        assertEquals(0, retryCalls)
        assertEquals(ApiRuntimeLoop.Exit.RePairRequired("credentials revoked"), exit)
    }

    @Test
    fun nonAuthErrorAfterReadyRetriesBeforeAuthRejectionRequiresRepair() = runTest {
        val clients = ArrayDeque(
            listOf(
                readyClient(
                    readyClose = ApiSessionClient.ReadyClose.Rejected(
                        "temporary server fault",
                        Common.ErrorCode.ERROR_CODE_INTERNAL
                    )
                ),
                rejectedClient("credentials revoked")
            )
        )
        val retryAttempts = mutableListOf<Int>()

        val exit = runtime(
            clientFactory = { clients.removeFirst() },
            retryDelay = { retryAttempts += it }
        ).run()

        assertEquals(listOf(0), retryAttempts)
        assertEquals(ApiRuntimeLoop.Exit.RePairRequired("credentials revoked"), exit)
    }

    @Test
    fun storedServerIdentityMismatchIsPermanent() = runTest {
        val client = readyClient(serverId = "different-server")
        var publisherSessions = 0

        val exit = runtime(
            clientFactory = { client },
            storedServerId = "expected-server",
            reportPublisher = countingPublisher { publisherSessions += 1 }
        ).run()

        assertEquals(
            ApiRuntimeLoop.Exit.IdentityMismatch("expected-server", "different-server"),
            exit
        )
        assertEquals(0, publisherSessions)
        assertTrue(client.closed)
    }

    @Test
    fun missingOptionalServerIdentityIsToleratedAndReadySessionProcessesSystemStream() = runTest {
        val systemStatus = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(1280)
            .setDisplayHeight(720)
            .build()
        val subscriptionRejected = Api.ApiMessage.newBuilder()
            .setRequestId(1)
            .setSubscribeResponse(
                Api.SubscribeResponse.newBuilder()
                    .addResults(
                        Api.TopicSubscriptionResult.newBuilder()
                            .setTopic(Common.Topic.TOPIC_SYSTEM)
                            .setAccepted(false)
                            .setReason("temporarily unavailable")
                    )
            )
            .build()
        val statusMessage = Api.ApiMessage.newBuilder()
            .setRequestId(0)
            .setSystemStatus(systemStatus)
            .build()
        val ready = readyClient(
            serverId = null,
            incomingMessages = listOf(subscriptionRejected, statusMessage),
            readyClose = ApiSessionClient.ReadyClose.PeerClosed("gone")
        )
        val rejected = rejectedClient("stop")
        val clients = ArrayDeque(listOf(ready, rejected))
        val persisted = mutableListOf<SystemProto.SystemStatus>()
        val states = mutableListOf<ApiRuntimeLoop.State>()

        runtime(
            clientFactory = { clients.removeFirst() },
            storedServerId = "expected-server",
            persistSystemStatus = { persisted += it },
            onStateChanged = { states += it }
        ).run()

        assertEquals(listOf(systemStatus), persisted)
        assertTrue(states.any { it is ApiRuntimeLoop.State.Ready })
        assertEquals(
            listOf(
                Api.ApiMessage.PayloadCase.CONNECTIVITY_REPORT,
                Api.ApiMessage.PayloadCase.TIME_REPORT,
                Api.ApiMessage.PayloadCase.SUBSCRIBE_REQUEST
            ),
            ready.sent.map { it.payloadCase }
        )
        assertEquals(1L, ready.sent.last().requestId)
    }

    @Test
    fun readyCloseStopsPublisherBeforeReconnect() = runTest {
        val publisherStopped = CompletableDeferred<Unit>()
        val publisher = object : ReadyReportPublisher {
            override suspend fun runReadySession(
                send: suspend (Api.ApiMessage) -> Unit,
                afterInitialReports: suspend () -> Unit
            ) {
                try {
                    afterInitialReports()
                    CompletableDeferred<Unit>().await()
                } finally {
                    publisherStopped.complete(Unit)
                }
            }
        }
        val ready = readyClient(readyClose = ApiSessionClient.ReadyClose.PeerClosed("gone"))
        var secondCreatedAfterPublisherStop = false
        var factoryCalls = 0

        runtime(
            clientFactory = {
                factoryCalls += 1
                if (factoryCalls == 1) {
                    ready
                } else {
                    secondCreatedAfterPublisherStop = publisherStopped.isCompleted
                    rejectedClient("stop")
                }
            },
            reportPublisher = publisher
        ).run()

        assertTrue(secondCreatedAfterPublisherStop)
    }

    @Test
    fun cancellationClosesActiveClientAndExits() = runTest {
        val readyReached = CompletableDeferred<Unit>()
        val client = readyClient(
            readyClose = null,
            closeIncomingImmediately = false
        )
        val loop = runtime(
            clientFactory = { client },
            onStateChanged = {
                if (it is ApiRuntimeLoop.State.Ready) readyReached.complete(Unit)
            }
        )

        val job = backgroundScope.launch { loop.run() }
        readyReached.await()
        job.cancelAndJoin()

        assertTrue(client.closed)
        assertTrue(job.isCancelled)
    }

    @Test
    fun systemStatusWithoutPositiveDimensionsIsIgnored() = runTest {
        val invalidStatus = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(0)
            .setDisplayHeight(720)
            .build()
        val ready = readyClient(
            incomingMessages = listOf(
                Api.ApiMessage.newBuilder().setSystemStatus(invalidStatus).build()
            ),
            readyClose = ApiSessionClient.ReadyClose.PeerClosed("gone")
        )
        val clients = ArrayDeque(listOf(ready, rejectedClient("stop")))
        var persisted = false

        runtime(
            clientFactory = { clients.removeFirst() },
            persistSystemStatus = { persisted = true }
        ).run()

        assertFalse(persisted)
    }

    private fun runtime(
        clientFactory: () -> ApiRuntimeClient,
        storedServerId: String? = null,
        reportPublisher: ReadyReportPublisher = countingPublisher(),
        retryDelay: suspend (Int) -> Unit = {},
        onStateChanged: (ApiRuntimeLoop.State) -> Unit = {},
        persistSystemStatus: (SystemProto.SystemStatus) -> Unit = {}
    ) = ApiRuntimeLoop(
        clientFactory = clientFactory,
        reportPublisher = reportPublisher,
        storedServerId = storedServerId,
        retryDelay = retryDelay,
        onStateChanged = onStateChanged,
        persistSystemStatus = persistSystemStatus
    )

    private fun countingPublisher(onRun: () -> Unit = {}): ReadyReportPublisher {
        val delegate = ApiReportPublisher(
            currentTimeMs = { 1_765_000_000_000L },
            timezoneId = { "America/Chicago" },
            elapsedRealtimeMs = { 1_000L }
        )
        return object : ReadyReportPublisher {
            override suspend fun runReadySession(
                send: suspend (Api.ApiMessage) -> Unit,
                afterInitialReports: suspend () -> Unit
            ) {
                onRun()
                delegate.runReadySession(send, afterInitialReports)
            }
        }
    }

    private fun disconnectedClient(reason: String) = FakeRuntimeClient(
        connectResult = ApiSessionClient.ConnectResult.Disconnected(reason)
    )

    private fun rejectedClient(
        reason: String,
        errorCode: Common.ErrorCode? = null
    ) = FakeRuntimeClient(
        connectResult = ApiSessionClient.ConnectResult.Rejected(reason, errorCode)
    )

    private fun readyClient(
        serverId: String? = "server-1",
        incomingMessages: List<Api.ApiMessage> = emptyList(),
        readyClose: ApiSessionClient.ReadyClose? = ApiSessionClient.ReadyClose.PeerClosed("gone"),
        closeIncomingImmediately: Boolean = true
    ): FakeRuntimeClient {
        val hello = Api.ServerHello.newBuilder()
            .setApiVersionMajor(1)
            .setApiVersionMinor(1)
            .setServerName("Prodigy")
            .setAppVersion("test")
            .setSessionId("session")
            .setCapabilities(Api.Capabilities.getDefaultInstance())
            .apply { if (serverId != null) setServerId(serverId) }
            .build()
        return FakeRuntimeClient(
            connectResult = ApiSessionClient.ConnectResult.Ready(hello, null),
            incomingMessages = incomingMessages,
            readyClose = readyClose,
            closeIncomingImmediately = closeIncomingImmediately
        )
    }

    private class FakeRuntimeClient(
        private val connectResult: ApiSessionClient.ConnectResult,
        incomingMessages: List<Api.ApiMessage> = emptyList(),
        readyClose: ApiSessionClient.ReadyClose? = null,
        closeIncomingImmediately: Boolean = true,
        private val onConnect: () -> Unit = {},
        private val onClose: () -> Unit = {}
    ) : ApiRuntimeClient {
        override val incoming = Channel<Api.ApiMessage>(Channel.UNLIMITED)
        val sent = mutableListOf<Api.ApiMessage>()
        var closed = false
            private set
        private val closeResult = CompletableDeferred<ApiSessionClient.ReadyClose>()

        init {
            incomingMessages.forEach { incoming.trySend(it).getOrThrow() }
            if (closeIncomingImmediately) incoming.close()
            if (readyClose != null) closeResult.complete(readyClose)
        }

        override suspend fun connect(): ApiSessionClient.ConnectResult {
            onConnect()
            return connectResult
        }

        override suspend fun sendReadyMessage(message: Api.ApiMessage) {
            sent += message
        }

        override suspend fun awaitClosed(): ApiSessionClient.ReadyClose = closeResult.await()

        override fun close() {
            if (closed) return
            closed = true
            incoming.close()
            closeResult.complete(ApiSessionClient.ReadyClose.ClientClosed)
            onClose()
        }
    }
}
