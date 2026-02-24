package architecture.structuredlogging.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Structured Logging Pattern - 해결책
 *
 * JSON 기반 구조화된 로깅 시스템 구현:
 * - LogLevel: TRACE, DEBUG, INFO, WARN, ERROR 레벨
 * - LogEntry: 구조화된 로그 이벤트 객체
 * - MDC: 요청별 컨텍스트 전파 (Mapped Diagnostic Context)
 * - LogAppender: 로그 출력 대상 (Console, JSON, File)
 * - Sanitizer: 민감 정보 마스킹
 * - Logger: 지연 평가, 컨텍스트 자동 포함
 *
 * 핵심 구성:
 * - LogEntry: 타임스탬프, 레벨, 메시지, 컨텍스트 필드
 * - MDC: ThreadLocal 기반 요청 컨텍스트
 * - Appender: 포맷터 + 출력 대상
 * - Sanitizer: 패턴 기반 민감 정보 마스킹
 */

// ============================================================
// 1. Log Level
// ============================================================

/**
 * 로그 레벨
 */
enum class LogLevel(val value: Int, val label: String) {
    TRACE(0, "TRACE"),
    DEBUG(1, "DEBUG"),
    INFO(2, "INFO"),
    WARN(3, "WARN"),
    ERROR(4, "ERROR");

    fun isEnabled(configuredLevel: LogLevel): Boolean = this.value >= configuredLevel.value
}

// ============================================================
// 2. Log Entry (구조화된 로그 이벤트)
// ============================================================

/**
 * 구조화된 로그 엔트리
 */
data class LogEntry(
    val timestamp: Instant = Instant.now(),
    val level: LogLevel,
    val loggerName: String,
    val message: String,
    val fields: Map<String, Any?> = emptyMap(),
    val mdcContext: Map<String, String> = emptyMap(),
    val throwable: Throwable? = null,
    val threadName: String = Thread.currentThread().name
) {
    /** JSON 형식 출력 */
    fun toJson(): String {
        val parts = mutableListOf<String>()

        parts.add("\"timestamp\":\"${DateTimeFormatter.ISO_INSTANT.format(timestamp)}\"")
        parts.add("\"level\":\"${level.label}\"")
        parts.add("\"logger\":\"$loggerName\"")
        parts.add("\"message\":${escapeJson(message)}")
        parts.add("\"thread\":\"$threadName\"")

        // MDC 컨텍스트
        if (mdcContext.isNotEmpty()) {
            mdcContext.forEach { (k, v) ->
                parts.add("\"$k\":${escapeJson(v)}")
            }
        }

        // 추가 필드
        if (fields.isNotEmpty()) {
            fields.forEach { (k, v) ->
                parts.add("\"$k\":${formatValue(v)}")
            }
        }

        // 예외 정보
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            parts.add("\"error.type\":\"${throwable.javaClass.name}\"")
            parts.add("\"error.message\":${escapeJson(throwable.message ?: "")}")
            parts.add("\"error.stacktrace\":${escapeJson(sw.toString().take(2000))}")
        }

        return "{${parts.joinToString(",")}}"
    }

    /** 읽기 좋은 텍스트 형식 */
    fun toText(): String {
        val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
            .format(timestamp)

        val sb = StringBuilder()
        sb.append("$time [${level.label.padEnd(5)}] [$threadName] $loggerName - $message")

        if (mdcContext.isNotEmpty()) {
            sb.append(" | ctx={${mdcContext.entries.joinToString(", ") { "${it.key}=${it.value}" }}}")
        }

        if (fields.isNotEmpty()) {
            sb.append(" | ${fields.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        }

        if (throwable != null) {
            sb.append("\n  ${throwable.javaClass.name}: ${throwable.message}")
        }

        return sb.toString()
    }

    companion object {
        private fun escapeJson(value: String): String {
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"$escaped\""
        }

        private fun formatValue(value: Any?): String = when (value) {
            null -> "null"
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> "[${value.joinToString(",") { formatValue(it) }}]"
            else -> escapeJson(value.toString())
        }
    }
}

// ============================================================
// 3. MDC (Mapped Diagnostic Context)
// ============================================================

/**
 * ThreadLocal 기반 요청 컨텍스트
 */
object MDC {
    private val contextMap = ThreadLocal<MutableMap<String, String>>()

    fun put(key: String, value: String) {
        getOrCreate()[key] = value
    }

    fun get(key: String): String? = contextMap.get()?.get(key)

    fun remove(key: String) {
        contextMap.get()?.remove(key)
    }

    fun getContext(): Map<String, String> = contextMap.get()?.toMap() ?: emptyMap()

    fun clear() {
        contextMap.get()?.clear()
        contextMap.remove()
    }

    private fun getOrCreate(): MutableMap<String, String> {
        var map = contextMap.get()
        if (map == null) {
            map = mutableMapOf()
            contextMap.set(map)
        }
        return map
    }

    /** 스코프 기반 MDC 관리 */
    inline fun <T> withContext(vararg pairs: Pair<String, String>, block: () -> T): T {
        pairs.forEach { (k, v) -> put(k, v) }
        try {
            return block()
        } finally {
            pairs.forEach { (k, _) -> remove(k) }
        }
    }

    /** 요청 처리 시 자동 컨텍스트 설정 */
    fun withRequestContext(
        requestId: String = UUID.randomUUID().toString().take(8),
        userId: String? = null,
        block: () -> Unit
    ) {
        put("requestId", requestId)
        put("traceId", UUID.randomUUID().toString().replace("-", "").take(16))
        userId?.let { put("userId", it) }

        try {
            block()
        } finally {
            clear()
        }
    }
}

// ============================================================
// 4. Log Appender (로그 출력 대상)
// ============================================================

/**
 * 로그 출력 인터페이스
 */
interface LogAppender {
    val name: String
    fun append(entry: LogEntry)
    fun flush() {}
}

/**
 * 콘솔 출력 (텍스트 포맷)
 */
class ConsoleAppender(
    private val colorEnabled: Boolean = true
) : LogAppender {
    override val name = "Console"

    override fun append(entry: LogEntry) {
        val text = entry.toText()
        if (colorEnabled) {
            println(colorize(text, entry.level))
        } else {
            println(text)
        }
    }

    private fun colorize(text: String, level: LogLevel): String {
        val color = when (level) {
            LogLevel.TRACE -> "\u001B[90m"   // 회색
            LogLevel.DEBUG -> "\u001B[36m"   // 시안
            LogLevel.INFO -> "\u001B[32m"    // 초록
            LogLevel.WARN -> "\u001B[33m"    // 노랑
            LogLevel.ERROR -> "\u001B[31m"   // 빨강
        }
        return "$color$text\u001B[0m"
    }
}

/**
 * JSON 출력 (ELK, Datadog 등)
 */
class JsonAppender(
    private val prettyPrint: Boolean = false
) : LogAppender {
    override val name = "JSON"

    override fun append(entry: LogEntry) {
        println(entry.toJson())
    }
}

/**
 * 메모리 버퍼 (테스트용)
 */
class InMemoryAppender : LogAppender {
    override val name = "InMemory"
    val entries = CopyOnWriteArrayList<LogEntry>()

    override fun append(entry: LogEntry) {
        entries.add(entry)
    }

    fun clear() = entries.clear()

    fun findByLevel(level: LogLevel) = entries.filter { it.level == level }

    fun findByField(key: String, value: Any?) = entries.filter { it.fields[key] == value }

    fun findByMessage(pattern: String) = entries.filter { it.message.contains(pattern) }
}

/**
 * 파일 출력 (시뮬레이션)
 */
class FileAppender(
    private val filePath: String,
    private val maxSizeMb: Int = 100,
    private val maxFiles: Int = 10
) : LogAppender {
    override val name = "File($filePath)"
    private val buffer = mutableListOf<String>()
    private val bufferSize = 100

    override fun append(entry: LogEntry) {
        buffer.add(entry.toJson())
        if (buffer.size >= bufferSize) {
            flush()
        }
    }

    override fun flush() {
        if (buffer.isNotEmpty()) {
            // 실제로는 파일에 쓰기
            println("[FileAppender] Flushing ${buffer.size} entries to $filePath")
            buffer.clear()
        }
    }
}

// ============================================================
// 5. Sanitizer (민감 정보 마스킹)
// ============================================================

/**
 * 민감 정보 마스킹
 */
class LogSanitizer {
    private val sensitiveFields = mutableSetOf<String>()
    private val sensitivePatterns = mutableListOf<Pair<Regex, String>>()

    fun addSensitiveField(vararg fields: String): LogSanitizer {
        sensitiveFields.addAll(fields)
        return this
    }

    fun addPattern(pattern: Regex, replacement: String): LogSanitizer {
        sensitivePatterns.add(pattern to replacement)
        return this
    }

    /** 필드 값 마스킹 */
    fun sanitizeFields(fields: Map<String, Any?>): Map<String, Any?> {
        return fields.mapValues { (key, value) ->
            if (key.lowercase() in sensitiveFields.map { it.lowercase() }) {
                maskValue(value)
            } else {
                value
            }
        }
    }

    /** 메시지 내 민감 정보 마스킹 */
    fun sanitizeMessage(message: String): String {
        var sanitized = message
        sensitivePatterns.forEach { (pattern, replacement) ->
            sanitized = pattern.replace(sanitized, replacement)
        }
        return sanitized
    }

    private fun maskValue(value: Any?): String = when {
        value == null -> "null"
        value.toString().length <= 4 -> "****"
        else -> {
            val str = value.toString()
            str.take(2) + "*".repeat(str.length - 4) + str.takeLast(2)
        }
    }

    companion object {
        fun default(): LogSanitizer = LogSanitizer()
            .addSensitiveField("password", "token", "secret", "apiKey", "authorization",
                "creditCard", "cardNumber", "ssn", "accessToken", "refreshToken")
            .addPattern(
                Regex("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"),
                "****-****-****-####"
            )
            .addPattern(
                Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
                "***@***.***"
            )
    }
}

// ============================================================
// 6. Logger (메인 로거)
// ============================================================

/**
 * 구조화된 로거
 */
class Logger private constructor(
    val name: String
) {
    private var level: LogLevel = LogLevel.INFO

    /**
     * 로그 기록 (지연 평가)
     */
    fun trace(message: () -> String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.TRACE, message, fields.toMap())

    fun debug(message: () -> String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.DEBUG, message, fields.toMap())

    fun info(message: () -> String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.INFO, message, fields.toMap())

    fun warn(message: () -> String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.WARN, message, fields.toMap())

    fun error(message: () -> String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) =
        log(LogLevel.ERROR, message, fields.toMap(), throwable)

    /**
     * 문자열 버전 (간단한 메시지용)
     */
    fun trace(message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.TRACE, { message }, fields.toMap())

    fun debug(message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.DEBUG, { message }, fields.toMap())

    fun info(message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.INFO, { message }, fields.toMap())

    fun warn(message: String, vararg fields: Pair<String, Any?>) =
        log(LogLevel.WARN, { message }, fields.toMap())

    fun error(message: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) =
        log(LogLevel.ERROR, { message }, fields.toMap(), throwable)

    private fun log(
        level: LogLevel,
        message: () -> String,
        fields: Map<String, Any?>,
        throwable: Throwable? = null
    ) {
        val effectiveLevel = LoggerFactory.getLevelFor(name)

        // 레벨 체크 - 비활성이면 메시지 람다도 실행 안됨
        if (!level.isEnabled(effectiveLevel)) return

        val sanitizer = LoggerFactory.getSanitizer()
        val sanitizedFields = sanitizer.sanitizeFields(fields)
        val sanitizedMessage = sanitizer.sanitizeMessage(message())

        val entry = LogEntry(
            level = level,
            loggerName = name,
            message = sanitizedMessage,
            fields = sanitizedFields,
            mdcContext = MDC.getContext(),
            throwable = throwable
        )

        LoggerFactory.getAppenders().forEach { appender ->
            try {
                appender.append(entry)
            } catch (e: Exception) {
                System.err.println("Failed to append log to ${appender.name}: ${e.message}")
            }
        }
    }

    /**
     * 타이머 로깅 (실행 시간 측정)
     */
    inline fun <T> timed(
        operation: String,
        vararg fields: Pair<String, Any?>,
        block: () -> T
    ): T {
        val start = System.currentTimeMillis()
        var success = true
        try {
            return block()
        } catch (e: Exception) {
            success = false
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            val allFields = fields.toMap() + mapOf(
                "duration_ms" to duration,
                "operation" to operation,
                "success" to success
            )

            if (duration > 1000) {
                warn("Slow operation: $operation", *allFields.toList().toTypedArray())
            } else {
                debug({ "Operation completed: $operation" }, *allFields.toList().toTypedArray())
            }
        }
    }

    companion object {
        fun getLogger(name: String): Logger = Logger(name)
        fun getLogger(clazz: Class<*>): Logger = Logger(clazz.simpleName)
    }
}

// ============================================================
// 7. Logger Factory (전역 설정)
// ============================================================

/**
 * 로거 팩토리 및 전역 설정
 */
object LoggerFactory {
    private var defaultLevel: LogLevel = LogLevel.INFO
    private val levelOverrides = ConcurrentHashMap<String, LogLevel>()
    private val appenders = CopyOnWriteArrayList<LogAppender>()
    private var sanitizer: LogSanitizer = LogSanitizer.default()

    fun configure(block: LoggerConfig.() -> Unit) {
        val config = LoggerConfig().apply(block)
        defaultLevel = config.level
        appenders.clear()
        appenders.addAll(config.appenders)
        sanitizer = config.sanitizer
        levelOverrides.clear()
        levelOverrides.putAll(config.levelOverrides)
    }

    fun getLevelFor(loggerName: String): LogLevel {
        // 정확한 매칭 → 패키지 매칭 → 기본값
        levelOverrides[loggerName]?.let { return it }

        // 패키지 계층 탐색
        var prefix = loggerName
        while (prefix.contains(".")) {
            prefix = prefix.substringBeforeLast(".")
            levelOverrides[prefix]?.let { return it }
        }

        return defaultLevel
    }

    fun getAppenders(): List<LogAppender> = appenders

    fun getSanitizer(): LogSanitizer = sanitizer

    /** 런타임 로그 레벨 변경 */
    fun setLevel(loggerName: String, level: LogLevel) {
        levelOverrides[loggerName] = level
        println("[LoggerFactory] Level for '$loggerName' changed to ${level.label}")
    }

    /** 기본 레벨 변경 */
    fun setDefaultLevel(level: LogLevel) {
        defaultLevel = level
        println("[LoggerFactory] Default level changed to ${level.label}")
    }
}

class LoggerConfig {
    var level: LogLevel = LogLevel.INFO
    val appenders = mutableListOf<LogAppender>()
    var sanitizer: LogSanitizer = LogSanitizer.default()
    val levelOverrides = mutableMapOf<String, LogLevel>()

    fun appender(appender: LogAppender) {
        appenders.add(appender)
    }

    fun level(loggerName: String, level: LogLevel) {
        levelOverrides[loggerName] = level
    }
}

// ============================================================
// 8. 편의 확장 함수
// ============================================================

/**
 * 인라인 로거 생성
 */
inline fun <reified T> logger(): Logger = Logger.getLogger(T::class.java)

// ============================================================
// 9. 사용 예시 - 서비스 클래스들
// ============================================================

data class Order(val id: String, val userId: String, val amount: Double, val items: Int)

class OrderService {
    private val log = logger<OrderService>()

    fun createOrder(userId: String, amount: Double, items: Int): Order {
        log.info("Creating order",
            "userId" to userId,
            "amount" to amount,
            "items" to items
        )

        val order = log.timed("create_order", "userId" to userId) {
            // 주문 생성 로직
            Thread.sleep(50)
            Order("ORD-${System.currentTimeMillis() % 10000}", userId, amount, items)
        }

        log.info("Order created successfully",
            "orderId" to order.id,
            "userId" to userId,
            "amount" to amount
        )

        return order
    }

    fun processPayment(orderId: String, cardNumber: String, amount: Double): Boolean {
        log.info("Processing payment",
            "orderId" to orderId,
            "cardNumber" to cardNumber,  // 자동 마스킹됨
            "amount" to amount
        )

        return try {
            Thread.sleep(30)

            if (amount > 10000) {
                log.warn("High value payment detected",
                    "orderId" to orderId,
                    "amount" to amount,
                    "threshold" to 10000
                )
            }

            log.info("Payment processed",
                "orderId" to orderId,
                "amount" to amount,
                "success" to true
            )
            true
        } catch (e: Exception) {
            log.error("Payment failed",
                throwable = e,
                "orderId" to orderId,
                "amount" to amount
            )
            false
        }
    }
}

class UserService {
    private val log = logger<UserService>()

    fun login(email: String, password: String): Boolean {
        log.info("User login attempt",
            "email" to email,
            "password" to password  // 자동 마스킹됨
        )

        return log.timed("user_login", "email" to email) {
            Thread.sleep(20)
            val success = email.contains("@")

            if (success) {
                log.info("User logged in", "email" to email)
            } else {
                log.warn("Login failed - invalid email format", "email" to email)
            }

            success
        }
    }
}

class NotificationService {
    private val log = logger<NotificationService>()

    fun sendEmail(to: String, subject: String) {
        log.debug({ "Sending email notification" },
            "to" to to,
            "subject" to subject
        )

        Thread.sleep(10)

        log.info("Email sent",
            "to" to to,
            "subject" to subject
        )
    }
}

// ============================================================
// 10. 데모
// ============================================================

fun main() {
    println("=== Structured Logging Pattern ===\n")

    // --- 1. 로거 설정 (텍스트 출력) ---
    println("--- 1. 텍스트 포맷 로깅 ---")

    val memoryAppender = InMemoryAppender()

    LoggerFactory.configure {
        level = LogLevel.DEBUG
        appender(ConsoleAppender(colorEnabled = false))
        appender(memoryAppender)

        // 로거별 레벨 오버라이드
        level("NotificationService", LogLevel.INFO)

        // 민감 정보 마스킹
        sanitizer = LogSanitizer.default()
    }

    val log = Logger.getLogger("Main")
    log.info("Application started", "version" to "1.0.0", "env" to "development")

    // --- 2. MDC 요청 컨텍스트 ---
    println("\n--- 2. MDC 요청 컨텍스트 ---")

    MDC.withRequestContext(requestId = "req-001", userId = "user-42") {
        val orderService = OrderService()
        val userService = UserService()

        // 모든 로그에 requestId, traceId, userId 자동 포함
        userService.login("john@example.com", "secret123")

        val order = orderService.createOrder("user-42", 299.99, 3)

        orderService.processPayment(order.id, "4111-2222-3333-4444", 299.99)
    }

    // --- 3. 민감 정보 마스킹 ---
    println("\n--- 3. 민감 정보 마스킹 ---")

    MDC.withRequestContext(requestId = "req-002") {
        val log2 = Logger.getLogger("SecurityTest")

        log2.info("Credentials test",
            "password" to "super_secret",
            "token" to "eyJhbGciOiJIUzI1NiJ9.xxx",
            "apiKey" to "sk_live_abc123xyz",
            "normalField" to "this is visible"
        )

        // 메시지 내 카드번호/이메일도 마스킹
        log2.info("Payment with card 4111-2222-3333-4444 from user@test.com")
    }

    // --- 4. 에러 로깅 ---
    println("\n--- 4. 에러 로깅 (스택트레이스 포함) ---")

    MDC.withRequestContext(requestId = "req-003") {
        val log3 = Logger.getLogger("ErrorTest")

        try {
            throw RuntimeException("Database connection timeout after 30000ms")
        } catch (e: Exception) {
            log3.error("Failed to connect to database",
                throwable = e,
                "db.host" to "db.production.internal",
                "db.port" to 5432,
                "timeout_ms" to 30000
            )
        }
    }

    // --- 5. JSON 포맷 ---
    println("\n--- 5. JSON 포맷 (ELK/Datadog 용) ---")

    LoggerFactory.configure {
        level = LogLevel.INFO
        appender(JsonAppender())
    }

    MDC.withRequestContext(requestId = "req-004", userId = "user-99") {
        val log4 = Logger.getLogger("JsonDemo")

        log4.info("Order processed",
            "orderId" to "ORD-5678",
            "amount" to 149.99,
            "items" to 2,
            "paymentMethod" to "credit_card"
        )
    }

    // --- 6. 런타임 레벨 변경 ---
    println("\n--- 6. 런타임 레벨 변경 ---")

    LoggerFactory.configure {
        level = LogLevel.INFO
        appender(ConsoleAppender(colorEnabled = false))
    }

    val debugLog = Logger.getLogger("DebugTarget")
    debugLog.debug("This won't appear - INFO level")
    debugLog.info("This appears - INFO level")

    // 런타임에 DEBUG로 변경
    LoggerFactory.setLevel("DebugTarget", LogLevel.DEBUG)
    debugLog.debug("Now this appears! - DEBUG level enabled at runtime")

    // --- 7. 지연 평가 ---
    println("\n--- 7. 지연 평가 (성능 최적화) ---")

    LoggerFactory.configure {
        level = LogLevel.WARN  // INFO 이하 비활성
        appender(ConsoleAppender(colorEnabled = false))
    }

    val perfLog = Logger.getLogger("Performance")

    // 람다이므로 WARN 미만이면 실행 안됨
    perfLog.debug({ "Expensive: ${(1..10000).toList()}" })
    println("(DEBUG 메시지 - 비활성이라 람다 실행 안됨)")

    perfLog.warn({ "This is evaluated" })

    // --- 8. 타이머 로깅 ---
    println("\n--- 8. 타이머 로깅 ---")

    LoggerFactory.configure {
        level = LogLevel.DEBUG
        appender(ConsoleAppender(colorEnabled = false))
    }

    val timerLog = Logger.getLogger("Timer")
    timerLog.timed("database_query", "table" to "users") {
        Thread.sleep(50)
        "query result"
    }

    // --- 9. InMemory 검증 ---
    println("\n--- 9. InMemory Appender (테스트용) ---")
    println("Total logged entries: ${memoryAppender.entries.size}")
    println("INFO entries: ${memoryAppender.findByLevel(LogLevel.INFO).size}")
    println("WARN entries: ${memoryAppender.findByLevel(LogLevel.WARN).size}")
    println("ERROR entries: ${memoryAppender.findByLevel(LogLevel.ERROR).size}")

    // --- 핵심 원칙 ---
    println("\n=== Structured Logging 핵심 원칙 ===")
    println("1. JSON 구조: 키-값 쌍으로 파싱/검색/분석 가능")
    println("2. MDC 컨텍스트: requestId, traceId로 요청 추적")
    println("3. 민감 정보 마스킹: password, cardNumber 자동 마스킹")
    println("4. 로그 레벨: TRACE < DEBUG < INFO < WARN < ERROR")
    println("5. 지연 평가: 비활성 레벨은 람다 실행 안됨")
    println("6. 런타임 변경: 재시작 없이 레벨 변경")
    println("7. 다중 출력: Console, JSON, File 동시 지원")
    println("8. 타이머: 실행 시간 자동 측정 및 로깅")
}
