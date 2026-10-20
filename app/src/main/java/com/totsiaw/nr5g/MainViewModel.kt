package com.totsiaw.nr5g

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _history = MutableStateFlow<Map<Int, SignalHistory>>(emptyMap())
    val history: StateFlow<Map<Int, SignalHistory>> = _history.asStateFlow()

    /** 5G carrier per SIM slot from the live channel table (Shizuku), see [NrModeController.readNrChannels]. */
    private val _nrChannels = MutableStateFlow<Map<Int, NrChannel>>(emptyMap())
    val nrChannels: StateFlow<Map<Int, NrChannel>> = _nrChannels.asStateFlow()

    private var inForeground = false

    var permissionsGranted by mutableStateOf(false)
        private set
    var nsaEnabled by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var statusLine by mutableStateOf<String?>(null)
        private set
    var verifyInfo by mutableStateOf<VerifyInfo?>(null)
        private set

    init {
        ShizukuManager.init(app)
        viewModelScope.launch {
            ShizukuManager.state.collect { state ->
                if (state == ShizukuManager.State.READY) refreshMode()
            }
        }
        // Sample at a fixed rate so the graphs scroll steadily even when the
        // radio reports nothing new.
        viewModelScope.launch {
            while (isActive) {
                delay(SignalHistory.SECONDS_PER_SAMPLE * 1000L)
                val sims = signals.value
                if (sims.isNotEmpty()) {
                    val old = _history.value
                    _history.value = sims.associate { s ->
                        s.subId to (old[s.subId] ?: SignalHistory()).append(s)
                    }
                }
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

    fun onPermissionsResult(granted: Boolean) {
        permissionsGranted = granted
        if (granted) repo.start()
    }

    fun refreshMode() {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) return
        viewModelScope.launch { nsaEnabled = nr.isNsaEnabled() }
    }

    fun setNsa(enable: Boolean) {
        if (busy || ShizukuManager.state.value != ShizukuManager.State.READY) return
        viewModelScope.launch {
            busy = true
            statusLine = null
            val ok = if (enable) nr.enableNsa { statusLine = it } else nr.reset { statusLine = it }
            if (ok) nsaEnabled = nr.isNsaEnabled()
            busy = false
        }
    }

    fun runVerify() {
        if (busy || ShizukuManager.state.value != ShizukuManager.State.READY) return
        viewModelScope.launch {
            busy = true
            statusLine = "Checking…"
            verifyInfo = nr.verify()
            statusLine = null
            busy = false
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
        refreshMode()
    }

    override fun onCleared() {
        repo.stop()
    }
}
