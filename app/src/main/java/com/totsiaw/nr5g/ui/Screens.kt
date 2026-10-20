package com.totsiaw.nr5g.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.totsiaw.nr5g.MainViewModel
import com.totsiaw.nr5g.nr.VerifyInfo
import com.totsiaw.nr5g.shizuku.ShizukuManager
import com.totsiaw.nr5g.telephony.SignalHistory
import com.totsiaw.nr5g.telephony.SimSignal
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/* ----------------------------------------------------------------------- */

@Composable
fun AppRoot(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { target ->
        when (target) {
            "about" -> AboutScreen(onBack = { screen = "home" })
            else -> HomeScreen(vm, onRequestPermissions, onAbout = { screen = "about" })
        }
    }
}

/* ----------------------------------------------------------------------- */

@Composable
fun HomeScreen(vm: MainViewModel, onRequestPermissions: () -> Unit, onAbout: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val signals by vm.signals.collectAsState()
    val history by vm.history.collectAsState()
    val shizuku by vm.shizukuState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = "5G Switch", scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = innerPadding,
        ) {
            item(key = "hero") { HeroCard(signals) }

            item(key = "control") {
                SmallTitle(text = "Controls")
                ControlCard(
                    shizuku = shizuku,
                    nsaEnabled = vm.nsaEnabled,
                    busy = vm.busy,
                    statusLine = vm.statusLine,
                    onToggle = vm::setNsa,
                    onRequestShizuku = vm::requestShizuku,
                    onVerify = vm::runVerify,
                )
            }

            vm.verifyInfo?.let { info ->
                item(key = "verify") {
                    SmallTitle(text = "Status")
                    VerifyCard(info, onClose = vm::clearVerify)
                }
            }

            if (!vm.permissionsGranted) {
                item(key = "perm") {
                    SmallTitle(text = "Permissions")
                    Card(modifier = cardMod()) {
                        SuperArrow(
                            title = "Allow phone and location access",
                            summary = "Needed to show your signal",
                            onClick = onRequestPermissions,
                        )
                    }
                }
            }

            if (signals.isEmpty()) {
                item(key = "nosignal") {
                    SmallTitle(text = "SIMs")
                    Card(modifier = cardMod()) {
                        BasicComponent(
                            title = "No SIM data yet",
                            summary = if (vm.permissionsGranted) "Waiting for the network…"
                            else "Allow access above to see your signal.",
                        )
                    }
                }
            } else {
                items(signals, key = { it.subId }) { sim ->
                    SignalCard(sim, history[sim.subId] ?: SignalHistory())
                }
            }

            item(key = "about") {
                SmallTitle(text = "App")
                Card(modifier = cardMod()) {
                    SuperArrow(title = "About", onClick = onAbout)
                }
            }

            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/* ----------------------------------------------------------------------- */

@Composable
private fun HeroCard(signals: List<SimSignal>) {
    val primary = signals.firstOrNull { it.overrideNetwork.startsWith("NR") }
        ?: signals.firstOrNull { it.displayNetwork != "No service" }
        ?: signals.firstOrNull()
    val label = primary?.displayNetwork ?: "No service"
    val color = when {
        label.startsWith("5G") -> AppColors.Active
        label.startsWith("LTE") -> AppColors.Accent
        else -> MiuixTheme.colorScheme.onSurfaceVariantActions
    }
    Card(modifier = cardMod()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Current connection",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = label, color = color, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            primary?.let {
                Spacer(Modifier.height(2.dp))
                Text(text = it.carrier, color = MiuixTheme.colorScheme.onSurfaceVariantActions, fontSize = 13.sp)
            }
        }
    }
}

/* ----------------------------------------------------------------------- */

@Composable
private fun ControlCard(
    shizuku: ShizukuManager.State,
    nsaEnabled: Boolean,
    busy: Boolean,
    statusLine: String?,
    onToggle: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onVerify: () -> Unit,
) {
    Card(modifier = cardMod()) {
        ShizukuRow(shizuku, onRequestShizuku)

        val ready = shizuku == ShizukuManager.State.READY
        SuperSwitch(
            title = "5G",
            summary = "Use 5G when available",
            checked = nsaEnabled,
            enabled = ready && !busy,
            onCheckedChange = { onToggle(it) },
        )
        SuperArrow(
            title = "Reset to default",
            enabled = ready && !busy,
            onClick = { onToggle(false) },
        )
        SuperArrow(
            title = "Check status",
            enabled = ready && !busy,
            onClick = onVerify,
        )
        if (statusLine != null) {
            Text(
                text = statusLine,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ShizukuRow(shizuku: ShizukuManager.State, onRequestShizuku: () -> Unit) {
    val (text, color) = when (shizuku) {
        ShizukuManager.State.READY -> "Connected" to AppColors.Active
        ShizukuManager.State.NEED_PERMISSION -> "Tap to allow" to AppColors.Warn
        ShizukuManager.State.NOT_RUNNING -> "Not running. Start Shizuku, then reopen the app." to AppColors.Warn
        ShizukuManager.State.NOT_INSTALLED -> "Not installed" to AppColors.Danger
    }
    BasicComponent(
        title = "Shizuku",
        summary = text,
        startAction = { Dot(color) },
        onClick = if (shizuku == ShizukuManager.State.NEED_PERMISSION) onRequestShizuku else null,
    )
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/* ----------------------------------------------------------------------- */

@Composable
private fun SignalCard(sim: SimSignal, history: SignalHistory) {
    SmallTitle(text = "${sim.carrier} (SIM ${sim.slotIndex + 1})")
    Card(modifier = cardMod()) {
        val lte = SeriesColors.lte()
        val nr = SeriesColors.nr()
        LiveChart(
            title = "Signal strength",
            unit = "dBm",
            series = listOf(
                ChartSeries("LTE", lte, history.lteRsrp),
                ChartSeries("5G", nr, history.nrRsrp),
            ),
            capacity = SignalHistory.CAPACITY,
            secondsPerSample = SignalHistory.SECONDS_PER_SAMPLE,
        )
        LiveChart(
            title = "Signal quality",
            unit = "dB",
            series = listOf(
                ChartSeries("LTE", lte, history.lteSinr),
                ChartSeries("5G", nr, history.nrSinr),
            ),
            capacity = SignalHistory.CAPACITY,
            secondsPerSample = SignalHistory.SECONDS_PER_SAMPLE,
        )

        kv("Network", sim.displayNetwork)
        kv("Override", sim.overrideNetwork.takeIf { it != "NONE" })
        kv("NR state", sim.nrState)
        kv("EN-DC available", sim.isEnDcAvailable?.let { if (it) "yes" else "no" })
        kv("NR available", sim.isNrAvailable?.let { if (it) "yes" else "no" })
        // LTE
        kv("LTE RSRP", sim.lteRsrp?.let { "$it dBm" })
        kv("LTE RSRQ", sim.lteRsrq?.let { "$it dB" })
        kv("LTE SNR", sim.lteRssnr?.let { "$it dB" })
        kv("LTE band", sim.lteBand)
        kv("EARFCN", sim.lteEarfcn?.toString())
        kv("LTE PCI", sim.ltePci?.toString())
        // NR
        kv("NR SS-RSRP", sim.nrSsRsrp?.let { "$it dBm" })
        kv("NR SS-RSRQ", sim.nrSsRsrq?.let { "$it dB" })
        kv("NR SS-SINR", sim.nrSsSinr?.let { "$it dB" })
        kv("NR band", sim.nrBand)
        kv("NR-ARFCN", sim.nrArfcn?.toString())
        kv("NR PCI", sim.nrPci?.toString())
    }
}

@Composable
private fun kv(key: String, value: String?) {
    if (value == null) return
    BasicComponent(
        title = key,
        endActions = {
            Text(
                text = value,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
        },
    )
}

/* ----------------------------------------------------------------------- */

@Composable
private fun VerifyCard(info: VerifyInfo, onClose: () -> Unit) {
    Card(modifier = cardMod()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = if (info.userNrMode == "3") "5G is switched on" else "Default setting",
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("Network report", color = MiuixTheme.colorScheme.onSurfaceVariantActions, fontSize = 12.sp)
            Text(info.registry, color = MiuixTheme.colorScheme.onSurface, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("Modem log", color = MiuixTheme.colorScheme.onSurfaceVariantActions, fontSize = 12.sp)
            Text(info.logcat, color = MiuixTheme.colorScheme.onSurface, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ----------------------------------------------------------------------- */

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = "About",
                scrollBehavior = scrollBehavior,
                navigationIcon = { TextButton(text = "Back", onClick = onBack) },
            )
        },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxWidth()) {
            item {
                Card(modifier = cardMod()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("5G Switch", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Version $version", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Made with ❤️ by totsiaw",
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item {
                SmallTitle(text = "How it works")
                Card(modifier = cardMod()) {
                    AboutText(
                        "Some carriers keep 5G turned off on this phone. 5G Switch turns it on " +
                            "and restarts the mobile network so the change takes effect. " +
                            "Reset puts everything back the way it was."
                    )
                }
            }
            item {
                SmallTitle(text = "You need")
                Card(modifier = cardMod()) {
                    AboutText(
                        "• Shizuku running\n" +
                            "• Phone and location access\n" +
                            "• 5G coverage where you are"
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AboutText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 14.sp,
    )
}

/* ----------------------------------------------------------------------- */

private fun cardMod(): Modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 12.dp)
    .padding(bottom = 12.dp)
