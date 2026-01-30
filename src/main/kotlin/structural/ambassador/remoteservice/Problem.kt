package structural.ambassador.remoteservice

/**
 * Ambassador Pattern - Problem
 *
 * 이 파일은 Ambassador Pattern을 적용하지 않았을 때
 * 원격 서비스 호출에서 발생하는 횡단 관심사 문제를 보여줍니다.
 *
 * 문제점:
 * 1. 횡단 관심사(로깅, 재시도, 인증, 모니터링)가 비즈니스 로직에 혼재
 * 2. 모든 서비스 클라이언트에서 동일한 네트워크 처리 코드 중복
 * 3. 레거시 서비스에 새로운 기능(인증, 암호화) 추가가 어려움
 * 4. 서비스 간 통신 정책 변경 시 모든 클라이언트 수정 필요
 * 5. 테스트 시 실제 원격 서비스 의존성 제거 어려움
 */

import java.time.LocalDateTime

// ========================================
// 문제: 횡단 관심사가 비즈니스 로직에 혼재
// ========================================

/**
 * 문제 1: 모든 네트워크 관심사가 서비스 클라이언트에 포함
 *
 * 재시도, 로깅, 인증, 타임아웃, 서킷브레이커 로직이
 * 비즈니스 로직과 뒤섞여 있음
 */
class OrderServiceClient {
    private val maxRetries = 3
    private val apiKey = "sk-legacy-api-key-12345"
    private var consecutiveFailures = 0
    private var circuitOpen = false

    fun getOrder(orderId: String): String {
        // 로깅 - 비즈니스 로직과 무관
        println("[${LocalDateTime.now()}] 주문 조회 요청: $orderId")

        // 서킷브레이커 - 비즈니스 로직과 무관
        if (circuitOpen) {
            println("[Circuit Breaker] 회로 열림 - 요청 차단")
            return "서비스 일시 중단"
        }

        // 재시도 로직 - 비즈니스 로직과 무관
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                // 인증 헤더 추가 - 비즈니스 로직과 무관
                val headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "X-Request-Id" to java.util.UUID.randomUUID().toString(),
                    "X-Timestamp" to LocalDateTime.now().toString()
                )

                // 타임아웃 설정 - 비즈니스 로직과 무관
                val startTime = System.currentTimeMillis()

                // 실제 비즈니스 로직: 원격 API 호출 (시뮬레이션)
                val response = callRemoteApi("/orders/$orderId", headers)

                // 응답 시간 측정 - 비즈니스 로직과 무관
                val duration = System.currentTimeMillis() - startTime
                println("[Metrics] 응답 시간: ${duration}ms")

                consecutiveFailures = 0
                return response
            } catch (e: Exception) {
                lastException = e
                println("[Retry] 시도 $attempt/$maxRetries 실패: ${e.message}")

                // 지수 백오프 - 비즈니스 로직과 무관
                Thread.sleep((attempt * 100).toLong())
            }
        }

        // 장애 카운트 - 비즈니스 로직과 무관
        consecutiveFailures++
        if (consecutiveFailures >= 5) {
            circuitOpen = true
            println("[Circuit Breaker] 연속 실패 $consecutiveFailures 회 - 회로 열림")
        }

        throw RuntimeException("주문 조회 실패: ${lastException?.message}")
    }

    private fun callRemoteApi(path: String, headers: Map<String, String>): String {
        // 원격 API 호출 시뮬레이션
        if (Math.random() < 0.3) throw RuntimeException("네트워크 타임아웃")
        return """{"orderId": "order-123", "status": "CONFIRMED"}"""
    }
}

/**
 * 문제 2: 다른 서비스 클라이언트에서도 동일한 코드 중복
 *
 * 재시도, 로깅, 인증 로직이 완전히 동일하게 복사됨
 */
class PaymentServiceClient {
    private val maxRetries = 3
    private val apiKey = "sk-legacy-api-key-12345"
    private var consecutiveFailures = 0
    private var circuitOpen = false

    fun processPayment(orderId: String, amount: Double): String {
        // 동일한 로깅 로직 중복
        println("[${LocalDateTime.now()}] 결제 처리 요청: $orderId, $amount")

        // 동일한 서킷브레이커 중복
        if (circuitOpen) {
            return "서비스 일시 중단"
        }

        // 동일한 재시도 로직 중복
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                // 동일한 인증 헤더 중복
                val headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "X-Request-Id" to java.util.UUID.randomUUID().toString()
                )

                val response = callRemoteApi("/payments", headers)
                consecutiveFailures = 0
                return response
            } catch (e: Exception) {
                lastException = e
                println("[Retry] 시도 $attempt/$maxRetries 실패: ${e.message}")
                Thread.sleep((attempt * 100).toLong())
            }
        }

        consecutiveFailures++
        if (consecutiveFailures >= 5) circuitOpen = true

        throw RuntimeException("결제 처리 실패: ${lastException?.message}")
    }

    private fun callRemoteApi(path: String, headers: Map<String, String>): String {
        if (Math.random() < 0.3) throw RuntimeException("네트워크 타임아웃")
        return """{"paymentId": "pay-456", "status": "SUCCESS"}"""
    }
}

/**
 * 문제 3: 레거시 서비스에 새로운 정책 추가 어려움
 *
 * 레거시 서비스가 인증/암호화를 지원하지 않지만
 * 보안 정책이 변경되어 추가해야 하는 상황
 */
class LegacyInventoryClient {
    // 레거시: 인증 없이 호출
    fun checkStock(productId: String): Int {
        println("[레거시] 재고 조회: $productId (인증 없음!)")
        // 보안 취약점: 인증 없이 접근
        return 100
    }

    // 레거시: 암호화 없이 통신
    fun updateStock(productId: String, quantity: Int) {
        println("[레거시] 재고 업데이트: $productId = $quantity (암호화 없음!)")
        // 보안 취약점: 평문 통신
    }
}

/**
 * 문제 4: 서비스 통신 정책 변경 시 모든 클라이언트 수정 필요
 */
class PolicyChangeProblem {
    fun demonstrate() {
        println("=== 통신 정책 변경 시나리오 ===")
        println()
        println("변경 1: API 키 → OAuth2 토큰 전환")
        println("  → OrderServiceClient 수정 필요")
        println("  → PaymentServiceClient 수정 필요")
        println("  → 모든 서비스 클라이언트 수정 필요")
        println()
        println("변경 2: 재시도 정책 변경 (3회 → 5회, 지수백오프 → 고정간격)")
        println("  → 모든 서비스 클라이언트에서 동일하게 수정")
        println()
        println("변경 3: 새로운 모니터링 헤더 추가")
        println("  → 모든 서비스 클라이언트에서 헤더 추가")
        println()
        println("문제: 변경이 많은 곳에 분산되어 누락/불일치 위험")
    }
}

/**
 * 문제점 요약:
 *
 * 1. 횡단 관심사 혼재
 *    - 로깅, 재시도, 인증, 모니터링이 비즈니스 로직에 포함
 *    - 단일 책임 원칙 위반
 *
 * 2. 코드 중복
 *    - 모든 서비스 클라이언트에서 동일한 네트워크 처리 로직 반복
 *    - DRY 원칙 위반
 *
 * 3. 레거시 호환성
 *    - 레거시 서비스에 새로운 정책 적용 어려움
 *    - 코드 수정이 불가능한 외부 라이브러리 문제
 *
 * 4. 유지보수 어려움
 *    - 정책 변경 시 모든 클라이언트 수정 필요
 *    - 누락/불일치 위험
 *
 * Ambassador Pattern으로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 구현을 확인하세요.
 */

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║          Ambassador Pattern 적용 전 문제점 데모               ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    println("--- 1. 횡단 관심사가 혼재된 서비스 클라이언트 ---")
    val orderClient = OrderServiceClient()
    try {
        orderClient.getOrder("order-123")
    } catch (e: Exception) {
        println("에러: ${e.message}")
    }
    println()

    println("--- 2. 동일한 로직이 중복된 또 다른 클라이언트 ---")
    val paymentClient = PaymentServiceClient()
    try {
        paymentClient.processPayment("order-123", 50000.0)
    } catch (e: Exception) {
        println("에러: ${e.message}")
    }
    println()

    println("--- 3. 레거시 서비스의 보안 취약점 ---")
    val legacyClient = LegacyInventoryClient()
    legacyClient.checkStock("prod-001")
    legacyClient.updateStock("prod-001", 50)
    println()

    println("--- 4. 정책 변경의 어려움 ---")
    PolicyChangeProblem().demonstrate()
    println()

    println("Solution.kt에서 Ambassador Pattern을 적용한 해결책을 확인하세요.")
}
