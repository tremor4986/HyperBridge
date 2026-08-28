package com.d4viddf.hyperbridge.service.translators

import kotlinx.serialization.Serializable

@Serializable
data class RemoteNavConfig(
    val version: Int = 1,
    val apps: List<RemoteAppRule> = emptyList()
)

@Serializable
data class RemoteAppRule(
    val packageName: String,
    val ignoreList: List<String> = emptyList(),
    val rules: List<NavRuleDef> = emptyList()
)

@Serializable
data class NavRuleDef(
    val type: String, // e.g., "match", "regex"
    val match: String? = null,
    val regex: String? = null,
    val contains: List<String>? = null,
    val instruction: String,
    val distance: String,
    val textCleanup: String? = null
)
