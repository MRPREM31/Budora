package com.budora.ble

/**
 * Decides which nearby AirPods are *yours* when several are broadcasting.
 *
 * This is necessary because the beacon does not identify a specific pair.
 * AirPods use resolvable private addresses that rotate roughly every 15
 * minutes, and the IRK needed to resolve them is only obtainable through the
 * AAP handshake we deliberately do not perform. Two identical Gen 2 pairs are
 * indistinguishable by model ID alone.
 *
 * So identity is heuristic: pick the strongest signal, but make the incumbent
 * sticky so a passing stranger on a train does not hijack the readout. A rival
 * must beat the current pick by [SWITCH_MARGIN_DB] to take over. The real
 * guarantee comes from upstream -- the service only scans while your bonded
 * AirPods are connected over classic Bluetooth.
 *
 * Not thread-safe; call from a single scan callback thread.
 */
class BatteryPredictor(private val alpha: Float = 0.35f) {
    private class Channel(
        val samples: MutableList<Pair<Long, Int>> = mutableListOf(),
        var smoothedRate: Float = 18f,
        var lastCharging: Boolean = false,
    )
    private val channels = mutableMapOf<String, Channel>()

    fun predict(channelId: String, level: Int?, isCharging: Boolean, nowMs: Long = System.currentTimeMillis()): String? {
        if (level == null || level <= 0) return null
        if (isCharging) {
            val missing = (100 - level).coerceAtLeast(0)
            if (missing <= 2) return "⚡ Fully Charged"
            val mins = ((missing / 100f) * 55).toInt().coerceAtLeast(5)
            return if (mins >= 60) {
                val h = mins / 60
                val m = mins % 60
                if (m > 0) "⚡ Full in ~${h}h ${m}m" else "⚡ Full in ~${h}h"
            } else {
                "⚡ Full in ~${mins}m"
            }
        }
        val ch = channels.getOrPut(channelId) { Channel() }
        if (ch.lastCharging) {
            ch.samples.clear()
            ch.smoothedRate = 18f
        }
        ch.lastCharging = false

        if (ch.samples.isEmpty() || ch.samples.last().second != level || (nowMs - ch.samples.last().first) > 60_000L) {
            ch.samples.add(nowMs to level)
            if (ch.samples.size > 20) ch.samples.removeAt(0)
        }

        if (ch.samples.size >= 2) {
            val dtHours = (ch.samples.last().first - ch.samples.first().first) / 3_600_000f
            val db = ch.samples.first().second - ch.samples.last().second
            if (dtHours > (45f / 3600f) && db > 0) {
                val instantRate = (db / dtHours).coerceIn(2f, 50f)
                ch.smoothedRate = alpha * instantRate + (1f - alpha) * ch.smoothedRate
            }
        }

        val rate = ch.smoothedRate.coerceAtLeast(4f)
        val hoursLeft = level / rate
        val totalMinutes = (hoursLeft * 60).toInt()

        return when {
            totalMinutes < 15 -> "Low (<15m)"
            totalMinutes < 60 -> "~ ${totalMinutes}m left"
            else -> {
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                if (m < 5) "~ ${h}h left" else "~ ${h}h ${m}m left"
            }
        }
    }
}

class BudoraTracker(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private class Candidate(
        val address: String,
        var rssi: Int,
        var state: BudoraState,
        var lastSeenMs: Long,
    )

    private val candidates = mutableMapOf<String, Candidate>()
    private var incumbentAddress: String? = null
    private val predictor = BatteryPredictor()

    fun predict(channelId: String, level: Int?, isCharging: Boolean): String? {
        return predictor.predict(channelId, level, isCharging, nowMs())
    }

    /**
     * Record one decoded advertisement.
     *
     * @return the state of the pair we believe is yours, or null if nothing is
     *   currently credible.
     */
    fun onAdvertisement(address: String, rssi: Int, state: BudoraState): BudoraState? {
        val now = nowMs()

        val enriched = state.copy(
            leftTimeRemaining = predictor.predict("${address}_L", state.leftBattery, state.isLeftCharging, now),
            rightTimeRemaining = predictor.predict("${address}_R", state.rightBattery, state.isRightCharging, now),
            singleTimeRemaining = if (state.isSingleDevice) {
                val lvl = state.leftBattery ?: state.rightBattery
                val chg = state.isLeftCharging || state.isRightCharging
                predictor.predict("${address}_S", lvl, chg, now)
            } else null
        )

        candidates[address]?.also {
            it.rssi = rssi
            it.state = enriched
            it.lastSeenMs = now
        } ?: run {
            candidates[address] = Candidate(address, rssi, enriched, now)
        }

        expireStale(now)
        return select()
    }

    /** Drop the incumbent and all candidates, e.g. on disconnect. */
    fun reset() {
        candidates.clear()
        incumbentAddress = null
    }

    /** Re-evaluate without a new advertisement, expiring anything stale. */
    fun current(): BudoraState? {
        expireStale(nowMs())
        return select()
    }

    private fun expireStale(now: Long) {
        candidates.entries.removeAll { now - it.value.lastSeenMs > CANDIDATE_TTL_MS }
        if (incumbentAddress !in candidates) incumbentAddress = null
    }

    private fun select(): BudoraState? {
        val strongest = candidates.values.maxByOrNull { it.rssi } ?: return null
        val incumbent = incumbentAddress?.let { candidates[it] }

        // No incumbent, or a rival clears it by the hysteresis margin.
        if (incumbent == null || strongest.rssi > incumbent.rssi + SWITCH_MARGIN_DB) {
            incumbentAddress = strongest.address
            return strongest.state
        }
        return incumbent.state
    }

    companion object {
        /**
         * How much stronger a rival must be to displace the incumbent. Roughly
         * a doubling of apparent closeness; small enough that genuinely moving
         * to your own pods wins, large enough that RSSI jitter does not.
         */
        const val SWITCH_MARGIN_DB = 8

        /** Forget a pair we have not heard from for this long. */
        const val CANDIDATE_TTL_MS = 15_000L
    }
}
