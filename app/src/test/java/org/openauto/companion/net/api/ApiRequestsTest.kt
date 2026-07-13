package org.openauto.companion.net.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common

class ApiRequestsTest {
    @Test
    fun subscribeSystem_rejectsRequestIdZero() {
        assertThrows(IllegalArgumentException::class.java) {
            ApiRequests.subscribeSystem(requestId = 0)
        }
    }

    @Test
    fun subscribeSystem_usesNonzeroIdAndOnlySystemTopic() {
        val message = ApiRequests.subscribeSystem(requestId = 41)

        assertEquals(41L, message.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.SUBSCRIBE_REQUEST, message.payloadCase)
        assertEquals(listOf(Common.Topic.TOPIC_SYSTEM), message.subscribeRequest.topicsList)
    }
}
