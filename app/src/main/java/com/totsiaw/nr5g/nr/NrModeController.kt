package com.totsiaw.nr5g.nr

import com.totsiaw.nr5g.shizuku.ShizukuManager
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
        val mode = readUserNrMode()
        val reg = ShizukuManager.exec(
            "dumpsys telephony.registry | grep -E 'isEnDcAvailable|isNrAvailable|overrideNetwork|nrState' | head -n 20"
        )
        val log = ShizukuManager.exec(
            "logcat -d -t 400 OplusNrModeRusCarrierControl:* OplusNrModeUpdater:* NetworkTypeController:* *:S | tail -n 15"
        )
        return VerifyInfo(
            userNrMode = mode,
            registry = reg.out.ifBlank { reg.err.ifBlank { "(no data)" } },
            logcat = log.out.ifBlank { log.err.ifBlank { "(no logcat lines)" } },
        )
    }
}

data class VerifyInfo(val userNrMode: String?, val registry: String, val logcat: String)
