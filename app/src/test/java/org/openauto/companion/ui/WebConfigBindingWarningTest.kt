package org.openauto.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openauto.companion.net.ProcessNetworkBinding

class WebConfigBindingWarningTest {
    @Test
    fun messageFor_returnsMissingNetworkWarning() {
        val message = WebConfigBindingWarning.messageFor(ProcessNetworkBinding.Result.NoNetwork)

        assertEquals(
            "Head-unit Wi-Fi route is not available. Android may route this page over cellular.",
            message
        )
    }

    @Test
    fun messageFor_returnsFailedBindingWarning() {
        val message = WebConfigBindingWarning.messageFor(
            ProcessNetworkBinding.Result.Failed(IllegalStateException("bind failed"))
        )

        assertEquals(
            "Could not bind web config to head-unit Wi-Fi. Android may route this page over cellular.",
            message
        )
    }
}
