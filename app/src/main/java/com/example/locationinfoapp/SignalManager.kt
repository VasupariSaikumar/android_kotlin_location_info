package com.example.locationinfoapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi

data class SignalInfo(
    val type: String, // "4G", "5G", "3G"
    val dbm: Int,     // The signal strength (e.g., -95)
    val level: Int    // 0 to 4 (The bars you see in status bar)
)

class SignalManager(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission") // We will check permissions before calling this
    fun getSignalStrength(): SignalInfo? {
        try {
            // 1. Get list of all cell towers the phone sees
            val cellInfos = telephonyManager.allCellInfo

            if (cellInfos.isNullOrEmpty()) {
                Log.e("SignalManager", "No Cell Info found. Is GPS/SIM on?")
                return null
            }

            // 2. Find the "Registered" cell (The one currently providing data/calls)
            val activeCell = cellInfos.find { it.isRegistered }

            if (activeCell == null) return null

            // 3. Extract data based on Network Type (4G vs 5G vs 3G)
            return when (activeCell) {
                // 4G (LTE)
                is CellInfoLte -> {
                    val signal = activeCell.cellSignalStrength
                    SignalInfo("4G (LTE)", signal.dbm, signal.level)
                }
                // 5G (New Radio - NR)
                is CellInfoNr -> {
                    val signal = activeCell.cellSignalStrength as CellSignalStrengthNr
                    SignalInfo("5G (NR)", signal.dbm, signal.level)
                }
                // 3G (WCDMA)
                is CellInfoWcdma -> {
                    val signal = activeCell.cellSignalStrength
                    SignalInfo("3G", signal.dbm, signal.level)
                }
                // GSM (2G)
                is CellInfoGsm -> {
                    val signal = activeCell.cellSignalStrength
                    SignalInfo("2G (GSM)", signal.dbm, signal.level)
                }
                else -> {
                    Log.w("SignalManager", "Unknown Cell Type: ${activeCell.javaClass.simpleName}")
                    null
                }
            }

        } catch (e: Exception) {
            Log.e("SignalManager", "Error reading signal: ${e.message}")
            return null
        }
    }
}