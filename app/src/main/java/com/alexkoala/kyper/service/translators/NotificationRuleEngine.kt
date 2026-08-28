package com.alexkoala.kyper.service.translators

import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Handles app-specific notification parsing rules from remote rules.json.
 */
object NotificationRuleEngine {

    data class RemoteRuleMatch(
        val instruction: String,
        val distance: String,
        val eta: String = "",
        val shouldIgnore: Boolean = false,
        val targetLayout: String? = null
    )

    private var currentConfig: RemoteRuleConfig? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRules(jsonStr: String) {
        try {
            currentConfig = json.decodeFromString<RemoteRuleConfig>(jsonStr)
            Log.d("NotificationRuleEngine", "Rules loaded: ${currentConfig?.apps?.size ?: 0} apps")
        } catch (e: Exception) {
            Log.e("NotificationRuleEngine", "Failed to parse rules JSON", e)
        }
    }

    /**
     * Attempts to translate a notification using app-specific rules from rules.json.
     * @return RemoteRuleMatch if a matching rule is found, null otherwise.
     */
    fun tryTranslate(sbn: StatusBarNotification, title: String, text: String): RemoteRuleMatch? {
        val pkg = sbn.packageName
        val appRule = currentConfig?.apps?.find { it.packageName == pkg } ?: return null
        
        return applyAppRule(appRule, title, text)
    }

    private fun applyAppRule(appRule: RemoteAppRule, title: String, text: String): RemoteRuleMatch? {
        val combined = "$title / $text".replace("  ", " ")

        // 1. Check allow list (If specified, notification MUST contain at least one)
        if (appRule.allowList.isNotEmpty()) {
            val isAllowed = appRule.allowList.any { combined.contains(it, ignoreCase = true) }
            if (!isAllowed) return null
        }

        // 2. Check ignore list
        if (appRule.ignoreList.any { combined.contains(it, ignoreCase = true) }) {
            return RemoteRuleMatch("", "", shouldIgnore = true)
        }

        for (rule in appRule.rules) {
            when (rule.type) {
                "match" -> {
                    if (rule.match != null && combined.contains(rule.match, ignoreCase = true)) {
                        return RemoteRuleMatch(
                            instruction = rule.instruction,
                            distance = rule.distance,
                            targetLayout = rule.targetLayout
                        )
                    }
                }
                "regex" -> {
                    if (rule.regex != null) {
                        val regex = try { Regex(rule.regex, RegexOption.IGNORE_CASE) } catch (_: Exception) { null }
                        val match = regex?.find(combined)
                        if (match != null) {
                            var inst = rule.instruction
                            var dist = rule.distance
                            
                            // Simple replacement of $1, $2, etc.
                            match.groupValues.forEachIndexed { i, value ->
                                if (i > 0) {
                                    inst = inst.replace("$$i", value)
                                    dist = dist.replace("$$i", value)
                                }
                            }
                            
                            return RemoteRuleMatch(
                                instruction = inst.trim(),
                                distance = dist.trim(),
                                targetLayout = rule.targetLayout
                            )
                        }
                    }
                }
                "transit_moving" -> {
                    if (rule.match != null && combined.contains(rule.match, ignoreCase = true)) {
                        val hasAll = rule.contains?.all { combined.contains(it, ignoreCase = true) } ?: true
                        if (hasAll) {
                            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parts.size >= 2) {
                                var inst = rule.instruction
                                // For transit, parts[1] is usually the detailed text
                                var contentToClean = parts[1]
                                rule.textCleanup?.let { contentToClean = contentToClean.replace(it, "", ignoreCase = true).trim() }
                                inst = inst.replace("{cleanup}", contentToClean)
                                
                                return RemoteRuleMatch(
                                    instruction = inst,
                                    distance = rule.distance,
                                    targetLayout = rule.targetLayout
                                )
                            }
                        }
                    }
                }
            }
        }
        return null
    }
}
