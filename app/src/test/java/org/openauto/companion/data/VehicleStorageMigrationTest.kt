package org.openauto.companion.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStorageMigrationTest {
    @Test
    fun planDeletesLegacyRecordsAndPreservesCompleteV1Records() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("ssid", "OldAP")
            .put("shared_secret", "legacy-secret")
        val valid = validV1Json()
            .put("id", "v1")
            .put("name", "My Car")
            .put("server_id", "server-1")
            .put("settings_host", "10.0.0.42")
            .put("settings_port", 8080)
            .put("display_width", 1280)
            .put("display_height", 720)
            .put("socks5_enabled", false)
            .put("audio_keep_alive", true)

        val plan = VehicleStorageMigration.plan(
            storedVersion = 0,
            rawVehiclesJson = JSONArray().put(legacy).put(valid).toString()
        )!!

        val survivor = VehicleStorageMigration.activeVehicles(plan.vehiclesJson).single()
        assertEquals("v1", survivor.id)
        assertEquals("My Car", survivor.name)
        assertEquals("server-1", survivor.serverId)
        assertEquals("10.0.0.42", survivor.settingsHost)
        assertEquals(8080, survivor.settingsPort)
        assertEquals(1280, survivor.displayWidth)
        assertEquals(720, survivor.displayHeight)
        assertEquals(false, survivor.socks5Enabled)
        assertEquals(true, survivor.audioKeepAlive)
        assertEquals(VehicleStorageMigration.CURRENT_VERSION, plan.targetVersion)
        assertEquals(VehicleStorageMigration.LEGACY_KEYS, plan.keysToRemove)
    }

    @Test
    fun activeFilteringRejectsMissingClientIdAndMalformedSecrets() {
        val missingClient = validV1Json().remove("api_client_id")
        val blankClient = validV1Json().put("api_client_id", "  ")
        val nonHex = validV1Json().put("api_secret_hex", "zz".repeat(32))
        val shortSecret = validV1Json().put("api_secret_hex", "aa".repeat(31))
        val valid = validV1Json().put("ssid", "Survivor")

        val result = VehicleStorageMigration.activeVehicles(
            JSONArray()
                .put(missingClient)
                .put(blankClient)
                .put(nonHex)
                .put(shortSecret)
                .put(valid)
                .toString()
        )

        assertEquals(listOf("Survivor"), result.map { it.ssid })
    }

    @Test
    fun malformedIndividualEntriesDoNotDiscardValidSiblings() {
        val malformedObject = JSONObject().put("ssid", 42)
        val raw = JSONArray()
            .put("not an object")
            .put(malformedObject)
            .put(JSONObject().put("api_mode", "external_api_v1"))
            .put(validV1Json().put("ssid", "GoodAP"))
            .toString()

        val result = VehicleStorageMigration.activeVehicles(raw)

        assertEquals(listOf("GoodAP"), result.map { it.ssid })
        assertTrue(VehicleStorageMigration.activeVehicles("not-json").isEmpty())
    }

    @Test
    fun completedMarkerSkipsPurgeSoLaterV1VehiclesRemainUntouched() {
        assertNull(
            VehicleStorageMigration.plan(
                storedVersion = VehicleStorageMigration.CURRENT_VERSION,
                rawVehiclesJson = JSONArray().put(validV1Json()).toString()
            )
        )
    }

    @Test
    fun failedCommitCanRetryWhileRuntimeStillExposesOnlyValidV1Vehicles() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("ssid", "LegacyAP")
                    .put("shared_secret", "legacy")
            )
            .put(validV1Json().put("ssid", "ValidAP"))
            .toString()

        val firstPlan = VehicleStorageMigration.plan(0, raw)!!
        val retryPlan = VehicleStorageMigration.plan(0, raw)!!

        assertEquals(firstPlan, retryPlan)
        assertEquals(listOf("ValidAP"), VehicleStorageMigration.activeVehicles(raw).map { it.ssid })
    }

    private fun validV1Json(): JSONObject = JSONObject()
        .put("ssid", "ProdigyAP")
        .put("name", "Prodigy")
        .put("shared_secret", "")
        .put("api_mode", "external_api_v1")
        .put("api_client_id", "client-123")
        .put("api_secret_hex", "ab".repeat(32))
}
