package architecture.gracefulshutdown.server

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Graceful Shutdown Pattern - 해결책
 *
 * 서버 애플리케이션의 안전한 종료를 구현:
 * - ShutdownHook: JVM 종료 신호(SIGTERM) 감지
 * - ShutdownPhase: 단계별 순서대로 종료
 * - LifecycleAware: 컴포넌트의 시작/종료 생명주기
 * - RequestDrainer: 진행 중 요청 완료 대기
 * - HealthCheck: 종료 시 헬스 체크 변경
 *
 * 핵심 구성:
 * - ShutdownManager: 종료 프로세스 관리자
 * - ShutdownPhase: PRE_SHUTDOWN → DRAIN → CLOSE → POST_SHUTDOWN
 * - LifecycleComponent: 시작/종료 인터페이스
 * - RequestTracker: 진행 중 요청 추적
 * - GracefulServer: 안전한 종료를 지원하는 서버
 */

// ============================================================
// 1. Shutdown Phase (종료 단계)
// ============================================================

/**
 * 종료 단계 - 순서대로 실행
 */
enum class ShutdownPhase(val order: Int, val description: String) {
    /** Health Check를 unhealthy로 변경, 새 요청 거부 시작 */
    PRE_SHUTDOWN(0, "Mark unhealthy, stop accepting new requests"),

    /** 진행 중인 요청 완료 대기 */
    DRAIN(1, "Wait for in-flight requests to complete"),

    /** 리소스 정리 (역순: 서비스 → 캐시 → DB) */
    CLOSE_SERVICES(2, "Close application services"),
    CLOSE_CACHES(3, "Flush and close caches"),
    CLOSE_CONNECTIONS(4, "Close database and external connections"),

    /** 최종 정리 */
    POST_SHUTDOWN(5, "Final cleanup and logging")
}

// ============================================================
// 2. Lifecycle Component (생명주기 인터페이스)
// ============================================================

/**
 * 생명주기를 가진 컴포넌트
 */
interface LifecycleComponent {
    val componentName: String
    val shutdownPhase: ShutdownPhase

    /** 컴포넌트 시작 */
    fun start()

    /** 컴포넌트 종료 (타임아웃 내 완료해야 함) */
    fun stop(timeout: Long = 5000)

    /** 현재 상태 */
    fun isRunning(): Boolean
}

/**
 * 기본 구현
 */
abstract class AbstractLifecycleComponent(
    override val componentName: String,
    override val shutdownPhase: ShutdownPhase
) : LifecycleComponent {
    protected val running = AtomicBoolean(false)

    override fun start() {
        running.set(true)
        println("[Lifecycle] $componentName started")
    }

    override fun stop(timeout: Long) {
        running.set(false)
        println("[Lifecycle] $componentName stopped")
    }

    override fun isRunning(): Boolean = running.get()
}

// ============================================================
// 3. Request Tracker (요청 추적기)
// ============================================================

/**
 * 진행 중 요청 추적
 */
class RequestTracker {
    private val activeRequests = AtomicInteger(0)
    private val accepting = AtomicBoolean(true)
    private val drainLatch = CountDownLatch(1)

    /** 현재 활성 요청 수 */
    fun activeCount(): Int = activeRequests.get()

    /** 새 요청 수락 여부 */
    fun isAccepting(): Boolean = accepting.get()

    /**
     * 요청 시작 등록
     * @return true면 요청 처리 가능, false면 거부해야 함
     */
    fun tryAcquire(): Boolean {
        if (!accepting.get()) return false

        activeRequests.incrementAndGet()

        // Double-check: acquire 후 accepting이 false가 되었으면 롤백
        if (!accepting.get()) {
            activeRequests.decrementAndGet()
            checkDrained()
            return false
        }

        return true
    }

    /** 요청 완료 등록 */
    fun release() {
        val remaining = activeRequests.decrementAndGet()
        if (remaining == 0 && !accepting.get()) {
            checkDrained()
        }
    }

    /** 새 요청 수락 중단 */
    fun stopAccepting() {
        accepting.set(false)
        println("[RequestTracker] Stopped accepting new requests")
        if (activeRequests.get() == 0) {
            drainLatch.countDown()
        }
    }

    /** 모든 진행 중 요청 완료 대기 */
    fun awaitDrain(timeout: Long, unit: TimeUnit): Boolean {
        println("[RequestTracker] Waiting for ${activeRequests.get()} active requests to complete...")
        return drainLatch.await(timeout, unit)
    }

    private fun checkDrained() {
        if (activeRequests.get() <= 0 && !accepting.get()) {
            drainLatch.countDown()
        }
    }
}

// ============================================================
// 4. Health Check
// ============================================================

/**
 * 헬스 체크 상태
 */
enum class HealthStatus {
    HEALTHY,      // 정상 (트래픽 수신 가능)
    UNHEALTHY,    // 비정상 (트래픽 수신 불가)
    SHUTTING_DOWN // 종료 중 (새 트래픽 거부)
}

/**
 * 헬스 체크 엔드포인트
 */
class HealthCheck {
    private val status = AtomicReference(HealthStatus.HEALTHY)

    fun getStatus(): HealthStatus = status.get()

    fun markUnhealthy() {
        status.set(HealthStatus.UNHEALTHY)
        println("[HealthCheck] Status: UNHEALTHY")
    }

    fun markShuttingDown() {
        status.set(HealthStatus.SHUTTING_DOWN)
        println("[HealthCheck] Status: SHUTTING_DOWN")
    }

    /** /health 엔드포인트 응답 */
    fun healthResponse(): HealthResponse {
        val current = status.get()
        return HealthResponse(
            status = current.name,
            healthy = current == HealthStatus.HEALTHY,
            timestamp = System.currentTimeMillis()
        )
    }
}

data class HealthResponse(
    val status: String,
    val healthy: Boolean,
    val timestamp: Long
)

// ============================================================
// 5. Shutdown Manager (종료 관리자)
// ============================================================

/**
 * 종료 프로세스 관리자
 */
class ShutdownManager(
    private val shutdownTimeout: Long = 30_000, // 전체 종료 타임아웃
    private val drainTimeout: Long = 15_000,     // 요청 드레인 타임아웃
    private val preShutdownDelay: Long = 3_000    // 로드밸런서 인지 대기
) {
    private val components = CopyOnWriteArrayList<LifecycleComponent>()
    private val shutdownHooks = CopyOnWriteArrayList<() -> Unit>()
    private val requestTracker = RequestTracker()
    private val healthCheck = HealthCheck()
    private val shuttingDown = AtomicBoolean(false)

    fun getRequestTracker(): RequestTracker = requestTracker
    fun getHealthCheck(): HealthCheck = healthCheck

    /** 컴포넌트 등록 */
    fun register(component: LifecycleComponent) {
        components.add(component)
        println("[ShutdownManager] Registered: ${component.componentName} (phase: ${component.shutdownPhase})")
    }

    /** 종료 콜백 등록 */
    fun addShutdownHook(hook: () -> Unit) {
        shutdownHooks.add(hook)
    }

    /** JVM Shutdown Hook 등록 */
    fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            println("\n[ShutdownManager] SIGTERM received - starting graceful shutdown")
            shutdown()
        })
        println("[ShutdownManager] JVM shutdown hook installed")
    }

    /** 종료 프로세스 시작 */
    fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            println("[ShutdownManager] Shutdown already in progress")
            return
        }

        val startTime = System.currentTimeMillis()
        println("[ShutdownManager] === Graceful Shutdown Started ===")

        try {
            // Phase 0: PRE_SHUTDOWN
            executePhase(ShutdownPhase.PRE_SHUTDOWN) {
                preShutdown()
            }

            // Phase 1: DRAIN
            executePhase(ShutdownPhase.DRAIN) {
                drainRequests()
            }

            // Phase 2-4: CLOSE (단계별)
            closeComponents()

            // Phase 5: POST_SHUTDOWN
            executePhase(ShutdownPhase.POST_SHUTDOWN) {
                postShutdown()
            }

            // 사용자 정의 Hook 실행
            shutdownHooks.forEach { hook ->
                try {
                    hook()
                } catch (e: Exception) {
                    println("[ShutdownManager] Hook error: ${e.message}")
                }
            }

        } catch (e: Exception) {
            println("[ShutdownManager] Error during shutdown: ${e.message}")
        } finally {
            val elapsed = System.currentTimeMillis() - startTime
            println("[ShutdownManager] === Graceful Shutdown Completed in ${elapsed}ms ===")
        }
    }

    private fun executePhase(phase: ShutdownPhase, action: () -> Unit) {
        println("\n[Phase ${phase.order}] ${phase.name}: ${phase.description}")
        try {
            action()
        } catch (e: Exception) {
            println("[Phase ${phase.order}] ERROR: ${e.message}")
        }
    }

    /**
     * PRE_SHUTDOWN: 새 요청 거부 준비
     */
    private fun preShutdown() {
        // 1. Health Check를 SHUTTING_DOWN으로 변경
        healthCheck.markShuttingDown()

        // 2. 로드밸런서가 Health Check 실패를 감지할 시간 대기
        println("[PreShutdown] Waiting ${preShutdownDelay}ms for load balancer to detect...")
        Thread.sleep(preShutdownDelay)

        // 3. 새 요청 수락 중단
        requestTracker.stopAccepting()

        // PRE_SHUTDOWN 단계 컴포넌트 종료
        components.filter { it.shutdownPhase == ShutdownPhase.PRE_SHUTDOWN }
            .forEach { safeStop(it) }
    }

    /**
     * DRAIN: 진행 중 요청 완료 대기
     */
    private fun drainRequests() {
        val activeCount = requestTracker.activeCount()

        if (activeCount == 0) {
            println("[Drain] No active requests")
            return
        }

        println("[Drain] Waiting for $activeCount active requests (timeout: ${drainTimeout}ms)")

        val drained = requestTracker.awaitDrain(drainTimeout, TimeUnit.MILLISECONDS)

        if (drained) {
            println("[Drain] All requests completed successfully")
        } else {
            val remaining = requestTracker.activeCount()
            println("[Drain] TIMEOUT! $remaining requests still in progress - forcing shutdown")
        }
    }

    /**
     * CLOSE: 컴포넌트 역순 종료
     */
    private fun closeComponents() {
        // 단계별 그룹화 후 순서대로 종료
        val phaseOrder = listOf(
            ShutdownPhase.CLOSE_SERVICES,
            ShutdownPhase.CLOSE_CACHES,
            ShutdownPhase.CLOSE_CONNECTIONS
        )

        phaseOrder.forEach { phase ->
            val phaseComponents = components.filter { it.shutdownPhase == phase }
            if (phaseComponents.isNotEmpty()) {
                executePhase(phase) {
                    phaseComponents.forEach { safeStop(it) }
                }
            }
        }
    }

    /**
     * POST_SHUTDOWN: 최종 정리
     */
    private fun postShutdown() {
        components.filter { it.shutdownPhase == ShutdownPhase.POST_SHUTDOWN }
            .forEach { safeStop(it) }

        println("[PostShutdown] Final cleanup completed")
    }

    private fun safeStop(component: LifecycleComponent) {
        try {
            if (component.isRunning()) {
                println("  Stopping ${component.componentName}...")
                component.stop(5000)
            }
        } catch (e: Exception) {
            println("  ERROR stopping ${component.componentName}: ${e.message}")
        }
    }
}

// ============================================================
// 6. 구현 예시 - 컴포넌트들
// ============================================================

/** 데이터베이스 연결 풀 */
class DatabasePool(
    private val url: String,
    private val poolSize: Int
) : AbstractLifecycleComponent("DatabasePool", ShutdownPhase.CLOSE_CONNECTIONS) {

    private var activeConnections = 0

    override fun start() {
        super.start()
        activeConnections = poolSize
        println("[DB] Connected to $url with $poolSize connections")
    }

    override fun stop(timeout: Long) {
        println("[DB] Closing $activeConnections connections...")
        // 진행 중 쿼리 완료 대기
        Thread.sleep(100) // 시뮬레이션
        activeConnections = 0
        super.stop(timeout)
    }

    fun query(sql: String): String {
        if (!isRunning()) throw IllegalStateException("DB pool is closed")
        return "result"
    }
}

/** 캐시 서비스 */
class CacheService(
    private val host: String
) : AbstractLifecycleComponent("CacheService", ShutdownPhase.CLOSE_CACHES) {

    private val dirtyKeys = mutableSetOf<String>()

    override fun start() {
        super.start()
        println("[Cache] Connected to $host")
    }

    override fun stop(timeout: Long) {
        // 더티 데이터 플러시
        if (dirtyKeys.isNotEmpty()) {
            println("[Cache] Flushing ${dirtyKeys.size} dirty keys...")
            dirtyKeys.clear()
        }
        super.stop(timeout)
    }

    fun put(key: String, value: String) {
        dirtyKeys.add(key)
    }

    fun get(key: String): String? = if (isRunning()) "cached" else null
}

/** 메시지 큐 컨슈머 */
class MessageConsumer(
    private val topic: String
) : AbstractLifecycleComponent("MessageConsumer[$topic]", ShutdownPhase.CLOSE_SERVICES) {

    private val processingMessages = AtomicInteger(0)

    override fun start() {
        super.start()
        println("[MQ] Subscribed to topic: $topic")
    }

    override fun stop(timeout: Long) {
        // 새 메시지 수신 중단
        println("[MQ] Stopping consumption from $topic")

        // 처리 중인 메시지 완료 대기
        val deadline = System.currentTimeMillis() + timeout
        while (processingMessages.get() > 0 && System.currentTimeMillis() < deadline) {
            println("[MQ] Waiting for ${processingMessages.get()} messages to complete...")
            Thread.sleep(100)
        }

        if (processingMessages.get() > 0) {
            println("[MQ] WARNING: ${processingMessages.get()} messages not completed within timeout")
        } else {
            println("[MQ] All messages processed successfully")
        }

        super.stop(timeout)
    }

    fun processMessage(messageId: String) {
        processingMessages.incrementAndGet()
        try {
            Thread.sleep(50) // 시뮬레이션
        } finally {
            processingMessages.decrementAndGet()
        }
    }
}

/** 스케줄러 */
class TaskScheduler : AbstractLifecycleComponent("TaskScheduler", ShutdownPhase.CLOSE_SERVICES) {

    private var executor: ScheduledExecutorService? = null

    override fun start() {
        super.start()
        executor = Executors.newScheduledThreadPool(2)
        println("[Scheduler] Started with 2 threads")
    }

    override fun stop(timeout: Long) {
        executor?.let { exec ->
            println("[Scheduler] Shutting down executor...")

            // 새 작업 거부, 진행 중 작업은 완료 대기
            exec.shutdown()

            if (!exec.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                println("[Scheduler] Force shutting down remaining tasks")
                val dropped = exec.shutdownNow()
                println("[Scheduler] Dropped ${dropped.size} pending tasks")
            } else {
                println("[Scheduler] All tasks completed")
            }
        }
        super.stop(timeout)
    }
}

/** 메트릭 리포터 */
class MetricsReporter : AbstractLifecycleComponent("MetricsReporter", ShutdownPhase.POST_SHUTDOWN) {

    private val pendingMetrics = ConcurrentLinkedQueue<String>()

    override fun start() {
        super.start()
        println("[Metrics] Reporter started")
    }

    override fun stop(timeout: Long) {
        // 남은 메트릭 플러시
        if (pendingMetrics.isNotEmpty()) {
            println("[Metrics] Flushing ${pendingMetrics.size} pending metrics...")
            pendingMetrics.clear()
        }
        super.stop(timeout)
    }

    fun record(metric: String) {
        pendingMetrics.add(metric)
    }
}

// ============================================================
// 7. Graceful Server
// ============================================================

/**
 * Graceful Shutdown을 지원하는 서버
 */
class GracefulServer(
    private val port: Int,
    private val shutdownManager: ShutdownManager
) {
    private val requestTracker = shutdownManager.getRequestTracker()
    private val healthCheck = shutdownManager.getHealthCheck()

    fun start() {
        println("[Server] Started on port $port")
    }

    /**
     * 요청 처리 (RequestTracker로 추적)
     */
    fun handleRequest(requestId: String, handler: () -> String): RequestResult {
        // 1. 요청 수락 가능 확인
        if (!requestTracker.tryAcquire()) {
            return RequestResult(
                statusCode = 503,
                body = "Service Unavailable - Server is shutting down",
                headers = mapOf("Retry-After" to "5")
            )
        }

        try {
            // 2. 요청 처리
            val response = handler()
            return RequestResult(200, response)
        } catch (e: Exception) {
            return RequestResult(500, "Internal Server Error: ${e.message}")
        } finally {
            // 3. 요청 완료 등록
            requestTracker.release()
        }
    }

    /** Health Check 엔드포인트 */
    fun handleHealthCheck(): RequestResult {
        val health = healthCheck.healthResponse()
        val statusCode = if (health.healthy) 200 else 503
        return RequestResult(statusCode, health.toString())
    }
}

data class RequestResult(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

// ============================================================
// 8. Shutdown DSL
// ============================================================

/**
 * Shutdown 설정 DSL
 */
class ShutdownBuilder {
    var shutdownTimeout: Long = 30_000
    var drainTimeout: Long = 15_000
    var preShutdownDelay: Long = 3_000

    private val components = mutableListOf<LifecycleComponent>()
    private val hooks = mutableListOf<() -> Unit>()

    fun component(component: LifecycleComponent) {
        components.add(component)
    }

    fun onShutdown(hook: () -> Unit) {
        hooks.add(hook)
    }

    fun build(): ShutdownManager {
        val manager = ShutdownManager(
            shutdownTimeout = shutdownTimeout,
            drainTimeout = drainTimeout,
            preShutdownDelay = preShutdownDelay
        )

        components.forEach { manager.register(it) }
        hooks.forEach { manager.addShutdownHook(it) }

        return manager
    }
}

fun gracefulShutdown(block: ShutdownBuilder.() -> Unit): ShutdownManager {
    return ShutdownBuilder().apply(block).build()
}

// ============================================================
// 9. 데모
// ============================================================

fun main() {
    println("=== Graceful Shutdown Pattern - 서버 애플리케이션 ===\n")

    // --- 1. 컴포넌트 생성 ---
    println("--- 1. 컴포넌트 생성 및 등록 ---")

    val database = DatabasePool("jdbc:postgresql://localhost/mydb", 10)
    val cache = CacheService("redis://localhost:6379")
    val consumer = MessageConsumer("orders")
    val scheduler = TaskScheduler()
    val metrics = MetricsReporter()

    // --- 2. Shutdown Manager 설정 (DSL) ---
    println("\n--- 2. Shutdown Manager 설정 ---")

    val shutdownManager = gracefulShutdown {
        shutdownTimeout = 30_000
        drainTimeout = 15_000
        preShutdownDelay = 1_000  // 데모에서는 짧게

        // 종료 단계별 컴포넌트 등록
        component(database)   // CLOSE_CONNECTIONS
        component(cache)      // CLOSE_CACHES
        component(consumer)   // CLOSE_SERVICES
        component(scheduler)  // CLOSE_SERVICES
        component(metrics)    // POST_SHUTDOWN

        onShutdown {
            println("[Hook] Custom shutdown hook executed")
        }
    }

    // --- 3. 컴포넌트 시작 ---
    println("\n--- 3. 컴포넌트 시작 ---")
    database.start()
    cache.start()
    consumer.start()
    scheduler.start()
    metrics.start()

    // --- 4. 서버 시작 ---
    println("\n--- 4. 서버 시작 ---")
    val server = GracefulServer(8080, shutdownManager)
    server.start()

    // --- 5. 정상 요청 처리 ---
    println("\n--- 5. 정상 요청 처리 ---")

    val result1 = server.handleRequest("REQ-001") {
        Thread.sleep(50)
        "Hello, World!"
    }
    println("REQ-001: ${result1.statusCode} - ${result1.body}")

    // --- 6. Health Check ---
    println("\n--- 6. Health Check ---")
    val health = server.handleHealthCheck()
    println("Health: ${health.statusCode} - ${health.body}")

    // --- 7. 진행 중 요청과 함께 종료 ---
    println("\n--- 7. 진행 중 요청과 함께 종료 시뮬레이션 ---")

    // 진행 중인 요청 시뮬레이션
    val requestTracker = shutdownManager.getRequestTracker()
    val requestThreads = (1..3).map { i ->
        Thread {
            val result = server.handleRequest("INFLIGHT-$i") {
                println("  [Request INFLIGHT-$i] Processing...")
                Thread.sleep(500) // 500ms 걸리는 요청
                "Response $i"
            }
            println("  [Request INFLIGHT-$i] Completed: ${result.statusCode}")
        }.also { it.start() }
    }

    // 약간 대기 후 종료 시작
    Thread.sleep(100)

    // 캐시에 더티 데이터 추가
    cache.put("user:1", "data")
    cache.put("user:2", "data")

    // 메트릭 기록
    metrics.record("request.count=100")
    metrics.record("request.latency=50ms")

    // --- 8. Graceful Shutdown 실행 ---
    println("\n--- 8. Graceful Shutdown 실행 ---")
    shutdownManager.shutdown()

    // 진행 중 요청 대기
    requestThreads.forEach { it.join() }

    // --- 9. 종료 후 요청 거부 확인 ---
    println("\n--- 9. 종료 후 요청 테스트 ---")
    val rejectedResult = server.handleRequest("AFTER-SHUTDOWN") {
        "This should not execute"
    }
    println("AFTER-SHUTDOWN: ${rejectedResult.statusCode} - ${rejectedResult.body}")
    rejectedResult.headers["Retry-After"]?.let {
        println("  Retry-After: ${it}s")
    }

    // --- 핵심 원칙 ---
    println("\n=== Graceful Shutdown 핵심 원칙 ===")
    println("1. 신호 감지: SIGTERM → Shutdown Hook 실행")
    println("2. 헬스 변경: Health Check → SHUTTING_DOWN (LB 인지)")
    println("3. 대기 시간: 로드밸런서가 트래픽 중단할 시간")
    println("4. 요청 거부: 새 요청 503 반환 (Retry-After 헤더)")
    println("5. 요청 드레인: 진행 중 요청 완료 대기")
    println("6. 역순 종료: 서비스 → 캐시 → DB 순서")
    println("7. 타임아웃: 무한 대기 방지, 타임아웃 후 강제 종료")
    println("8. 데이터 보호: 버퍼 플러시, 트랜잭션 완료")
}
