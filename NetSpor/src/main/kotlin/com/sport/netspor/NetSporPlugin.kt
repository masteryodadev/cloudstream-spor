package com.sport.netspor

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NetSporPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NetSpor())
    }
}
