package com.grimreap.game

import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "GameBridge")
class GameBridgePlugin : Plugin() {
    @PluginMethod
    fun getRuntimeInfo(call: PluginCall) {
        val metrics = activity.resources.displayMetrics
        val info = JSObject().apply {
            put("platform", "android")
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkVersion", Build.VERSION.SDK_INT)
            put("screenWidthPx", metrics.widthPixels)
            put("screenHeightPx", metrics.heightPixels)
            put("density", metrics.density.toDouble())
            put("densityDpi", metrics.densityDpi)
        }
        call.resolve(info)
    }

    override fun handleOnResume() {
        super.handleOnResume()
        notifyListeners("appStateChange", JSObject().put("isActive", true))
    }

    override fun handleOnPause() {
        super.handleOnPause()
        notifyListeners("appStateChange", JSObject().put("isActive", false))
    }
}
