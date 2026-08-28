package com.d4viddf.hyperbridge.service.translators

import android.content.Context
import android.util.Log
import com.d4viddf.hyperbridge.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val RULES_URL = "https://gist.githubusercontent.com/alexkoala/3809f83caf364a95a1d33baa554bf69d/raw/rules.json"

    suspend fun fetchLatestRules(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching rules from $RULES_URL")
                val json = URL(RULES_URL).readText()
                val preferences = AppPreferences(context)
                preferences.setRemoteNavRules(json)
                Log.d(TAG, "Rules updated successfully")
                
                // Refresh the engine cache
                NavigationRuleEngine.loadRules(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote rules", e)
            }
        }
    }
}
