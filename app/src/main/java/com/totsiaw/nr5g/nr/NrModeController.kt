package com.totsiaw.nr5g.nr

import com.totsiaw.nr5g.shizuku.ShizukuManager
import com.totsiaw.nr5g.telephony.NrBands
import com.totsiaw.nr5g.telephony.NrChannel
import kotlinx.coroutines.delay

/**
 * Drives Oplus NR mode selection via Shizuku shell.
 *
 * On this phone (realme RMX6699, ColorOS): Settings.System "user_nr_mode" wins
 * over the carrier default when set. Empirically, 1 = no NSA/EN-DC, 3 = NSA works.
 * After writing the setting the radio must re-apply it, which an airplane-mode
 * toggle forces.
 */
class NrModeController {

    suspend fun readUserNrMode(): String? {
        val r = ShizukuManager.exec("settings get system user_nr_mode")
        val v = r.out.trim()
        return if (!r.ok || v.isEmpty() || v == "null") null else v
    }

    suspend fun isNsaEnabled(): Boolean = readUserNrMode() == "3"

    suspend fun enableNsa(onStep: (String) -> Unit): Boolean {
        onStep("Turning on 5G…")
        val put = ShizukuManager.exec("settings put system user_nr_mode 3")
        if (!put.ok) {
            onStep("Couldn't turn on 5G. Check that Shizuku is running.")
            return false
        }
        toggleAirplane(onStep)
        onStep("Looking for 5G…")
        val up = pollEnDc()
        onStep(if (up) "5G is on." else "5G is on. It will show up near a 5G tower.")
        return true
    }

    suspend fun reset(onStep: (String) -> Unit): Boolean {
        onStep("Restoring default…")
        val del = ShizukuManager.exec("settings delete system user_nr_mode")
        if (!del.ok) {
            onStep("Couldn't restore default. Check that Shizuku is running.")
            return false
        }
        toggleAirplane(onStep)
        onStep("Back to default.")
        return true
    }

    private suspend fun toggleAirplane(onStep: (String) -> Unit) {
        onStep("Restarting mobile network…")
        ShizukuManager.exec("cmd connectivity airplane-mode enable")
        delay(3000)
        ShizukuManager.exec("cmd connectivity airplane-mode disable")
        delay(2500)
    }

    private suspend fun pollEnDc(timeoutMs: Long = 18000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val r = ShizukuManager.exec(
                "dumpsys telephony.registry | grep -E 'overrideNetwork|isEnDcAvailable'"
            )
            if (r.out.contains("NR_NSA") || r.out.contains("isEnDcAvailable = true")) return true
            delay(1500)
        }
        return false
    }

    suspend fun verify(): VerifyInfo {
        val switchedOn = isNsaEnabled()
        // Only the current per-SIM state lines (one service-state + one display line
        // per SIM, in slot order); the rest of the dump is history.
        val reg = ShizukuManager.exec(
            "dumpsys telephony.registry | grep -E '^ +(mServiceState|mTelephonyDisplayInfo)='"
        )
        val attached = readNrChannels()
        val sims = parseSims(reg.out).mapIndexed { slot, s -> s.copy(nrAttached = slot in attached) }
        return VerifyInfo(switchedOn = switchedOn, sims = sims, error = !reg.ok && reg.out.isBlank())
    }

    private fun parseSims(text: String): List<SimStatus> {
        val sims = mutableListOf<SimStatus>()
        for (line in text.lines()) {
            val l = line.trim()
            if (l.startsWith("mServiceState=")) {
                sims += SimStatus(
                    carrier = Regex("mOperatorAlphaLong=([^,]+)").find(l)?.groupValues?.get(1)?.trim()
                        ?.takeIf { it != "null" } ?: "SIM ${sims.size + 1}",
                    towerNearby = flag(l, "isNrAvailable"),
                    phoneReady = flag(l, "isEnDcAvailable"),
                    carrierBlocks = flag(l, "isDcNrRestricted"),
                )
            } else if (l.startsWith("mTelephonyDisplayInfo=") && sims.isNotEmpty()) {
                val override = Regex("overrideNetwork=(\\w+)").find(l)?.groupValues?.get(1)
                val network = Regex("network=(\\w+)").find(l)?.groupValues?.get(1)
                sims[sims.lastIndex] = sims.last().copy(showing = showingLabel(network, override))
            }
        }
        return sims
    }

    /**
     * The 5G carrier in use per SIM slot, from the phone's live channel table (needs a
     * privileged permission, hence Shizuku). In NSA the 5G cell is often missing from
     * the public cell list, but it shows up here while the 5G leg is active.
     */
    suspend fun readNrChannels(): Map<Int, NrChannel> {
        val r = ShizukuManager.exec("dumpsys telephony.registry | grep -E '^ +mPhysicalChannelConfigs='")
        if (r.out.isBlank()) return emptyMap()
        val result = mutableMapOf<Int, NrChannel>()
        r.out.lines().filter { it.isNotBlank() }.forEachIndexed { slot, line ->
            val nr = Regex("\\{([^{}]*)\\}").findAll(line).map { it.groupValues[1] }.mapNotNull { e ->
                fun str(k: String) = Regex("$k=(\\w+)").find(e)?.groupValues?.get(1)
                fun int(k: String) = str(k)?.toIntOrNull()
                val channel = int("mDownlinkChannelNumber")?.takeIf { it > 0 }
                // NR-ARFCNs start above 100000; LTE EARFCNs stay well below that.
                val isNr = str("mNetworkType") == "NR" || (channel != null && channel >= 100000)
                if (!isNr) return@mapNotNull null
                NrChannel(
                    band = int("mBand")?.takeIf { it > 0 }?.let { "n$it" } ?: channel?.let { NrBands.bandFor(it) },
                    arfcn = channel,
                    pci = int("mPhysicalCellId")?.takeIf { it in 0..1007 },
                    bandwidthMhz = int("mCellBandwidthDownlinkKhz")?.takeIf { it > 0 }?.div(1000),
                )
            }.firstOrNull()
            if (nr != null) result[slot] = nr
        }
        return result
    }

    private fun flag(line: String, name: String): Boolean? =
        Regex("$name\\s*=\\s*(true|false)").find(line)?.groupValues?.get(1)?.toBoolean()

    private fun showingLabel(network: String?, override: String?): String = when (override) {
        "NR_NSA", "NR_NSA_MMWAVE" -> "5G"
        "NR_ADVANCED" -> "5G+"
        "LTE_CA", "LTE_ADVANCED_PRO" -> "LTE+"
        else -> when (network) {
            "NR" -> "5G"
            "LTE" -> "LTE"
            null, "UNKNOWN" -> "No service"
            else -> network
        }
    }
}

/** Result of "Check status", already in plain terms. */
data class VerifyInfo(val switchedOn: Boolean, val sims: List<SimStatus>, val error: Boolean)

data class SimStatus(
    val carrier: String,
    val towerNearby: Boolean?,   // isNrAvailable: the LTE cell advertises a 5G partner
    val phoneReady: Boolean?,    // isEnDcAvailable: phone can add 5G on this cell
    val carrierBlocks: Boolean?, // isDcNrRestricted
    val showing: String = "No service",  // status bar label
    val nrAttached: Boolean = false,     // a 5G carrier is really in use
) {
    /** What the phone is actually using, not just what the icon says. */
    val actual: String
        get() = when {
            nrAttached -> "5G"
            showing.startsWith("5G") -> "LTE"
            else -> showing
        }

    /**
     * One-line answer to "is 5G working on this SIM?". While the 5G switch is off the
     * modem doesn't report nearby 5G at all, so "no tower" is only claimed when it's on.
     */
    fun verdict(switchedOn: Boolean): String = when {
        nrAttached -> "Connected to 5G"
        carrierBlocks == true -> "Your plan doesn't allow 5G"
        showing.startsWith("5G") || phoneReady == true ->
            "5G available, but the network hasn't added it. It needs a stronger 5G signal."
        !switchedOn -> "5G is off. Turn it on to look for 5G."
        towerNearby == true -> "5G tower nearby. Still connecting, check again in a minute."
        else -> "No 5G tower here"
    }
}
