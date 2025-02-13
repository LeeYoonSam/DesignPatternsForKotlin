package stability.throttling.api

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Semaphore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 시간 기반과 동시성 기반의 두 가지 쓰로틀링 전략을 구현하여 요청을 제어하는 방법
 */
class Solution {
    interface ThrottlingStrategy {
        suspend fun acquirePermission(): Boolean
        fun reset()
    }

    // 시간 단위 제한 전략
    class TimeBasedThrottling(
        private val maxRequests: Int,
        private val timeWindow: Duration
    ) : ThrottlingStrategy {
        private val requests = ConcurrentSkipListMap<Instant, Int>()

        override suspend fun acquirePermission(): Boolean {
            val now = Instant.now()
            val windowStart = now.minusMillis(timeWindow.inWholeMilliseconds)

            // 오래된 요청 정보 제거
            requests.headMap(windowStart, false).clear()

            // 현재 시간 창의 요청 수 제한
            val currentRequests = requests.tailMap(windowStart).values.sum()

            return if (currentRequests < maxRequests) {
                requests[now] = 1
                true
            } else {
                false
            }
        }

        override fun reset() {
            requests.clear()
        }
    }

    // 동시성 제한 전략
    class ConcurrencyBasedThrottling(
        private val maxConcurrentRequests: Int
    ) : ThrottlingStrategy {
        private val semaphore = Semaphore(maxConcurrentRequests)

        override suspend fun acquirePermission(): Boolean {
            return semaphore.tryAcquire()
        }

        override fun reset() {
            // Semaphore 를 초기 상태로 재설정
            while (semaphore.tryAcquire()) { /* empty */}
            repeat(maxConcurrentRequests) { semaphore.release() }
        }
    }

    class ThrottledApiClient(
        private val apiService: Problem.ApiService,
        private val strategies: List<ThrottlingStrategy>
    ) {
        private suspend fun executeRequest(requestId: Int): String {
            // 모든 전략에서 권한을 획득해야 함
            val hasPermission = strategies.all { it.acquirePermission() }

            return if (hasPermission) {
                try {
                    apiService.fetchData(requestId)
                } finally {
                    // 필요한 경우 여기서 리소스 해제
                }
            } else {
                throw ThrottlingException("Request $requestId throttled")
            }
        }

        suspend fun processRequests() {
            coroutineScope {
                repeat(100) { requestId ->
                    launch {
                        try {
                            val result = executeRequest(requestId)
                            println("Request $requestId: $result")
                        } catch (e: ThrottlingException) {
                            println(e.message)
                            delay(100) // 재시도 전 지연
                        }
                    }
                }
            }
        }
    }

    class ThrottlingException(message: String): Exception(message)
}

suspend fun main() {
    val strategies = listOf(
        Solution.TimeBasedThrottling(maxRequests = 10, timeWindow = 1.seconds),
        Solution.ConcurrencyBasedThrottling(maxConcurrentRequests = 5)
    )

    val client = Solution.ThrottledApiClient(Problem.ApiService(), strategies)
    client.processRequests()
}