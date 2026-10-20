package com.totsiaw.nr5g.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Plain-language meaning of each technical row. Keys ignore an "LTE "/"NR " prefix. */
object InfoText {
    private val text = mapOf(
        "Network" to "The type of mobile network your phone is using right now.",
        "Status bar shows" to "The icon at the top of your screen. It can say 5G when 5G is only offered " +
            "nearby, even while your phone is still using LTE.",
        "5G" to "Whether your phone is actually using 5G right now.",
        "Override" to "An extra label your phone adds to the network name, like 5G or LTE+.",
        "NR state" to "The 5G status the network reports for your phone.",
        "EN-DC available" to "Whether this tower lets your phone use 4G and 5G together. " +
            "That is how 5G works in Pakistan.",
        "NR available" to "Whether the tower says 5G is offered nearby.",
        "RSRP" to "How strong the signal is. Closer to zero is better: -80 is great, -110 is very weak.",
        "SS-RSRP" to "How strong the 5G signal is. Closer to zero is better: -80 is great, -110 is very weak.",
        "RSRQ" to "How clean the signal is when many people share the tower. Closer to zero is better.",
        "SS-RSRQ" to "How clean the 5G signal is when many people share the tower. Closer to zero is better.",
        "SNR" to "How clear the signal is over background noise. Higher is better: above 13 is great, " +
            "below 0 is poor.",
        "SS-SINR" to "How clear the 5G signal is over background noise. Higher is better: above 13 is great, " +
            "below 0 is poor.",
        "band" to "The group of radio frequencies your phone is using. Some reach further, others are faster.",
        "EARFCN" to "The exact channel your phone is tuned to, like a station number on a radio.",
        "NR-ARFCN" to "The exact 5G channel your phone is tuned to, like a station number on a radio.",
        "PCI" to "The ID of the tower antenna you are connected to. If it changes, you moved to another antenna.",
        "frequency" to "The radio frequency of the 5G signal. Higher is faster but goes through walls less well.",
        "bandwidth" to "How wide the 5G channel is. Wider means more speed.",
        "Signal strength" to "How strong your signal is over the last 2 minutes. Higher on the graph is better.",
        "Signal quality" to "How clear your signal is over the last 2 minutes. Higher on the graph is better.",
    )

    fun of(key: String): String? = text[key] ?: text[key.removePrefix("LTE ").removePrefix("NR ")]
}

/** Small circled "i" shown in front of a label that has an explanation. */
@Composable
fun InfoIcon(modifier: Modifier = Modifier) {
    val tint = MiuixTheme.colorScheme.onSurfaceVariantActions
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(end = 10.dp)
            .size(16.dp)
            .border(1.dp, tint, CircleShape),
    ) {
        Text("i", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
