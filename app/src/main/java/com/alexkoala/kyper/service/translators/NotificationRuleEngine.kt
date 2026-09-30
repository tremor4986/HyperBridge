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
            safeLogD("Rules loaded: ${currentConfig?.apps?.size ?: 0} apps")
        } catch (e: Exception) {
            safeLogE("Failed to parse rules JSON", e)
        }
    }

    private fun safeLogD(msg: String) {
        try { Log.d("NotificationRuleEngine", msg) } catch (_: Throwable) {}
    }

    private fun safeLogE(msg: String, e: Exception) {
        try { Log.e("NotificationRuleEngine", msg, e) } catch (_: Throwable) {}
    }

    /**
     * Attempts to translate a notification using app-specific rules from rules.json.
     * @return RemoteRuleMatch if a matching rule is found, null otherwise.
     */
    fun tryTranslate(sbn: StatusBarNotification, title: String, text: String): RemoteRuleMatch? {
        val pkg = sbn.packageName
        return tryTranslate(pkg, title, text)
    }

    fun tryTranslate(packageName: String, title: String, text: String): RemoteRuleMatch? {
        val appRule = currentConfig?.apps?.find { it.packageName == packageName } ?: return null
        return applyAppRule(appRule, title, text)
    }

    private fun applyAppRule(appRule: RemoteAppRule, title: String, text: String): RemoteRuleMatch? {
        val combinedSlash = "$title / $text".replace("  ", " ").trim()
        val combinedSpace = "$title $text".replace("  ", " ").trim()
        val combinedNewline = "$title\n$text".replace("  ", " ").trim()

        // 1. Check allow list (If specified, notification MUST contain at least one)
        if (appRule.allowList.isNotEmpty()) {
            val isAllowed = appRule.allowList.any { 
                combinedSlash.contains(it, ignoreCase = true) || combinedSpace.contains(it, ignoreCase = true)
            }
            if (!isAllowed) return null
        }

        // 2. Check ignore list
        if (appRule.ignoreList.any { 
            combinedSlash.contains(it, ignoreCase = true) || combinedSpace.contains(it, ignoreCase = true)
        }) {
            return RemoteRuleMatch("", "", shouldIgnore = true)
        }

        for (rule in appRule.rules) {
            when (rule.type) {
                "match" -> {
                    val isMatch = rule.match == null || rule.match.isEmpty() ||
                        combinedSlash.contains(rule.match, ignoreCase = true) ||
                        combinedSpace.contains(rule.match, ignoreCase = true)

                    if (isMatch) {
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
                        if (regex != null) {
                            val candidates = if (rule.regex.contains("/")) {
                                listOf(combinedSlash, combinedNewline, combinedSpace)
                            } else {
                                listOf(combinedNewline, combinedSpace, combinedSlash)
                            }

                            for (candidate in candidates) {
                                val match = regex.find(candidate)
                                if (match != null) {
                                    var inst = rule.instruction
                                    var dist = rule.distance

                                    for (i in match.groupValues.lastIndex downTo 1) {
                                        val value = match.groupValues[i]
                                        inst = inst.replace("\$$i", value)
                                        dist = dist.replace("\$$i", value)
                                    }

                                    inst = inst.replace(Regex("\\$\\d+"), "").trim()
                                    dist = dist.replace(Regex("\\$\\d+"), "").trim()

                                    return RemoteRuleMatch(
                                        instruction = inst,
                                        distance = dist,
                                        targetLayout = rule.targetLayout
                                    )
                                }
                            }
                        }
                    }
                }
                "transit_moving" -> {
                    val isMatch = rule.match != null && (
                        combinedSlash.contains(rule.match, ignoreCase = true) ||
                        combinedSpace.contains(rule.match, ignoreCase = true)
                    )
                    if (isMatch) {
                        val hasAll = rule.contains?.all {
                            combinedSlash.contains(it, ignoreCase = true) || combinedSpace.contains(it, ignoreCase = true)
                        } ?: true
                        if (hasAll) {
                            val parts = combinedSlash.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parts.size >= 2) {
                                var inst = rule.instruction
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
