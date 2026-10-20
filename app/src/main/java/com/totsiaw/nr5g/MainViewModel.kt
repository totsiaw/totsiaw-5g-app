package com.totsiaw.nr5g

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.totsiaw.nr5g.nr.FiveGToggle
import com.totsiaw.nr5g.nr.NrModeController
import com.totsiaw.nr5g.nr.VerifyInfo
import com.totsiaw.nr5g.shizuku.ShizukuManager
import com.totsiaw.nr5g.telephony.NrChannel
import com.totsiaw.nr5g.telephony.SignalHistory
import com.totsiaw.nr5g.telephony.SignalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SignalRepository(app)
    private val nr = NrModeController()

    val signals = repo.signals
    val shizukuState: StateFlow<ShizukuManager.State> = ShizukuManager.state

    val history: StateFlow<Map<Int, SignalHistory>> = historyStore.asStateFlow()

    /** 5G carrier per SIM slot from the live channel table (Shizuku), see [NrModeController.readNrChannels]. */
    private val _nrChannels = MutableStateFlow<Map<Int, NrChannel>>(emptyMap())
    val nrChannels: StateFlow<Map<Int, NrChannel>> = _nrChannels.asStateFlow()

    private var inForeground = false

    var permissionsGranted by mutableStateOf(false)
        private set
    /** The 5G switch lives in the app process, not this screen. */
    val toggle: StateFlow<FiveGToggle.State> = FiveGToggle.state
    var verifying by mutableStateOf(false)
        private set
    var verifyInfo by mutableStateOf<VerifyInfo?>(null)
        private set
    /** Wi-Fi is the active network, so mobile data (and its 5G leg) sits idle. */
    var onWifi by mutableStateOf(false)
        private set

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    init {
        ShizukuManager.init(app)
        FiveGToggle.init(app)
        viewModelScope.launch {
            ShizukuManager.state.collect { state ->
                if (state == ShizukuManager.State.READY) FiveGToggle.onReady()
            }
        }
        // Sample at a fixed rate so the graphs scroll steadily even when the
        // radio reports nothing new.
        viewModelScope.launch {
            while (isActive) {
                onWifi = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                repo.refreshDataSim()
                sample()
                delay(SignalHistory.SECONDS_PER_SAMPLE * 1000L)
            }
        }
        // Shell read, so only while the app is on screen.
        viewModelScope.launch {
            while (isActive) {
                if (inForeground && ShizukuManager.state.value == ShizukuManager.State.READY) {
                    _nrChannels.value = nr.readNrChannels()
                }
                delay(5000)
            }
        }
    }

    private fun sample() {
        val sims = signals.value
        if (sims.isEmpty()) return
        val now = System.currentTimeMillis()
        val step = SignalHistory.SECONDS_PER_SAMPLE * 1000L
        // Seconds skipped since the last sample (e.g. the app was closed) become blanks.
        val missed = if (lastSampleAt == 0L) 0 else ((now - lastSampleAt) / step - 1).toInt()
        lastSampleAt = now
        val old = historyStore.value
        historyStore.value = sims.associate { s ->
            s.subId to (old[s.subId] ?: SignalHistory()).gap(missed).append(s)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        permissionsGranted = granted
        if (granted) repo.start()
    }

    fun setNsa(enable: Boolean) = FiveGToggle.set(enable)

    fun runVerify() {
        if (verifying || ShizukuManager.state.value != ShizukuManager.State.READY) return
        viewModelScope.launch {
            verifying = true
            verifyInfo = nr.verify()
            verifying = false
        }
    }

    fun clearVerify() { verifyInfo = null }

    fun requestShizuku() = ShizukuManager.requestPermission()

    fun onPause() {
        inForeground = false
    }

    fun onResume() {
        inForeground = true
        ShizukuManager.refresh()
        FiveGToggle.refresh()
    }

    override fun onCleared() {
        repo.stop()
    }

    private companion object {
        // Kept for the life of the app process so reopening the screen keeps the graphs.
        val historyStore = MutableStateFlow<Map<Int, SignalHistory>>(emptyMap())
        var lastSampleAt = 0L
    }
}
