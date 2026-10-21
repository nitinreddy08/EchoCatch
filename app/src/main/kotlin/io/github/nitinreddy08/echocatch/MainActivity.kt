// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nitinreddy08.echocatch.ui.AppRoot
import io.github.nitinreddy08.echocatch.ui.EchoCatchTheme

/** The single screen host. Everything the user sees, including the permission questions, is inside the app (see ui/). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as EchoCatchApp).graph
        setContent {
            EchoCatchTheme {
                AppRoot(graph)
            }
        }
    }
}
