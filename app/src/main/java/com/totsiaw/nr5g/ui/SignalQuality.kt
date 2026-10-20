package com.totsiaw.nr5g.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Signal quality levels. Status colors are fixed and always paired with the word. */
enum class Quality(val label: String, val color: Color) {
    Strong("Strong", Color(0xFF0CA30C)),
    Ok("OK", Color(0xFFFAB219)),
    Weak("Weak", Color(0xFFEC835A)),
    VeryWeak("Very weak", Color(0xFFD03B3B)),
}

/** Lower bounds for Strong / OK / Weak; anything below [weakAbove] is Very weak. */
class QualityScale(val strongAbove: Float, val okAbove: Float, val weakAbove: Float) {
    fun of(v: Float): Quality = when {
        v >= strongAbove -> Quality.Strong
        v >= okAbove -> Quality.Ok
        v >= weakAbove -> Quality.Weak
        else -> Quality.VeryWeak
    }

    fun of(v: Int?): Quality? = v?.let { of(it.toFloat()) }

    companion object {
        /** RSRP / SS-RSRP in dBm. */
        val Power = QualityScale(strongAbove = -90f, okAbove = -100f, weakAbove = -110f)
        /** SINR / RSSNR in dB. */
        val Snr = QualityScale(strongAbove = 13f, okAbove = 5f, weakAbove = 0f)
        /** RSRQ / SS-RSRQ in dB. */
        val Rsrq = QualityScale(strongAbove = -10f, okAbove = -15f, weakAbove = -20f)
    }
}

/** Small "● Strong" chip; the dot carries the color, the word carries the meaning. */
@Composable
fun QualityChip(q: Quality) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(q.color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Spacer(Modifier.size(6.dp).clip(CircleShape).background(q.color))
        Spacer(Modifier.width(5.dp))
        Text(q.label, color = MiuixTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}
