package com.budora.ble

/**
 * A decoded snapshot of one AirPods proximity-pairing advertisement.
 *
 * Battery levels are nullable by design. Apple encodes "unknown" as nibble 0xF
 * and AirPods emit it routinely -- a pod in the case with the lid shut reports
 * unknown, not zero. Callers must render null as "no reading", never as empty.
 */
data class BudoraState(
    val model: Int,
    val leftBattery: Int?,
    val rightBattery: Int?,
    val caseBattery: Int?,
    val isLeftCharging: Boolean,
    val isRightCharging: Boolean,
    val isCaseCharging: Boolean,
    val isLidOpen: Boolean,
    val lidOpenCounter: Int,
    /**
     * Raw in-ear bits, decoded for diagnostics only.
     *
     * These are known-unreliable: they report "in ear" with the buds sitting in
     * a shut case. LibrePods does not trust them either and sources ear
     * detection from the AAP channel instead. Log these; do not drive UI or
     * media control from them.
     */
    val rawLeftInEar: Boolean,
    val rawRightInEar: Boolean,
    val isSingleDevice: Boolean = false,
    val isLeftInCase: Boolean = false,
    val isRightInCase: Boolean = false,
    val leftTimeRemaining: String? = null,
    val rightTimeRemaining: String? = null,
    val caseTimeRemaining: String? = null,
    val singleTimeRemaining: String? = null,
) {
    val isKnownModel: Boolean get() = BudoraModel.isKnownModel(model)
}

object BudoraModel {
    /** AirPods (1st generation) */
    const val AIRPODS_GEN_1 = 0x0220
    /** AirPods (2nd generation, 2019) -- A2031 / A2032. */
    const val AIRPODS_GEN_2 = 0x0F20
    /** AirPods Pro (1st generation) */
    const val AIRPODS_PRO = 0x0E20
    /** AirPods (3rd generation) */
    const val AIRPODS_GEN_3 = 0x1320
    /** AirPods Pro (2nd generation) */
    const val AIRPODS_PRO_2 = 0x1420
    /** AirPods Max */
    const val AIRPODS_MAX = 0x0A20
    /** Generic Bluetooth Headset / Earbuds */
    const val GENERIC_BLUETOOTH = 0xFFFF

    fun isKnownModel(model: Int): Boolean = when (model) {
        AIRPODS_GEN_1, AIRPODS_GEN_2, AIRPODS_PRO, AIRPODS_GEN_3, AIRPODS_PRO_2, AIRPODS_MAX, GENERIC_BLUETOOTH -> true
        else -> (model and 0x00FF) == 0x0020
    }

    fun displayName(model: Int): String = when (model) {
        AIRPODS_GEN_1 -> "AirPods (1st generation)"
        AIRPODS_GEN_2 -> "AirPods (2nd generation)"
        AIRPODS_PRO -> "AirPods Pro"
        AIRPODS_GEN_3 -> "AirPods (3rd generation)"
        AIRPODS_PRO_2 -> "AirPods Pro (2nd generation)"
        AIRPODS_MAX -> "AirPods Max"
        GENERIC_BLUETOOTH -> "Wireless Earbuds"
        else -> "AirPods (0x%04X)".format(model)
    }
}
