package com.d4viddf.hyperbridge.service.translators

import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.serialization.json.Json

/**
 * Handles app-specific navigation notification parsing rules.
 */
object NavigationRuleEngine {

    data class CustomNavResult(
        val instruction: String,
        val distance: String,
        val eta: String = "",
        val shouldIgnore: Boolean = false
    )

    private var currentConfig: RemoteNavConfig? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRules(jsonStr: String) {
        try {
            currentConfig = json.decodeFromString<RemoteNavConfig>(jsonStr)
            Log.d("NavigationRuleEngine", "Rules loaded: ${currentConfig?.apps?.size ?: 0} apps")
        } catch (e: Exception) {
            Log.e("NavigationRuleEngine", "Failed to parse rules JSON", e)
        }
    }

    /**
     * Attempts to translate a notification using app-specific rules.
     * @return CustomNavResult if a matching rule is found, null otherwise.
     */
    fun tryTranslate(sbn: StatusBarNotification, title: String, text: String): CustomNavResult? {
        val pkg = sbn.packageName
        
        // 1. Try Remote Rules first
        val appRule = currentConfig?.apps?.find { it.packageName == pkg }
        if (appRule != null) {
            val result = applyAppRule(appRule, title, text)
            if (result != null) return result
        }

        // 2. Fallback to hardcoded local rules if remote failed or not found
        return when (pkg) {
            "com.nhn.android.nmap" -> translateNaverMaps(title, text)
            else -> null
        }
    }

    private fun applyAppRule(appRule: RemoteAppRule, title: String, text: String): CustomNavResult? {
        val combined = "$title / $text".replace("  ", " ")

        // Check ignore list
        if (appRule.ignoreList.any { combined.contains(it) }) {
            return CustomNavResult("", "", shouldIgnore = true)
        }

        for (rule in appRule.rules) {
            when (rule.type) {
                "match" -> {
                    if (rule.match != null && combined.contains(rule.match)) {
                        return CustomNavResult(
                            instruction = rule.instruction,
                            distance = rule.distance
                        )
                    }
                }
                "regex" -> {
                    if (rule.regex != null) {
                        val regex = Regex(rule.regex)
                        val match = regex.find(combined)
                        if (match != null) {
                            var inst = rule.instruction
                            var dist = rule.distance
                            
                            // Simple replacement of $1, $2, etc.
                            match.groupValues.forEachIndexed { i, value ->
                                inst = inst.replace("$$i", value)
                                dist = dist.replace("$$i", value)
                            }
                            
                            return CustomNavResult(
                                instruction = inst.trim(),
                                distance = dist.trim()
                            )
                        }
                    }
                }
                "transit_moving" -> {
                    if (rule.match != null && combined.contains(rule.match)) {
                        val hasAll = rule.contains?.all { combined.contains(it) } ?: true
                        if (hasAll) {
                            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parts.size >= 2) {
                                var inst = rule.instruction
                                var cleanedPart1 = parts[1]
                                rule.textCleanup?.let { cleanedPart1 = cleanedPart1.replace(it, "").trim() }
                                inst = inst.replace("{cleanup}", cleanedPart1)
                                
                                return CustomNavResult(
                                    instruction = inst,
                                    distance = rule.distance
                                )
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun translateNaverMaps(title: String, text: String): CustomNavResult? {
        // Local fallback logic (same as before)
        val combined = "$title / $text".replace("  ", " ")

        if (combined.contains("다른 앱 위에 표시") || combined.contains("내비게이션 - 안내 중")) {
            return CustomNavResult("", "", shouldIgnore = true)
        }
        
        if (combined.contains("길안내를 시작합니다")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val startIdx = parts.indexOfFirst { it.contains("길안내를 시작합니다") }
            if (startIdx != -1 && parts.size > startIdx + 1) {
                return CustomNavResult(
                    instruction = "길안내 시작",
                    distance = "네이버 지도"
                )
            }
        }

        if (combined.contains("이동 중") && combined.contains("정류장")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val moveStatus = if (parts[0].contains("이동 중")) "이동 중" else parts[0]
                val stopInfo = parts[1].replace("하차까지", "").trim()
                return CustomNavResult(
                    instruction = "$stopInfo 남음",
                    distance = moveStatus
                )
            }
        }

        val busPattern = Regex("([^,()\\s/]+행|[^,()\\s/]+)\\s*\\((도착|곧 도착|\\d+분|출발|진입)\\)")
        val firstMatch = busPattern.find(combined)

        if (firstMatch != null) {
            val name = firstMatch.groupValues[1].trim()
            val status = firstMatch.groupValues[2].trim()
            return CustomNavResult(
                instruction = status,
                distance = name
            )
        }

        if (combined.contains("/")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val left = parts[0].split(",").first().trim()
                val right = parts[1].split(",").first().trim()
                return CustomNavResult(instruction = right, distance = left)
            }
        }

        return CustomNavResult(
            instruction = text,
            distance = title.ifEmpty { "네이버 지도" }
        )
    }
}
