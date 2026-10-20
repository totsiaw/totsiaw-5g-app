package com.totsiaw.nr5g.telephony

/** A snapshot of one SIM's connectivity + technical radio signal. */
data class SimSignal(
    val subId: Int,
    val slotIndex: Int,
    val carrier: String,
    val displayNetwork: String,   // user-facing: "5G NSA", "LTE+", "LTE", "No service"
    val overrideNetwork: String,  // raw: NR_NSA / NR_ADVANCED / LTE_CA / NONE
    // LTE
    val lteRsrp: Int? = null,
    val lteRsrq: Int? = null,
    val lteRssnr: Int? = null,
    val lteBand: String? = null,
    val lteEarfcn: Int? = null,
    val ltePci: Int? = null,
    // NR
    val nrSsRsrp: Int? = null,
    val nrSsRsrq: Int? = null,
    val nrSsSinr: Int? = null,
    val nrBand: String? = null,
    val nrArfcn: Int? = null,
    val nrPci: Int? = null,
    // State (parsed from ServiceState)
    val nrState: String? = null,          // NONE / RESTRICTED / NOT_RESTRICTED / CONNECTED
    val isEnDcAvailable: Boolean? = null,
    val isNrAvailable: Boolean? = null,
) {
    val hasNrSignal: Boolean get() = nrSsRsrp != null
}

/** Rolling per-SIM history for the live graphs; all lists stay the same length. */
data class SignalHistory(
    val lteRsrp: List<Float?> = emptyList(),
    val nrRsrp: List<Float?> = emptyList(),
    val lteSinr: List<Float?> = emptyList(),
    val nrSinr: List<Float?> = emptyList(),
) {
    fun append(s: SimSignal) = SignalHistory(
        lteRsrp = lteRsrp.push(s.lteRsrp),
        nrRsrp = nrRsrp.push(s.nrSsRsrp),
        lteSinr = lteSinr.push(s.lteRssnr),
        nrSinr = nrSinr.push(s.nrSsSinr),
    )

    companion object {
        const val CAPACITY = 120          // samples kept
        const val SECONDS_PER_SAMPLE = 1  // → 2 minutes on screen

        private fun List<Float?>.push(v: Int?): List<Float?> =
            (if (size >= CAPACITY) drop(size - CAPACITY + 1) else this) + v?.toFloat()
    }
}
