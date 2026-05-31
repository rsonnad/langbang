package com.sponic.langbang

import android.content.Context
import android.provider.Settings
import android.util.Log

object AdbWifiKeeper {
    private const val TAG = "AdbWifiKeeper"
    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val ALWAYS_ON_VPN_APP = "always_on_vpn_app"
    private const val ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    fun enableIfGranted(context: Context, reason: String) {
        try {
            val resolver = context.contentResolver
            val current = Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0)
            if (current != 1) {
                Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 1)
                Log.i(TAG, "Enabled Wireless debugging after $reason")
            }
        } catch (se: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted; skipped after $reason")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not enable Wireless debugging after $reason", t)
        }

        ensureTailscaleAlwaysOn(context, reason)
    }

    private fun ensureTailscaleAlwaysOn(context: Context, reason: String) {
        try {
            val resolver = context.contentResolver
            val currentPackage = Settings.Secure.getString(resolver, ALWAYS_ON_VPN_APP)
            val currentLockdown = Settings.Secure.getInt(resolver, ALWAYS_ON_VPN_LOCKDOWN, 0)
            if (currentPackage != TAILSCALE_PACKAGE) {
                Settings.Secure.putString(resolver, ALWAYS_ON_VPN_APP, TAILSCALE_PACKAGE)
                Log.i(TAG, "Set Tailscale as always-on VPN after $reason")
            }
            if (currentLockdown != 0) {
                Settings.Secure.putInt(resolver, ALWAYS_ON_VPN_LOCKDOWN, 0)
                Log.i(TAG, "Disabled always-on VPN lockdown after $reason")
            }
        } catch (se: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted; skipped VPN setup after $reason")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not configure Tailscale always-on VPN after $reason", t)
        }
    }
}
