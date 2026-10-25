package org.retroforge.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "RetroForge Emulator",
    ) {
        App()
    }
}
