package com.alexkoala.kyper.service.translators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class NotificationRuleEngineTest {

    private val sampleRulesJson = """
    {
      "version": 17,
      "apps": [
        {
          "packageName": "com.nhn.android.nmap",
          "rules": [
            {
              "type": "regex",
              "regex": "^(.*?까지\\s*걷기)",
              "instruction": "$1",
              "distance": "도보 이동",
              "targetLayout": "NAVIGATION"
            },
            {
              "type": "regex",
              "regex": "(.*?방향|직진|좌회전|우회전|유턴)\\s*\\n?\\s*(\\d+m|\\d+km)?\\s*(남음)?",
              "instruction": "$1",
              "distance": "$2 남음",
              "targetLayout": "NAVIGATION"
            },
            {
              "type": "regex",
              "regex": "^.*?\\s*/\\s*(.*?)역\\s*내리는 문\\s*([^,\\s]+).*",
              "instruction": "$2 문으로 하차",
              "distance": "$1역 접근 중",
              "targetLayout": "NAVIGATION"
            },
            {
              "type": "match",
              "match": "길안내를 시작합니다",
              "instruction": "길안내 시작",
              "distance": "네이버지도",
              "targetLayout": "NAVIGATION"
            },
            {
              "type": "transit_moving",
              "match": "이동 중",
              "contains": ["정류장"],
              "instruction": "{cleanup} 남음",
              "distance": "이동 중",
              "textCleanup": "하차까지",
              "targetLayout": "NAVIGATION"
            }
          ]
        },
        {
          "packageName": "viva.republica.toss",
          "rules": [
            {
              "type": "regex",
              "regex": "따릉이\\s*(\\d+분|\\d+시간(?:\\s*\\d+분)?)\\s*남았어요",
              "instruction": "$1 남음",
              "distance": "따릉이",
              "targetLayout": "NAVIGATION"
            }
          ]
        },
        {
          "packageName": "com.android.vending",
          "rules": [
            {
              "type": "regex",
              "regex": "(.*)\\s*(다운로드 중|설치 중|업데이트 중)",
              "instruction": "$2",
              "distance": "$1",
              "targetLayout": "NAVIGATION"
            }
          ]
        },
        {
          "packageName": "com.skt.prod.dialer",
          "rules": [
            {
              "type": "match",
              "match": "",
              "instruction": "",
              "distance": "",
              "targetLayout": "CALL"
            }
          ]
        }
      ]
    }
    """.trimIndent()

    @Before
    fun setUp() {
        NotificationRuleEngine.loadRules(sampleRulesJson)
    }

    @Test
    fun testNaverMapsDirectionAndDistanceRule() {
        val match = NotificationRuleEngine.tryTranslate("com.nhn.android.nmap", "강남대로 방향", "200m 남음")
        assertNotNull(match)
        assertEquals("강남대로 방향", match?.instruction)
        assertEquals("200m 남음", match?.distance)
        assertEquals("NAVIGATION", match?.targetLayout)
    }

    @Test
    fun testNaverMapsSubwayRule() {
        val match = NotificationRuleEngine.tryTranslate("com.nhn.android.nmap", "수인분당선", "강남역 내리는 문 오른쪽")
        assertNotNull(match)
        assertEquals("오른쪽 문으로 하차", match?.instruction)
        assertEquals("강남역 접근 중", match?.distance)
        assertEquals("NAVIGATION", match?.targetLayout)
    }

    @Test
    fun testTossTtareungyiRule() {
        val match = NotificationRuleEngine.tryTranslate("viva.republica.toss", "따릉이", "45분 남았어요")
        assertNotNull(match)
        assertEquals("45분 남음", match?.instruction)
        assertEquals("따릉이", match?.distance)
        assertEquals("NAVIGATION", match?.targetLayout)
    }

    @Test
    fun testPlayStoreDownloadRule() {
        val match = NotificationRuleEngine.tryTranslate("com.android.vending", "Chrome", "다운로드 중")
        assertNotNull(match)
        assertEquals("다운로드 중", match?.instruction)
        assertEquals("Chrome", match?.distance)
        assertEquals("NAVIGATION", match?.targetLayout)
    }

    @Test
    fun testTPhoneCallRule() {
        val match = NotificationRuleEngine.tryTranslate("com.skt.prod.dialer", "홍길동", "통화 중")
        assertNotNull(match)
        assertEquals("CALL", match?.targetLayout)
    }
}
