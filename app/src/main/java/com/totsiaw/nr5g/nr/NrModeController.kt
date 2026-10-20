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
        onStep("Setting NR mode to 3 (NSA)…")
        val put = ShizukuManager.exec("settings put system user_nr_mode 3")
        if (!put.ok) {
            onStep("Failed: ${put.err.ifBlank { "could not write user_nr_mode" }}")
            return false
        }
        toggleAirplane(onStep)
        onStep("Checking for EN-DC…")
        val up = pollEnDc()
        onStep(if (up) "Done — 5G (NSA) is active." else "Applied. 5G will light up when a 5G cell is in range.")
        return true
    }

    suspend fun reset(onStep: (String) -> Unit): Boolean {
        onStep("Removing NR mode override…")
        val del = ShizukuManager.exec("settings delete system user_nr_mode")
        if (!del.ok) {
            onStep("Failed: ${del.err.ifBlank { "could not delete user_nr_mode" }}")
            return false
        }
        toggleAirplane(onStep)
        onStep("Reset to stock (NSA off).")
        return true
    }

    private suspend fun toggleAirplane(onStep: (String) -> Unit) {
        onStep("Re-applying radio (airplane mode)…")
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
