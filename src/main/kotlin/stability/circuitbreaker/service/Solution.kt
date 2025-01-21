package stability.circuitbreaker.service

import stability.circuitbreaker.service.Problem.ServiceException

class Solution {
    // 불안정한 외부 서비스
    class UnstableRemoteService {
        private var failureCount = 0

        fun call(): String {
            if (failureCount++ % 3 == 0) {
                throw ServiceException("Service temporarily unavailable")
            }
            return "Service Response"
        }
    }

    // 서킷 브레이커 상태
    enum class State {
        CLOSED,     // 정상 동작
        OPEN,       // 차단됨
        HALF_OPEN   // 시험 동작
    }

    // 서킷 브레이커 설정
    data class CircuitBreakerConfig(
        val failureThreshold: Int = 5,          // 실패 임계값
        val resetTimeout: Long = 60000,         // 리셋 타임아웃(ms)
        val halfOpenMaxCalls: Int = 3,          // Half-Open 상태에서 허용할 최대 호출 수
        val halfOpenSuccessThreshold: Int = 2   // Half-Open 상태에서 성공 필요 횟수
    )

    // 서킷 브레이커 구현
    class CircuitBreaker(
        private val config: CircuitBreakerConfig = CircuitBreakerConfig()
    ) {
        private var state = State.CLOSED
        private var failureCount = 0
        private var lastFailureTime: Long = 0
        private var halfOpenCallCount = 0
        private var halfOpenSuccessCount = 0

        @Synchronized
        fun recordSuccess() {
            when (state) {
                State.CLOSED -> {
                    failureCount = 0
                }
                State.HALF_OPEN -> {
                    halfOpenSuccessCount++
                    if (halfOpenSuccessCount >= config.halfOpenSuccessThreshold) {
                        state = State.CLOSED
                        resetCounts()
                    }
                }
                else -> {}
            }
        }

        @Synchronized
        fun recordFailure() {
            lastFailureTime = System.currentTimeMillis()
            when (state) {
                State.CLOSED -> {
                    failureCount++
                    if (failureCount >= config.failureThreshold) {
                        state = State.OPEN
                    }
                }
                State.HALF_OPEN -> {
                    state = State.OPEN
                    resetCounts()
                }
                else -> {}
            }
        }

        @Synchronized
        fun isAllowingRequests(): Boolean {
            when (state) {
                State.CLOSED -> return true
                State.OPEN -> {
                    if (System.currentTimeMillis() - lastFailureTime >= config.resetTimeout) {
                        state = State.HALF_OPEN
                        resetCounts()
                        return true
                    }
                    return false
                }
                State.HALF_OPEN -> {
                    if (halfOpenCallCount < config.halfOpenMaxCalls) {
                        halfOpenSuccessCount++
                        return true
                    }
                    return false
                }
            }
        }

        private fun resetCounts() {
            failureCount = 0
            halfOpenCallCount = 0
            halfOpenSuccessCount = 0
        }

        fun getState() = state
    }

    // 서킷 브레이커를 사용하는 서비스 호출자
    class CircuitBreakerServiceCaller(
        private val service: UnstableRemoteService,
        private val circuitBreaker: CircuitBreaker
    ) {
        fun executeCall(): String {
            if (!circuitBreaker.isAllowingRequests()) {
                return "Circuit Breaker is OPEN - Fast Fail"
            }

            try {
                val result = service.call()
                circuitBreaker.recordSuccess()
                return result
            } catch (e: ServiceException) {
                circuitBreaker.recordFailure()
                return "Service Failed: ${e.message}"
            }
        }
    }
}

fun main() {
    val unstableService = Solution.UnstableRemoteService()

    println("\nWith Circuit Breaker:")
    // 서킷 브레이커를 사용한 호출
    val circuitBreaker = Solution.CircuitBreaker(
        Solution.CircuitBreakerConfig(
            failureThreshold = 1,
            resetTimeout = 3000,
            halfOpenMaxCalls = 2,
            halfOpenSuccessThreshold = 2
        )
    )
    val protectedCaller = Solution.CircuitBreakerServiceCaller(unstableService, circuitBreaker)

    repeat(6) {
        println("Call ${it + 1}: ${protectedCaller.executeCall()}")
        println("Circuit Breaker State: ${circuitBreaker.getState()}")
        Thread.sleep(1000) // 호출 간 간격
    }
}