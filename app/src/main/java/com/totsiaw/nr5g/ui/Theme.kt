package com.totsiaw.nr5g.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Accent colors reused across the UI. */
object AppColors {
    val Active = Color(0xFF34C759)   // 5G NSA up
    val Accent = Color(0xFF0A84FF)   // brand blue
    val Warn = Color(0xFFFF9F0A)
    val Danger = Color(0xFFFF453A)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller, content = content)
}
