// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Placeholder screen. The real UI (Compose) arrives in milestone M1. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = TextView(this).apply {
            text = getString(R.string.skeleton_message)
            textSize = 18f
            setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        }
        setContentView(message)
    }

    private companion object {
        const val PADDING_PX = 48
    }
}
