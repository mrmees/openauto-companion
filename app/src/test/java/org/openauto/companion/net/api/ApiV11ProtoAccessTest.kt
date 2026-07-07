package org.openauto.companion.net.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Companion as CompanionProto
import prodigy.api.v1.System as SystemProto

class ApiV11ProtoAccessTest {
    @Test
    fun serverHello_exposesOptionalServerId() {
        val hello = Api.ServerHello.newBuilder()
            .setApiVersionMajor(1)
            .setApiVersionMinor(1)
            .setServerName("Prodigy")
            .setAppVersion("v1-test")
            .setSessionId("session-1")
            .setCapabilities(Api.Capabilities.getDefaultInstance())
            .setServerId("server-uuid-1")
            .build()

        assertTrue(hello.hasServerId())
        assertEquals("server-uuid-1", hello.serverId)
    }

    @Test
    fun timeReport_exposesOptionalTimezoneId() {
        val report = CompanionProto.TimeReport.newBuilder()
            .setUnixTimeMs(1_765_000_000_000L)
            .setTimezoneId("America/Chicago")
            .build()

        assertTrue(report.hasTimezoneId())
        assertEquals("America/Chicago", report.timezoneId)
    }

    @Test
    fun systemStatus_exposesOptionalDisplayDimensions() {
        val status = SystemProto.SystemStatus.newBuilder()
            .setDisplayWidth(1024)
            .setDisplayHeight(600)
            .build()

        assertTrue(status.hasDisplayWidth())
        assertTrue(status.hasDisplayHeight())
        assertEquals(1024, status.displayWidth)
        assertEquals(600, status.displayHeight)
    }
}
