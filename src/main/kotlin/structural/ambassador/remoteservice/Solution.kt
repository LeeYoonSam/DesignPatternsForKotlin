package structural.ambassador.remoteservice

/**
 * Ambassador Pattern - Solution
 *
 * Ambassador Pattern은 원격 서비스 호출 시 발생하는 횡단 관심사를
 * 별도의 프록시(Ambassador)로 분리하는 패턴입니다.
 *
 * 대사(Ambassador)가 본국을 대리하여 외국과 소통하듯,
 * Ambassador가 애플리케이션을 대리하여 원격 서비스와 통신합니다.
 *
 * 핵심 아이디어:
 * 1. 비즈니스 로직에서 네트워크 관심사를 완전히 분리
 * 2. 로깅, 재시도, 인증, 서킷브레이커 등을 Ambassador에 위임
 * 3. 레거시 서비스에 새로운 기능을 투명하게 추가
 * 4. 플러그인 방식으로 기능 조합 가능
 */

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ========================================
// Remote Service Interface
// ========================================

/**
 * 원격 서비스 호출을 위한 공통 인터페이스
 */
interface RemoteService {
    fun call(request: ServiceRequest): ServiceResponse
}

data class ServiceRequest(
    val method: String,             // GET, POST, PUT, DELETE
    val path: String,               // /orders/123
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

data class ServiceResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val latencyMs: Long = 0
)

// ========================================
// Actual Remote Service (시뮬레이션)
// ========================================

class HttpRemoteService(
    private val baseUrl: String,
    private val failureRate: Double = 0.2
) : RemoteService {

    override fun call(request: ServiceRequest): ServiceResponse {
        // 네트워크 지연 시뮬레이션
        val latency = (50..300).random().toLong()
        Thread.sleep(latency.coerceAtMost(100))

        // 장애 시뮬레이션
        if (Math.random() < failureRate) {
            throw RuntimeException("Connection timeout: $baseUrl${request.path}")
        }

        return ServiceResponse(
            statusCode = 200,
            body = """{"path":"${request.path}","status":"ok","from":"$baseUrl"}""",
            latencyMs = latency
        )
    }
}

// ========================================
// Ambassador: 횡단 관심사를 처리하는 프록시
// ========================================

/**
 * Ambassador Feature: 플러그인 방식의 기능 인터페이스
 */
interface AmbassadorFeature {
    val name: String
    val order: Int get() = 0  // 실행 순서 (낮을수록 먼저)

    /** 요청 전처리 */
    fun onRequest(request: ServiceRequest): ServiceRequest = request

    /** 응답 후처리 */
    fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse = response

    /** 에러 처리, null 반환 시 에러를 상위로 전파 */
    fun onError(request: ServiceRequest, error: Exception): ServiceResponse? = null
}

/**
 * Ambassador: 원격 서비스 앞에 위치하는 프록시
 *
 * 여러 AmbassadorFeature를 조합하여 횡단 관심사를 처리
 */
class Ambassador(
    private val target: RemoteService,
    private val features: List<AmbassadorFeature> = emptyList()
) : RemoteService {

    private val sortedFeatures = features.sortedBy { it.order }

    override fun call(request: ServiceRequest): ServiceResponse {
        // 1. 요청 전처리 (Feature 파이프라인)
        var processedRequest = request
        for (feature in sortedFeatures) {
            processedRequest = feature.onRequest(processedRequest)
        }

        // 2. 실제 원격 서비스 호출
        return try {
            var response = target.call(processedRequest)

            // 3. 응답 후처리 (역순)
            for (feature in sortedFeatures.reversed()) {
                response = feature.onResponse(processedRequest, response)
            }

            response
        } catch (e: Exception) {
            // 4. 에러 처리
            for (feature in sortedFeatures) {
                val recovery = feature.onError(processedRequest, e)
                if (recovery != null) return recovery
            }
            throw e
        }
    }
}

// ========================================
// Ambassador Features 구현
// ========================================

/**
 * Feature 1: 로깅
 */
class LoggingFeature : AmbassadorFeature {
    override val name = "Logging"
    override val order = 1

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        val requestId = request.metadata["requestId"] ?: UUID.randomUUID().toString().take(8)
        println("  [LOG] → ${request.method} ${request.path} (id=$requestId)")
        request.metadata["requestId"] = requestId
        request.metadata["startTime"] = System.currentTimeMillis()
        return request
    }

    override fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse {
        val startTime = request.metadata["startTime"] as? Long ?: 0
        val duration = System.currentTimeMillis() - startTime
        val requestId = request.metadata["requestId"]
        println("  [LOG] ← ${response.statusCode} (${duration}ms, id=$requestId)")
        return response
    }

    override fun onError(request: ServiceRequest, error: Exception): ServiceResponse? {
        val requestId = request.metadata["requestId"]
        println("  [LOG] ✗ Error: ${error.message} (id=$requestId)")
        return null // 에러를 상위로 전파
    }
}

/**
 * Feature 2: 인증 헤더 자동 추가
 */
class AuthenticationFeature(
    private val tokenProvider: () -> String
) : AmbassadorFeature {
    override val name = "Authentication"
    override val order = 2

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        val token = tokenProvider()
        val newHeaders = request.headers + mapOf(
            "Authorization" to "Bearer $token",
            "X-Request-Id" to UUID.randomUUID().toString()
        )
        println("  [AUTH] 인증 토큰 추가됨")
        return request.copy(headers = newHeaders)
    }
}

/**
 * Feature 3: 재시도 (Retry with Exponential Backoff)
 */
class RetryFeature(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 100
) : AmbassadorFeature {
    override val name = "Retry"
    override val order = 10  // 다른 feature 이후에 적용

    private val retryCount = AtomicInteger(0)

    override fun onError(request: ServiceRequest, error: Exception): ServiceResponse? {
        val currentRetry = retryCount.incrementAndGet()

        if (currentRetry <= maxRetries) {
            val delay = baseDelayMs * (1 shl (currentRetry - 1))  // 지수 백오프
            println("  [RETRY] 시도 $currentRetry/$maxRetries (${delay}ms 대기)")
            Thread.sleep(delay.coerceAtMost(500))

            // 재시도는 Ambassador 레벨에서 처리되므로 null 반환
            // 실제 구현에서는 Ambassador가 재시도 로직을 직접 처리
            return null
        }

        retryCount.set(0)
        println("  [RETRY] 최대 재시도 초과")
        return null
    }
}

/**
 * Feature 4: 서킷브레이커
 */
class CircuitBreakerFeature(
    private val failureThreshold: Int = 5,
    private val recoveryTimeMs: Long = 10000
) : AmbassadorFeature {
    override val name = "CircuitBreaker"
    override val order = 0  // 가장 먼저 실행 (빠른 실패)

    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state: State = State.CLOSED

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        when (state) {
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - lastFailureTime.get()
                if (elapsed > recoveryTimeMs) {
                    state = State.HALF_OPEN
                    println("  [CB] 상태: HALF_OPEN (복구 시도)")
                } else {
                    println("  [CB] ⚡ 회로 열림 - 요청 차단")
                    throw CircuitBreakerOpenException("서킷브레이커 OPEN 상태")
                }
            }
            State.HALF_OPEN -> println("  [CB] 상태: HALF_OPEN (테스트 요청)")
            State.CLOSED -> { /* 정상 */ }
        }
        return request
    }

    override fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED
            failureCount.set(0)
            println("  [CB] 복구 성공 → CLOSED")
        }
        return response
    }

    override fun onError(request: ServiceRequest, error: Exception): ServiceResponse? {
        if (error is CircuitBreakerOpenException) {
            return ServiceResponse(503, """{"error":"Service Unavailable","reason":"Circuit Breaker Open"}""")
        }

        val failures = failureCount.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())

        if (failures >= failureThreshold) {
            state = State.OPEN
            println("  [CB] 연속 실패 $failures 회 → OPEN")
        }

        return null
    }

    fun getState(): State = state
}

class CircuitBreakerOpenException(message: String) : RuntimeException(message)

/**
 * Feature 5: 모니터링 / 메트릭 수집
 */
class MetricsFeature : AmbassadorFeature {
    override val name = "Metrics"
    override val order = 3

    private val requestCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val totalLatency = AtomicLong(0)

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        requestCount.incrementAndGet()
        request.metadata["metricsStart"] = System.currentTimeMillis()
        return request
    }

    override fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse {
        successCount.incrementAndGet()
        val startTime = request.metadata["metricsStart"] as? Long ?: 0
        totalLatency.addAndGet(System.currentTimeMillis() - startTime)
        return response
    }

    override fun onError(request: ServiceRequest, error: Exception): ServiceResponse? {
        failureCount.incrementAndGet()
        return null
    }

    fun getStats(): Map<String, Any> = mapOf(
        "totalRequests" to requestCount.get(),
        "successCount" to successCount.get(),
        "failureCount" to failureCount.get(),
        "avgLatencyMs" to if (successCount.get() > 0) totalLatency.get() / successCount.get() else 0
    )
}

/**
 * Feature 6: 캐싱
 */
class CachingFeature(
    private val ttlMs: Long = 5000
) : AmbassadorFeature {
    override val name = "Caching"
    override val order = 5

    private data class CacheEntry(val response: ServiceResponse, val cachedAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        if (request.method != "GET") return request

        val key = "${request.method}:${request.path}"
        val entry = cache[key]

        if (entry != null && System.currentTimeMillis() - entry.cachedAt < ttlMs) {
            println("  [CACHE] HIT: $key")
            request.metadata["cacheHit"] = true
            request.metadata["cachedResponse"] = entry.response
        } else {
            println("  [CACHE] MISS: $key")
        }

        return request
    }

    override fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse {
        // 캐시 히트인 경우 캐시된 응답 반환
        val cachedResponse = request.metadata["cachedResponse"] as? ServiceResponse
        if (cachedResponse != null) return cachedResponse

        // GET 요청 응답 캐싱
        if (request.method == "GET" && response.statusCode == 200) {
            val key = "${request.method}:${request.path}"
            cache[key] = CacheEntry(response, System.currentTimeMillis())
            println("  [CACHE] STORED: $key")
        }

        return response
    }
}

/**
 * Feature 7: 레이트리미터
 */
class RateLimitFeature(
    private val maxRequestsPerSecond: Int = 10
) : AmbassadorFeature {
    override val name = "RateLimit"
    override val order = -1  // 가장 먼저 실행

    private val requestTimestamps = mutableListOf<Long>()

    override fun onRequest(request: ServiceRequest): ServiceRequest {
        val now = System.currentTimeMillis()

        synchronized(requestTimestamps) {
            // 1초 이전 타임스탬프 제거
            requestTimestamps.removeAll { now - it > 1000 }

            if (requestTimestamps.size >= maxRequestsPerSecond) {
                println("  [RATE] ⚡ 요청 제한 초과 (${requestTimestamps.size}/$maxRequestsPerSecond)")
                throw RateLimitExceededException("Rate limit exceeded")
            }

            requestTimestamps.add(now)
            println("  [RATE] 요청 허용 (${requestTimestamps.size}/$maxRequestsPerSecond)")
        }

        return request
    }

    override fun onError(request: ServiceRequest, error: Exception): ServiceResponse? {
        if (error is RateLimitExceededException) {
            return ServiceResponse(429, """{"error":"Too Many Requests"}""")
        }
        return null
    }
}

class RateLimitExceededException(message: String) : RuntimeException(message)

// ========================================
// Ambassador Builder (편의 생성)
// ========================================

class AmbassadorBuilder(private val target: RemoteService) {
    private val features = mutableListOf<AmbassadorFeature>()

    fun withLogging(): AmbassadorBuilder {
        features.add(LoggingFeature())
        return this
    }

    fun withAuthentication(tokenProvider: () -> String): AmbassadorBuilder {
        features.add(AuthenticationFeature(tokenProvider))
        return this
    }

    fun withRetry(maxRetries: Int = 3, baseDelayMs: Long = 100): AmbassadorBuilder {
        features.add(RetryFeature(maxRetries, baseDelayMs))
        return this
    }

    fun withCircuitBreaker(failureThreshold: Int = 5): AmbassadorBuilder {
        features.add(CircuitBreakerFeature(failureThreshold))
        return this
    }

    fun withMetrics(): AmbassadorBuilder {
        features.add(MetricsFeature())
        return this
    }

    fun withCaching(ttlMs: Long = 5000): AmbassadorBuilder {
        features.add(CachingFeature(ttlMs))
        return this
    }

    fun withRateLimit(maxRequestsPerSecond: Int = 10): AmbassadorBuilder {
        features.add(RateLimitFeature(maxRequestsPerSecond))
        return this
    }

    fun withFeature(feature: AmbassadorFeature): AmbassadorBuilder {
        features.add(feature)
        return this
    }

    fun build(): Ambassador = Ambassador(target, features)
}

fun RemoteService.withAmbassador(): AmbassadorBuilder = AmbassadorBuilder(this)

// ========================================
// Clean Service Clients (비즈니스 로직만 포함)
// ========================================

/**
 * 깔끔한 주문 서비스 클라이언트
 *
 * 비즈니스 로직만 포함, 네트워크 관심사는 Ambassador가 처리
 */
class CleanOrderServiceClient(private val service: RemoteService) {

    fun getOrder(orderId: String): String {
        val response = service.call(
            ServiceRequest(method = "GET", path = "/orders/$orderId")
        )
        return response.body
    }

    fun createOrder(customerId: String, productId: String, quantity: Int): String {
        val body = """{"customerId":"$customerId","productId":"$productId","quantity":$quantity}"""
        val response = service.call(
            ServiceRequest(method = "POST", path = "/orders", body = body)
        )
        return response.body
    }
}

/**
 * 깔끔한 결제 서비스 클라이언트
 */
class CleanPaymentServiceClient(private val service: RemoteService) {

    fun processPayment(orderId: String, amount: Double): String {
        val body = """{"orderId":"$orderId","amount":$amount}"""
        val response = service.call(
            ServiceRequest(method = "POST", path = "/payments", body = body)
        )
        return response.body
    }
}

/**
 * 레거시 서비스에 Ambassador 적용
 * - 레거시 코드 수정 없이 인증/로깅 추가
 */
class LegacyInventoryService : RemoteService {
    override fun call(request: ServiceRequest): ServiceResponse {
        // 레거시 로직 (변경 불가)
        println("  [레거시] 재고 서비스 처리: ${request.path}")
        return ServiceResponse(200, """{"stock":100}""")
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║              Ambassador Pattern - 원격 서비스 데모            ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    // === 1. Ambassador 구성 ===
    println("=== 1. Ambassador를 통한 주문 서비스 호출 ===")
    val orderRemoteService = HttpRemoteService("https://order-api.example.com", failureRate = 0.0)

    val metricsFeature = MetricsFeature()
    val orderAmbassador = orderRemoteService.withAmbassador()
        .withLogging()
        .withAuthentication { "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" }
        .withFeature(metricsFeature)
        .withCaching(ttlMs = 3000)
        .build()

    val orderClient = CleanOrderServiceClient(orderAmbassador)
    println("[Client] 주문 조회:")
    val order = orderClient.getOrder("order-123")
    println("[Client] 결과: $order")
    println()

    // === 2. 캐시 히트 확인 ===
    println("=== 2. 캐시 동작 확인 (동일 요청 재호출) ===")
    val cachedOrder = orderClient.getOrder("order-123")
    println("[Client] 캐시된 결과: $cachedOrder")
    println()

    // === 3. 주문 생성 (POST - 캐시 안됨) ===
    println("=== 3. 주문 생성 (POST 요청) ===")
    val newOrder = orderClient.createOrder("cust-001", "prod-001", 3)
    println("[Client] 생성 결과: $newOrder")
    println()

    // === 4. 서킷브레이커 동작 ===
    println("=== 4. 서킷브레이커 동작 확인 ===")
    val unreliableService = HttpRemoteService("https://unstable-api.example.com", failureRate = 1.0)
    val cbFeature = CircuitBreakerFeature(failureThreshold = 3, recoveryTimeMs = 5000)

    val cbAmbassador = Ambassador(
        target = unreliableService,
        features = listOf(LoggingFeature(), cbFeature)
    )

    for (i in 1..5) {
        try {
            val response = cbAmbassador.call(ServiceRequest("GET", "/health"))
            println("[Client] 응답: ${response.body}")
        } catch (e: Exception) {
            println("[Client] 실패 #$i: ${e.message}")
        }
    }
    println("서킷브레이커 상태: ${cbFeature.getState()}")
    println()

    // === 5. 레거시 서비스에 Ambassador 적용 ===
    println("=== 5. 레거시 서비스에 Ambassador 적용 ===")
    val legacyService = LegacyInventoryService()

    val legacyAmbassador = legacyService.withAmbassador()
        .withLogging()
        .withAuthentication { "legacy-service-token-2024" }
        .withRateLimit(maxRequestsPerSecond = 5)
        .build()

    // 레거시 코드 수정 없이 인증/로깅/레이트리미팅 추가
    println("[Client] 레거시 재고 서비스 호출 (Ambassador 경유):")
    val stockResponse = legacyAmbassador.call(ServiceRequest("GET", "/inventory/prod-001"))
    println("[Client] 결과: ${stockResponse.body}")
    println()

    // === 6. 메트릭 확인 ===
    println("=== 6. 메트릭 통계 ===")
    println("주문 서비스 메트릭: ${metricsFeature.getStats()}")
    println()

    // === 7. 다양한 구성 비교 ===
    println("=== 7. 용도별 Ambassador 구성 ===")
    println()

    println("[내부 서비스] 로깅 + 메트릭만:")
    val internalService = HttpRemoteService("http://internal-api:8080", failureRate = 0.0)
    val internalAmbassador = internalService.withAmbassador()
        .withLogging()
        .withMetrics()
        .build()
    internalAmbassador.call(ServiceRequest("GET", "/internal/status"))
    println()

    println("[외부 서비스] 풀 스택 Ambassador:")
    val externalService = HttpRemoteService("https://external-api.com", failureRate = 0.0)
    val externalAmbassador = externalService.withAmbassador()
        .withRateLimit(5)
        .withCircuitBreaker(5)
        .withLogging()
        .withAuthentication { "external-api-key" }
        .withMetrics()
        .withRetry(3)
        .withCaching(10000)
        .build()
    externalAmbassador.call(ServiceRequest("GET", "/external/data"))
    println()

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                  Ambassador Pattern 장점                     ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 관심사 분리: 비즈니스 로직 ↔ 네트워크 관심사 완전 분리   ║")
    println("║ 2. 재사용성: 동일 Ambassador를 여러 서비스에 적용           ║")
    println("║ 3. 레거시 호환: 코드 수정 없이 새 기능 추가                ║")
    println("║ 4. 조합 가능: 필요한 Feature만 선택적으로 조합             ║")
    println("║ 5. 정책 통합: 통신 정책 변경 시 Ambassador만 수정          ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
