package com.totsiaw.nr5g.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Observes per-subscription connectivity + signal using public TelephonyManager
 * callbacks (API 31+). No root. Needs READ_PHONE_STATE (+ ACCESS_FINE_LOCATION
 * for cell identity: earfcn/pci/band).
 */
class SignalRepository(private val context: Context) {

    private val _signals = MutableStateFlow<List<SimSignal>>(emptyList())
    val signals: StateFlow<List<SimSignal>> = _signals.asStateFlow()

    private val subMgr = context.getSystemService(SubscriptionManager::class.java)
    private val baseTm = context.getSystemService(TelephonyManager::class.java)

    private class Holder(val tm: TelephonyManager, val callback: TelephonyCallback, var data: SimSignal)

    private val holders = ConcurrentHashMap<Int, Holder>()
    private var started = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPhoneState() || baseTm == null) return
        started = true
        val subs = subMgr?.activeSubscriptionInfoList ?: emptyList()
        val executor = ContextCompat.getMainExecutor(context)
        for (sub in subs) {
            val subId = sub.subscriptionId
            val tm = baseTm.createForSubscriptionId(subId)
            val name = sub.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
            val initial = SimSignal(subId, sub.simSlotIndex, name, "—", "NONE")
            val cb = SignalCallback(subId)
            holders[subId] = Holder(tm, cb, initial)
            tm.registerTelephonyCallback(executor, cb)
        }
        publish()
    }

    fun stop() {
        holders.values.forEach { runCatching { it.tm.unregisterTelephonyCallback(it.callback) } }
        holders.clear()
        started = false
    }

    private fun hasPhoneState() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocation() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun update(subId: Int, transform: (SimSignal) -> SimSignal) {
        val h = holders[subId] ?: return
        h.data = transform(h.data)
        publish()
    }

    private fun publish() {
        _signals.value = holders.values.map { it.data }.sortedBy { it.slotIndex }
    }

    private inner class SignalCallback(val subId: Int) : TelephonyCallback(),
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.CellInfoListener {

        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
            update(subId) {
                it.copy(
                    displayNetwork = label(info.networkType, info.overrideNetworkType),
                    overrideNetwork = overrideName(info.overrideNetworkType),
                )
            }
        }

        override fun onSignalStrengthsChanged(ss: SignalStrength) {
            val lte = ss.getCellSignalStrengths(CellSignalStrengthLte::class.java).firstOrNull()
            val nr = ss.getCellSignalStrengths(CellSignalStrengthNr::class.java).firstOrNull()
            update(subId) {
                it.copy(
                    lteRsrp = lte?.rsrp?.valid(),
                    lteRsrq = lte?.rsrq?.valid(),
                    lteRssnr = lte?.rssnr?.valid(),
                    nrSsRsrp = nr?.ssRsrp?.valid(),
                    nrSsRsrq = nr?.ssRsrq?.valid(),
                    nrSsSinr = nr?.ssSinr?.valid(),
                )
            }
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            val text = serviceState.toString()
            val nrState = Regex("nrState=(\\w+)").find(text)?.groupValues?.getOrNull(1)
            val endc = Regex("isEnDcAvailable\\s*=\\s*(true|false)").find(text)?.groupValues?.getOrNull(1)?.toBoolean()
            val nrAvail = Regex("isNrAvailable\\s*=\\s*(true|false)").find(text)?.groupValues?.getOrNull(1)?.toBoolean()
            update(subId) { it.copy(nrState = nrState, isEnDcAvailable = endc, isNrAvailable = nrAvail) }
        }

        @SuppressLint("MissingPermission")
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
            if (!hasLocation()) return
            val lte = cellInfo.filterIsInstance<CellInfoLte>().let { list ->
                list.firstOrNull { it.isRegistered } ?: list.firstOrNull()
            }
            val nr = cellInfo.filterIsInstance<CellInfoNr>().let { list ->
                list.firstOrNull { it.isRegistered } ?: list.firstOrNull()
            }
            update(subId) {
                var r = it
                (lte?.cellIdentity as? CellIdentityLte)?.let { id ->
                    r = r.copy(
                        lteEarfcn = id.earfcn.valid(),
                        ltePci = id.pci.valid(),
                        lteBand = id.bands.joinToString("/").takeIf { b -> b.isNotBlank() }?.let { b -> "B$b" },
                    )
                }
                (nr?.cellIdentity as? CellIdentityNr)?.let { id ->
                    r = r.copy(
                        nrArfcn = id.nrarfcn.valid(),
                        nrPci = id.pci.valid(),
                        nrBand = id.bands.joinToString("/").takeIf { b -> b.isNotBlank() }?.let { b -> "n$b" },
                    )
                }
                r
            }
        }
    }

    private fun Int.valid(): Int? =
        if (this == Int.MAX_VALUE || this == Int.MIN_VALUE || this == CellInfo.UNAVAILABLE) null else this

    private fun overrideName(o: Int): String = when (o) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "NR_NSA"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NR_ADVANCED"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE_CA"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE_ADV_PRO"
        else -> "NONE"
    }

    private fun label(networkType: Int, override: Int): String = when (override) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G NSA+"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE+"
        else -> when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "—"
            else -> "2G"
        }
    }
}
