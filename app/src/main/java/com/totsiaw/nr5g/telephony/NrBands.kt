package com.totsiaw.nr5g.telephony

/**
 * NR-ARFCN → band / frequency (3GPP TS 38.101-1/-2 downlink ranges).
 * Phones often report the 5G channel number but leave the band list empty,
 * especially in NSA, so the band is derived from the channel.
 */
object NrBands {

    // Where ranges overlap, the band listed first wins: n78 before n77 (3.5 GHz,
    // the band used in Pakistan), n41 before n7/n38 (2.6 GHz TDD), n28 before n20.
    private val ranges = listOf(
        78 to 620000..653333,
        77 to 620000..680000,
        79 to 693334..733333,
        41 to 499200..537999,
        40 to 460000..480000,
        38 to 514000..524000,
        7 to 524000..538000,
        1 to 422000..434000,
        3 to 361000..376000,
        28 to 151600..160600,
        20 to 158200..164200,
        8 to 185000..192000,
        5 to 173800..178800,
        257 to 2054166..2104165,
        258 to 2016667..2070832,
        260 to 2229166..2279165,
        261 to 2070833..2084999,
    )

    fun bandFor(arfcn: Int): String? = ranges.firstOrNull { arfcn in it.second }?.let { "n${it.first}" }

    /** Downlink centre frequency in MHz. */
    fun frequencyMhz(arfcn: Int): Double = when {
        arfcn < 600000 -> arfcn * 0.005
        arfcn < 2016667 -> 3000.0 + (arfcn - 600000) * 0.015
        else -> 24250.08 + (arfcn - 2016667) * 0.06
    }

    fun describe(arfcn: Int): String {
        val mhz = frequencyMhz(arfcn)
        return if (mhz >= 1000) "%.2f GHz".format(mhz / 1000) else "%.0f MHz".format(mhz)
    }
}

/** The 5G (NR) carrier the phone is actually using, read from its live channel table. */
data class NrChannel(
    val band: String?,
    val arfcn: Int?,
    val pci: Int?,
    val bandwidthMhz: Int?,
)
