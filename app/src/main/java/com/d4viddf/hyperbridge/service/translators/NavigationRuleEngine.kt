package com.d4viddf.hyperbridge.service.translators

import android.service.notification.StatusBarNotification

/**
 * Handles app-specific navigation notification parsing rules.
 */
object NavigationRuleEngine {

    data class CustomNavResult(
        val instruction: String,
        val distance: String,
        val eta: String = ""
    )

    /**
     * Attempts to translate a notification using app-specific rules.
     * @return CustomNavResult if a matching rule is found, null otherwise.
     */
    fun tryTranslate(sbn: StatusBarNotification, title: String, text: String): CustomNavResult? {
        return when (sbn.packageName) {
            "com.nhn.android.nmap" -> translateNaverMaps(title, text)
            // "com.kakao.taxi" -> translateKakaoTaxi(title, text) // Example for future extension
            else -> null
        }
    }

    private fun translateNaverMaps(title: String, text: String): CustomNavResult? {
        // Use a separator to handle cases where info is split between title and text
        val combined = "$title / $text".replace("  ", " ")
        
        // Case 1: 길안내를 시작합니다. / [목적지]까지 이동
        if (combined.contains("길안내를 시작합니다")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val startIdx = parts.indexOfFirst { it.contains("길안내를 시작합니다") }
            if (startIdx != -1 && parts.size > startIdx + 1) {
                return CustomNavResult(
                    instruction = "길안내 시작",   // Expanded Right: 길안내 시작
                    distance = "네이버 지도"     // Expanded Left: 네이버 지도
                )
            }
        }

        // Case 2: ~로 이동 중 / 하차까지 ~개 정류장
        if (combined.contains("이동 중") && combined.contains("정류장")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val moveStatus = if (parts[0].contains("이동 중")) "이동 중" else parts[0]
                val stopInfo = parts[1].replace("하차까지", "").trim()
                return CustomNavResult(
                    instruction = "$stopInfo 남음", // Expanded Right: 4개 정류장 남음
                    distance = moveStatus           // Expanded Left: 이동 중
                )
            }
        }

        // Case 3: 버스/지하철 도착 정보 추출 (첫 번째 정보만 표시)
        // 지하철(내선순환행 등)과 버스 번호를 모두 포함하도록 패턴 보강
        val busPattern = Regex("([^,()\\s/]+행|[^,()\\s/]+)\\s*\\((도착|곧 도착|\\d+분|출발|진입)\\)")
        val firstMatch = busPattern.find(combined)

        if (firstMatch != null) {
            val name = firstMatch.groupValues[1].trim()
            val status = firstMatch.groupValues[2].trim()
            return CustomNavResult(
                instruction = status, // Expanded Right: 6분
                distance = name       // Expanded Left: 내선순환행
            )
        }

        // Case 3: General Fallback for Naver Maps (슬래시(/) 기반 분리 및 첫 항목 추출)
        if (combined.contains("/")) {
            val parts = combined.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val left = parts[0].split(",").first().trim()
                val right = parts[1].split(",").first().trim()

                return CustomNavResult(
                    instruction = right,
                    distance = left
                )
            }
        }

        return CustomNavResult(
            instruction = text,
            distance = title.ifEmpty { "네이버 지도" }
        )
    }
}
