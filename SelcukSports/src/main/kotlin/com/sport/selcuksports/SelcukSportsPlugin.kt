package com.sport.selcuksports

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SelcukSportsPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SelcukSports())
    }
}
