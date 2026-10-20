package com.totsiaw.nr5g.telephony

/** A snapshot of one SIM's connectivity + technical radio signal. */
data class SimSignal(
    val subId: Int,
    val slotIndex: Int,
    val carrier: String,
    val displayNetwork: String,   // user-facing: "5G NSA" / "LTE+" / "LTE" / "—"
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
