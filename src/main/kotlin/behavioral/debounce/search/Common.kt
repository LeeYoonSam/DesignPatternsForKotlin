package behavioral.debounce.search

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

// 검색 API 호출을 시뮬레이션하는 클래스
class SearchApi {
    private val requestCounter = AtomicInteger(0)

    // 검색 API 호출을 시뮬레이션하는 함수
    suspend fun search(query: String): List<String> {
        val requestId = requestCounter.incrementAndGet()
        println("API 요청 #$requestId: '$query' 검색 중...")

        // 네트워크 지연 시간 시뮬레이션
        delay(500)

        val results = when {
            query.isEmpty() -> emptyList()
            else -> listOf(
                "$query 관련 결과 1",
                "$query 관련 결과 2",
                "$query 관련 결과 3"
            )
        }

        println("API 요청 #$requestId: '$query' 검색 완료, ${results.size}개 결과 반환")
        return results
    }
}