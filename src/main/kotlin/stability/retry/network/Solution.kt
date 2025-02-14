package stability.retry.network

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * 두 가지 재시도 전략(지수 백오프, 즉시 재시도)을 구현하여 일시적인 오류를 처리하는 방법
 */
class Solution {
    sealed interface RetryStrategy {
        suspend fun shouldRetry(attempt: Int, error: Throwable): Boolean
        suspend fun nextDelay(attempt: Int): Duration
    }

    // 지수 백오프 전략
    class ExponentialBackoffStrategy(
        private val maxAttempts: Int = 3,
        private val initialDelay: Duration = 100.milliseconds,
        private val maxDelay: Duration = 5.seconds,
        private val multiplier: Double = 2.0,
        private val jitter: Duration = 100.milliseconds
    ) : RetryStrategy {
        override suspend fun shouldRetry(attempt: Int, error: Throwable): Boolean {
            return when {
                attempt >= maxAttempts -> false
                error is Problem.NetworkException -> true
                else -> false
            }
        }

        @OptIn(ExperimentalTime::class)
        override suspend fun nextDelay(attempt: Int): Duration {
            val baseDelay = initialDelay.inWholeMilliseconds * multiplier.pow(attempt - 1)
            val withJitter = baseDelay + Random.nextLong(-jitter.inWholeMilliseconds, jitter.inWholeMilliseconds)
            return milliseconds(min(withJitter, maxDelay.inWholeMilliseconds.toDouble()))
        }
    }

    // 즉시 재시도 전략
    class ImmediateRetryStrategy(
        private val maxAttempts: Int = 3
    ) : RetryStrategy {
        override suspend fun shouldRetry(attempt: Int, error: Throwable): Boolean {
            return attempt < maxAttempts && error is Problem.NetworkException
        }

        override suspend fun nextDelay(attempt: Int): Duration = Duration.ZERO
    }

    class RetryableClient(
        private val service: Problem.NetworkService,
        private val retryStrategy: RetryStrategy
    ) {
        suspend fun fetchDataWithRetry(id: String): String {
            var attempt = 1
            var lastError: Throwable? = null

            while (true) {
                try {
                    return service.fetchData(id)
                } catch (e: Exception) {
                    lastError = e

                    if (!retryStrategy.shouldRetry(attempt, e)) {
                        throw RetryExhaustedException("모든 재시도 실패", e)
                    }

                    val delay = retryStrategy.nextDelay(attempt)
                    println("재시도 $attempt: $delay 후 다시 시도")
                    delay(delay)
                    attempt++
                }
            }
        }
    }

    class RetryExhaustedException(message: String, cause: Throwable? = null) : Exception(message, cause)

    init {
        1.2.pow(1)
    }
}

suspend fun main() {
    val strategies = listOf(
        Solution.ExponentialBackoffStrategy(),
        Solution.ImmediateRetryStrategy()
    )

    strategies.forEachIndexed { index, strategy ->
        println("\n전략 ${index + 1} 테스트:")
        val client = Solution.RetryableClient(Problem.NetworkService(), strategy)

        repeat(5) { requestIndex ->
            try {
                val result = client.fetchDataWithRetry("request-$requestIndex")
                println("성공: $result")
            } catch (e: Solution.RetryExhaustedException) {
                println("최종 실패: ${e.message}")
            }
            delay(1000) // 각 요청 사이에 지연
        }
    }
}