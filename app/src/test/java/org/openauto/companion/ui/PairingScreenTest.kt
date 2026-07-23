package org.openauto.companion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingScreenTest {
    @Test
    fun manualPairingRequiresSsidAndCompleteSecureCodeWhileIdle() {
        val groupedLowercaseCode = "abcd efgh-ijkl mnop-qrst uvwx"

        assertTrue(canSubmitManualPairing(" ProdigyAP ", groupedLowercaseCode, false))
        assertFalse(canSubmitManualPairing(" ", groupedLowercaseCode, false))
        assertFalse(canSubmitManualPairing("ProdigyAP", "ABCDEFGHIJKLMNOPQRSTUVW", false))
        assertFalse(canSubmitManualPairing("ProdigyAP", groupedLowercaseCode, true))
    }
}
