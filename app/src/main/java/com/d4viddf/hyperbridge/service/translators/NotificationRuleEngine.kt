package com.d4viddf.hyperbridge.service.translators

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
        
        // 1. Try Remote Rules first
        val appRule = currentConfig?.apps?.find { it.packageName == pkg }
        if (appRule != null) {
            val result = applyAppRule(appRule, title, text)
            if (result != null) return result
        }

        // 2. Fallback to hardcoded local rules if remote failed or not found (Only for Nav apps)
        return when (pkg) {
            "com.nhn.android.nmap" -> translateNaverMaps(title, text)
            else -> null
        }
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

    private fun translateNaverMaps(title: String, text: String): RemoteRuleMatch? {
        // Local fallback logic (same as before)
        val combined = "$title / $text".replace("  ", " ")

        if (combined.contains("다른 앱 위에 표시") || combined.contains("내비게이션 - 안내 중")) {
            return RemoteRuleMatch("", "", shouldIgnore = true)
        }
        
        if (combined.contains("길안내를 시작합니다")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val startIdx = parts.indexOfFirst { it.contains("길안내를 시작합니다") }
            if (startIdx != -1 && parts.size > startIdx + 1) {
                return RemoteRuleMatch(
                    instruction = "길안내 시작",
                    distance = "네이버 지도",
                    targetLayout = "NAVIGATION"
                )
            }
        }

        if (combined.contains("이동 중") && combined.contains("정류장")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val moveStatus = if (parts[0].contains("이동 중")) "이동 중" else parts[0]
                val stopInfo = parts[1].replace("하차까지", "").trim()
                return RemoteRuleMatch(
                    instruction = "$stopInfo 남음",
                    distance = moveStatus,
                    targetLayout = "NAVIGATION"
                )
            }
        }

        val busPattern = Regex("([^,()\\s/]+행|[^,()\\s/]+)\\s*\\((도착|곧 도착|\\d+분|출발|진입)\\)")
        val firstMatch = busPattern.find(combined)

        if (firstMatch != null) {
            val name = firstMatch.groupValues[1].trim()
            val status = firstMatch.groupValues[2].trim()
            return RemoteRuleMatch(
                instruction = status,
                distance = name,
                targetLayout = "NAVIGATION"
            )
        }

        if (combined.contains("/")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val left = parts[0].split(",").first().trim()
                val right = parts[1].split(",").first().trim()
                return RemoteRuleMatch(instruction = right, distance = left, targetLayout = "NAVIGATION")
            }
        }

        return RemoteRuleMatch(
            instruction = text,
            distance = title.ifEmpty { "네이버 지도" },
            targetLayout = "NAVIGATION"
        )
    }
}
