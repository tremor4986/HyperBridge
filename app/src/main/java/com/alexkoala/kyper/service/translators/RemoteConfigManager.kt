package com.alexkoala.kyper.service.translators

import android.content.Context
import android.util.Log
import com.alexkoala.kyper.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"
    private const val RULES_URL = "https://gist.githubusercontent.com/alexkoala/3809f83caf364a95a1d33baa554bf69d/raw/rules.json"

    suspend fun fetchLatestRules(context: Context): Int? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching rules from $RULES_URL")
                val json = URL(RULES_URL).readText()
                
                val kotlinxJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val config = kotlinxJson.decodeFromString<RemoteRuleConfig>(json)
                
                val preferences = AppPreferences(context)
                preferences.setRemoteNavRules(json)
                Log.d(TAG, "Rules updated successfully to version v${config.version}")
                
                // Refresh the engine cache
                NotificationRuleEngine.loadRules(json)
                config.version
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch remote rules", e)
                null
            }
        }
    }
}
