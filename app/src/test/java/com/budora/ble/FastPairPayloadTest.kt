package com.budora.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FastPairPayloadTest {

    @Test
    fun parsesMultiComponentFastPairBatteryCorrectly() {
        // Captured live packet from CMF Buds 2a:
        // 00 40 00 : Model ID prefix
        // 34 41 20 11 55 : Account Key Filter / Salt (formerly falsely parsed as 65%, 32%, 17%)
        // 33 5A 5A 7F : Real battery block (Type 3 Show UI, 3 values: L=0x5A (90%), R=0x5A (90%), Case=0x7F (unknown))
        val rawHex = "0040003441201155335A5A7F"
        val bytes = rawHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        val state = FastPairPayload.parse(bytes)
        assertNotNull(state)
        assertEquals(90, state?.leftBattery)
        assertEquals(90, state?.rightBattery)
        assertNull(state?.caseBattery) // 0x7F must be null (unknown), NEVER 17!
    }

    @Test
    fun parsesStandardFastPairBatteryWithCase() {
        // 33 : 3 components, Type 3
        // 55 : Left 85%
        // 50 : Right 80%
        // 46 : Case 70%
        val bytes = byteArrayOf(0x33, 0x55, 0x50, 0x46)
        val state = FastPairPayload.parse(bytes)
        assertNotNull(state)
        assertEquals(85, state?.leftBattery)
        assertEquals(80, state?.rightBattery)
        assertEquals(70, state?.caseBattery)
    }
}
