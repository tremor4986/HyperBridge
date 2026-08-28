package com.alexkoala.kyper.service.translators

import kotlinx.serialization.Serializable

@Serializable
data class RemoteRuleConfig(
    val version: Int = 1,
    val apps: List<RemoteAppRule> = emptyList()
)

@Serializable
data class RemoteAppRule(
    val packageName: String,
    val ignoreList: List<String> = emptyList(),
    val allowList: List<String> = emptyList(),
    val rules: List<NotificationRuleDef> = emptyList()
)

@Serializable
data class NotificationRuleDef(
    val type: String, // e.g., "match", "regex"
    val match: String? = null,
    val regex: String? = null,
    val contains: List<String>? = null,
    val instruction: String,
    val distance: String,
    val textCleanup: String? = null,
    val targetLayout: String? = null // e.g., "NAVIGATION", "MESSAGE", "PROGRESS"
)
