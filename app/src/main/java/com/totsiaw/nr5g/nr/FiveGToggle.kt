package com.totsiaw.nr5g.nr

import android.content.Context
import android.content.SharedPreferences
import com.totsiaw.nr5g.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The 5G switch, owned by the app process rather than a screen, so leaving or
 * reopening the app never cuts a change off halfway.
 *
 * Taps while a change is running just move the target; the worker loops until the
 * phone matches the last thing the user asked for.
 */
object FiveGToggle {

    data class State(
        val enabled: Boolean = false,  // what the phone is set to now
        val pending: Boolean? = null,  // what the user asked for, until applied
        val busy: Boolean = false,
        val status: String? = null,
    ) {
        /** Position the switch should show: the user's latest choice wins. */
        val checked: Boolean get() = pending ?: enabled
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nr = NrModeController()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private const val KEY_RADIO_RESTART = "radio_restart_in_progress"

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("five_g", Context.MODE_PRIVATE)
    }

    /** Shizuku just became usable: undo an interrupted radio restart, then read the setting. */
    fun onReady() {
        scope.launch {
            if (prefs?.getBoolean(KEY_RADIO_RESTART, false) == true && !_state.value.busy) {
                // The app was killed while airplane mode was on for a restart; turn it back off.
                ShizukuManager.exec("cmd connectivity airplane-mode disable")
                prefs?.edit()?.putBoolean(KEY_RADIO_RESTART, false)?.apply()
            }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.busy || ShizukuManager.state.value != ShizukuManager.State.READY) return
        scope.launch {
            val on = nr.isNsaEnabled()
            _state.update { if (it.busy) it else it.copy(enabled = on) }
        }
    }

    fun set(enable: Boolean) {
        if (ShizukuManager.state.value != ShizukuManager.State.READY) return
        _state.update { it.copy(pending = enable) }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        scope.launch {
            try {
                while (true) {
                    val target = _state.value.pending ?: break
                    if (target == _state.value.enabled && nr.isNsaEnabled() == target) {
                        _state.update { it.copy(pending = null, status = null) }
                        break
                    }
                    prefs?.edit()?.putBoolean(KEY_RADIO_RESTART, true)?.commit()
                    val step: (String) -> Unit = { s -> _state.update { it.copy(status = s) } }
                    val ok = if (target) nr.enableNsa(step) else nr.reset(step)
                    prefs?.edit()?.putBoolean(KEY_RADIO_RESTART, false)?.apply()
                    val on = nr.isNsaEnabled()
                    _state.update {
                        it.copy(
                            enabled = on,
                            // Done only if the user didn't change their mind meanwhile.
                            pending = if (it.pending == target) null else it.pending,
                            status = if (ok) it.status else "Couldn't change 5G. Check that Shizuku is running.",
                        )
                    }
                    if (!ok) {
                        _state.update { it.copy(pending = null) }
                        break
                    }
                }
            } finally {
                _state.update { it.copy(busy = false, pending = null) }
            }
        }
    }
}
