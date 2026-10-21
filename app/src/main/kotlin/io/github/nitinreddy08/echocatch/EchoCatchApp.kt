// SPDX-License-Identifier: GPL-3.0-only
package io.github.nitinreddy08.echocatch

import android.app.Application

class EchoCatchApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.recoverFromUnexpectedStop()
    }
}
