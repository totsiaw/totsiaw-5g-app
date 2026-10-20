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
import com.totsiaw.nr5g.telephony.SignalRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SignalRepository(app)
    private val nr = NrModeController()

    val signals = repo.signals
    val shizukuState: StateFlow<ShizukuManager.State> = ShizukuManager.state

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
            statusLine = "Reading radio state…"
            verifyInfo = nr.verify()
            statusLine = null
            busy = false
        }
    }

    fun clearVerify() { verifyInfo = null }

    fun requestShizuku() = ShizukuManager.requestPermission()

    fun onResume() {
        ShizukuManager.refresh()
        refreshMode()
    }

    override fun onCleared() {
        repo.stop()
    }
}
