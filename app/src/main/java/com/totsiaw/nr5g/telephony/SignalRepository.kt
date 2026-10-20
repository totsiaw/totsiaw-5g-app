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

    // SIMs can be swapped, added or removed while the app runs; re-sync on every change.
    private val subsListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() = syncSubscriptions()
    }

    fun start() {
        if (started || !hasPhoneState() || baseTm == null) return
        started = true
        subMgr?.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(context), subsListener)
        syncSubscriptions()
    }

    fun stop() {
        subMgr?.removeOnSubscriptionsChangedListener(subsListener)
        holders.values.forEach { runCatching { it.tm.unregisterTelephonyCallback(it.callback) } }
        holders.clear()
        started = false
    }

    /** Listen to exactly the active SIMs: drop removed ones, add new ones, refresh names/slots. */
    @SuppressLint("MissingPermission")
    private fun syncSubscriptions() {
        if (!started || baseTm == null) return
        val subs = subMgr?.activeSubscriptionInfoList ?: emptyList()
        val active = subs.map { it.subscriptionId }.toSet()
        holders.keys.filter { it !in active }.forEach { gone ->
            holders.remove(gone)?.let { runCatching { it.tm.unregisterTelephonyCallback(it.callback) } }
        }
        val executor = ContextCompat.getMainExecutor(context)
        for (sub in subs) {
            val subId = sub.subscriptionId
            val name = sub.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
            val existing = holders[subId]
            if (existing != null) {
                existing.data = existing.data.copy(carrier = name, slotIndex = sub.simSlotIndex)
                continue
            }
            val tm = baseTm.createForSubscriptionId(subId)
            val cb = SignalCallback(subId)
            holders[subId] = Holder(tm, cb, SimSignal(subId, sub.simSlotIndex, name, "No service", "NONE"))
            tm.registerTelephonyCallback(executor, cb)
        }
        publish()
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
        val dataSub = SubscriptionManager.getDefaultDataSubscriptionId()
        _signals.value = holders.values
            .map { it.data.copy(isDataSim = it.data.subId == dataSub) }
            .sortedBy { it.slotIndex }
    }

    /** Re-check which SIM carries data (the user can switch it any time). */
    fun refreshDataSim() {
        if (holders.isNotEmpty()) publish()
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
                    rat = label(info.networkType, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE),
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
            val ca = serviceState.cellBandwidths.size > 1
            update(subId) {
                it.copy(nrState = nrState, isEnDcAvailable = endc, isNrAvailable = nrAvail, carrierAggregation = ca)
            }
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
                    val arfcn = id.nrarfcn.valid()
                    r = r.copy(
                        nrArfcn = arfcn,
                        nrPci = id.pci.valid(),
                        nrBand = id.bands.joinToString("/").takeIf { b -> b.isNotBlank() }?.let { b -> "n$b" }
                            ?: arfcn?.let { a -> NrBands.bandFor(a) },
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
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "No service"
            else -> "2G"
        }
    }
}
