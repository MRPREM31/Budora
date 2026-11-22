package com.budora.ble

import android.os.ParcelUuid

object FastPairPayload {
    const val FAST_PAIR_SERVICE_UUID_STRING = "0000FE2C-0000-1000-8000-00805F9B34FB"

    val FAST_PAIR_SERVICE_UUID: ParcelUuid by lazy {
        ParcelUuid.fromString(FAST_PAIR_SERVICE_UUID_STRING)
    }

    /**
     * Parses Fast Pair battery advertisement (Service Data for 0xFE2C).
     *
     * Google Fast Pair specification:
     * - Header byte: length and type (0bLLLLTTTT)
     *   L = 1..3 (number of components: left, right, case)
     *   T = 3 (Show UI) or 4 (Hide UI)
     * - Followed by 1-3 component bytes:
     *   Bit 7: charging status (1 = charging, 0 = discharging)
     *   Bits 0-6: battery level (0-100, 0x7F = unknown)
     */
    fun parse(data: ByteArray): BudoraState? {
        if (data.size < 2) return null

        data class Candidate(
            val state: BudoraState,
            val isExactEnd: Boolean,
            val type: Int,
            val index: Int,
        )

        val candidates = mutableListOf<Candidate>()

        for (i in 0 until data.size - 1) {
            val header = data[i].toInt() and 0xFF
            val numValues = (header shr 4) and 0x0F
            val type = header and 0x0F

            // Type 3 = Show UI, Type 4 = Hide UI
            if (numValues in 1..3 && (type == 3 || type == 4) && i + numValues < data.size) {
                val leftRaw = data[i + 1].toInt() and 0xFF
                val rightRaw = if (numValues >= 2) data[i + 2].toInt() and 0xFF else null
                val caseRaw = if (numValues >= 3) data[i + 3].toInt() and 0xFF else null

                val leftVal = leftRaw and 0x7F
                val rightVal = rightRaw?.let { it and 0x7F }
                val caseVal = caseRaw?.let { it and 0x7F }

                // In Fast Pair, 0x7F means unknown/not reported.
                // Valid battery levels are 0..100. Values > 100 (other than 0x7F) indicate invalid data.
                val leftValid = leftVal == 0x7F || leftVal in 0..100
                val rightValid = rightVal == null || rightVal == 0x7F || rightVal in 0..100
                val caseValid = caseVal == null || caseVal == 0x7F || caseVal in 0..100

                if (!leftValid || !rightValid || !caseValid) continue

                val leftBattery = if (leftVal in 0..100) leftVal else null
                val rightBattery = if (rightVal != null && rightVal in 0..100) rightVal else null
                val caseBattery = if (caseVal != null && caseVal in 0..100) caseVal else null

                val isLeftCharging = (leftRaw and 0x80) != 0
                val isRightCharging = (rightRaw?.let { (it and 0x80) != 0 }) ?: false
                val isCaseCharging = (caseRaw?.let { (it and 0x80) != 0 }) ?: false

                // Ignore uninitialized dummy frames reporting all zeroes with no charging
                val hasRealReading = (leftBattery != null && leftBattery > 0) ||
                    (rightBattery != null && rightBattery > 0) ||
                    (caseBattery != null && caseBattery > 0) ||
                    (isLeftCharging || isRightCharging || isCaseCharging)

                if (hasRealReading) {

                    val isExactEnd = (i + 1 + numValues == data.size)

                    candidates.add(
                        Candidate(
                            state = BudoraState(
                                model = BudoraModel.GENERIC_BLUETOOTH,
                                leftBattery = leftBattery,
                                rightBattery = rightBattery,
                                caseBattery = caseBattery,
                                isLeftCharging = isLeftCharging,
                                isRightCharging = isRightCharging,
                                isCaseCharging = isCaseCharging,
                                isLidOpen = isCaseCharging || (caseBattery != null && caseBattery > 0),
                                lidOpenCounter = 0,
                                rawLeftInEar = false,
                                rawRightInEar = false,
                            ),
                            isExactEnd = isExactEnd,
                            type = type,
                            index = i,
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        // Ranking priority:
        // 1. Matches exact end of packet (standard Fast Pair TLV tail)
        // 2. Type == 3 (Show UI) over Type == 4 (Hide UI)
        // 3. Later index in packet (past Model ID / Account Key filter)
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.isExactEnd }
                .thenBy { it.type == 3 }
                .thenBy { it.index }
        )

        return best?.state
    }
}
