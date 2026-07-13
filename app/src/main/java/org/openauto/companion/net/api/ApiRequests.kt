package org.openauto.companion.net.api

import prodigy.api.v1.Api
import prodigy.api.v1.Common

object ApiRequests {
    fun subscribeSystem(requestId: Long): Api.ApiMessage {
        require(requestId != 0L) { "Subscription request id must be nonzero" }
        return Api.ApiMessage.newBuilder()
            .setRequestId(requestId)
            .setSubscribeRequest(
                Api.SubscribeRequest.newBuilder()
                    .addTopics(Common.Topic.TOPIC_SYSTEM)
                    .build()
            )
            .build()
    }
}
