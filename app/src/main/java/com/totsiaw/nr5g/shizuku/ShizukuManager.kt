package com.totsiaw.nr5g.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Thin wrapper over the Shizuku client API. Lets the app run shell commands with
 * the shell UID (2000), enough for `settings put system user_nr_mode 3` and
 * `cmd connectivity airplane-mode …` without root.
 */
object ShizukuManager {

    private const val PERM_REQUEST = 4001
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEED_PERMISSION, READY }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    private var installed = true
    private var registered = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        _state.value = if (installed) State.NOT_RUNNING else State.NOT_INSTALLED
    }
    private val permResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun init(context: Context) {
        installed = isInstalled(context)
        if (!registered) {
            registered = true
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permResult)
        }
        refresh()
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun refresh() {
        _state.value = when {
            !installed -> State.NOT_INSTALLED
            !pingSafe() -> State.NOT_RUNNING
            isGranted() -> State.READY
            else -> State.NEED_PERMISSION
        }
    }

    private fun pingSafe(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    private fun isGranted(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun requestPermission() {
        if (!pingSafe()) return
        runCatching { Shizuku.requestPermission(PERM_REQUEST) }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val svc = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                ?: return@withContext ShellResult(-1, "", "Shizuku binder unavailable")
            val proc = svc.newProcess(arrayOf("sh", "-c", command), null, null)
            val out = ParcelFileDescriptor.AutoCloseInputStream(proc.inputStream)
                .bufferedReader().use { it.readText() }
            val err = ParcelFileDescriptor.AutoCloseInputStream(proc.errorStream)
                .bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            ShellResult(code, out.trim(), err.trim())
        } catch (e: Throwable) {
            ShellResult(-1, "", e.message ?: e.toString())
        }
    }
}

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
}
