# Structured Logging Pattern

## 개요

Structured Logging Pattern은 로그를 **키-값 쌍의 구조화된 데이터**로 기록하는 패턴입니다. JSON 포맷으로 출력하여 ELK, Datadog 등 로그 분석 도구와 연동하고, **MDC로 요청 추적**, **민감 정보 마스킹**, **지연 평가로 성능 최적화**, **런타임 레벨 변경**을 지원합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **LogEntry** | 타임스탬프, 레벨, 메시지, 필드, MDC를 담는 이벤트 |
| **MDC** | ThreadLocal 기반 요청별 컨텍스트 (requestId, traceId) |
| **LogAppender** | 로그 출력 대상 (Console, JSON, File, InMemory) |
| **LogSanitizer** | 민감 정보 자동 마스킹 |
| **Logger** | 지연 평가, 타이머, 컨텍스트 자동 포함 |
| **LoggerFactory** | 전역 설정, 런타임 레벨 변경 |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Application Code                                    │
│                                                                              │
│   log.info("Order created",                                                  │
│       "orderId" to "ORD-123",                                                │
│       "amount" to 99.99,                                                     │
│       "userId" to "user-42"                                                  │
│   )                                                                          │
│                                                                              │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Logger                                          │
│                                                                              │
│   1. Level Check ──────────────────────────────────────────────────┐        │
│      level.isEnabled(configuredLevel)?                              │        │
│      NO → return (람다 실행 안됨, 성능 보존)                        │        │
│      YES ↓                                                          │        │
│                                                                     │        │
│   2. Message Evaluation ────────────────────────────────────────┐  │        │
│      message() 람다 실행 → 문자열 생성                           │  │        │
│                                                                  │  │        │
│   3. Sanitize ──────────────────────────────────────────────┐   │  │        │
│      password → "su****et"                                   │   │  │        │
│      cardNumber → "****-****-****-####"                     │   │  │        │
│      email → "***@***.***"                                  │   │  │        │
│                                                              │   │  │        │
│   4. MDC Context ────────────────────────────────────────┐  │   │  │        │
│      ThreadLocal에서 requestId, traceId, userId 가져옴   │  │   │  │        │
│                                                           │  │   │  │        │
│   5. Build LogEntry ──────────────────────────────────┐  │  │   │  │        │
│      timestamp + level + message + fields + MDC       │  │  │   │  │        │
│                                                        │  │  │   │  │        │
│   └────────────────────────────────────────────────────┘  │  │   │  │        │
│   └───────────────────────────────────────────────────────┘  │   │  │        │
│   └──────────────────────────────────────────────────────────┘   │  │        │
│   └──────────────────────────────────────────────────────────────┘  │        │
│   └─────────────────────────────────────────────────────────────────┘        │
│                                                                              │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │ LogEntry
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Appenders                                         │
│                                                                              │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│   │  Console     │  │  JSON        │  │  File        │  │  InMemory    │  │
│   │              │  │              │  │              │  │  (테스트)     │  │
│   │  entry       │  │  entry       │  │  entry       │  │  entry       │  │
│   │  .toText()   │  │  .toJson()   │  │  .toJson()   │  │  → List<>   │  │
│   │              │  │              │  │  + 로테이션   │  │              │  │
│   │  ↓           │  │  ↓           │  │  ↓           │  │  ↓           │  │
│   │  stdout      │  │  stdout      │  │  file.log    │  │  memory      │  │
│   └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## MDC 요청 추적 흐름

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MDC Request Context                                   │
│                                                                              │
│  HTTP Request 수신                                                           │
│      │                                                                       │
│      ▼                                                                       │
│  MDC.withRequestContext(                                                      │
│      requestId = "req-001",                                                  │
│      userId = "user-42"                                                      │
│  ) {                                                                         │
│      │  ThreadLocal: { requestId: "req-001", traceId: "abc123", userId: ... }│
│      │                                                                       │
│      ├── UserService.login()                                                 │
│      │   log.info("Login")                                                   │
│      │   → {"requestId":"req-001","traceId":"abc123","message":"Login"}      │
│      │                                                                       │
│      ├── OrderService.create()                                               │
│      │   log.info("Order created")                                           │
│      │   → {"requestId":"req-001","traceId":"abc123","message":"Order..."}   │
│      │                                                                       │
│      └── NotificationService.send()                                          │
│          log.info("Email sent")                                              │
│          → {"requestId":"req-001","traceId":"abc123","message":"Email..."}   │
│  }                                                                           │
│      │                                                                       │
│      ▼                                                                       │
│  MDC.clear()  ← 자동 정리                                                   │
│                                                                              │
│  모든 로그에 동일한 requestId → Kibana에서 한 요청의 전체 흐름 추적 가능      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 로그 출력 비교

### 비구조적 로그 (Before)
```
2024-01-15 10:30:00 User login: john@example.com
2024-01-15 10:30:01 Payment processed: $99.99 for order #1234
2024-01-15 10:30:02 ERROR Database connection failed!
```

### 구조화된 텍스트 로그
```
10:30:00.123 [INFO ] [main] UserService - User logged in | ctx={requestId=req-001, traceId=abc123} | email=john@example.com
10:30:01.456 [INFO ] [main] OrderService - Payment processed | ctx={requestId=req-001} | orderId=ORD-1234, amount=99.99
10:30:02.789 [ERROR] [main] DbService - Connection failed | ctx={requestId=req-001} | db.host=localhost, timeout_ms=30000
```

### JSON 로그 (ELK/Datadog)
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "UserService",
  "message": "User logged in",
  "thread": "main",
  "requestId": "req-001",
  "traceId": "abc123",
  "email": "john@example.com"
}
```

## 주요 구현

### LogEntry

```kotlin
data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val loggerName: String,
    val message: String,
    val fields: Map<String, Any?>,
    val mdcContext: Map<String, String>,
    val throwable: Throwable?,
    val threadName: String
) {
    fun toJson(): String   // ELK, Datadog
    fun toText(): String   // 개발 콘솔
}
```

### MDC (Mapped Diagnostic Context)

```kotlin
object MDC {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun getContext(): Map<String, String>
    fun clear()

    // 스코프 기반 사용
    fun withRequestContext(
        requestId: String,
        userId: String? = null,
        block: () -> Unit
    )
}

// 사용
MDC.withRequestContext(requestId = "req-001", userId = "user-42") {
    // 이 블록 안의 모든 로그에 requestId, traceId 자동 포함
    log.info("Order created", "orderId" to "ORD-123")
}
```

### Logger (지연 평가)

```kotlin
class Logger {
    // 람다 버전 - 비활성 레벨이면 실행 안됨
    fun debug(message: () -> String, vararg fields: Pair<String, Any?>)

    // 타이머 - 실행 시간 자동 측정
    inline fun <T> timed(operation: String, block: () -> T): T

    // 문자열 버전
    fun info(message: String, vararg fields: Pair<String, Any?>)
}

// 사용
log.debug({ "Expensive: ${heavyComputation()}" })  // DEBUG 비활성이면 실행 안됨

log.timed("db_query", "table" to "users") {
    database.query("SELECT * FROM users")
}
// → DEBUG: "Operation completed: db_query" | duration_ms=45
```

### LogSanitizer

```kotlin
class LogSanitizer {
    fun addSensitiveField(vararg fields: String)
    fun addPattern(pattern: Regex, replacement: String)

    companion object {
        fun default(): LogSanitizer  // password, token, cardNumber 등
    }
}

// 자동 마스킹
log.info("Login",
    "password" to "secret123"     // → "se****23"
    "cardNumber" to "4111222233334444"  // → "****-****-****-####"
)
```

### LoggerFactory

```kotlin
LoggerFactory.configure {
    level = LogLevel.INFO                        // 기본 레벨
    appender(ConsoleAppender())                  // 콘솔 출력
    appender(JsonAppender())                     // JSON 출력
    appender(FileAppender("/var/log/app.log"))   // 파일 출력
    level("NotificationService", LogLevel.WARN)  // 로거별 레벨

    sanitizer = LogSanitizer.default()           // 민감 정보 마스킹
}

// 런타임 레벨 변경 (재시작 없이)
LoggerFactory.setLevel("OrderService", LogLevel.DEBUG)
```

## 장점

1. **검색/분석 가능**: JSON 키-값으로 Kibana, Datadog에서 쿼리
2. **요청 추적**: MDC의 requestId/traceId로 분산 시스템 추적
3. **민감 정보 보호**: 자동 마스킹으로 로그 보안
4. **성능 최적화**: 지연 평가로 비활성 레벨 오버헤드 제거
5. **런타임 변경**: 재시작 없이 로그 레벨 변경
6. **일관성**: 모든 로그가 동일한 구조
7. **테스트 용이**: InMemoryAppender로 로그 검증

## 단점

1. **JSON 크기**: 텍스트보다 로그 용량 증가
2. **가독성**: 개발 시 JSON은 읽기 어려움 (ConsoleAppender로 해결)
3. **초기 설정**: LoggerFactory, Sanitizer 등 설정 필요
4. **MDC 관리**: ThreadLocal 정리 누락 시 메모리 누수

## 적용 시점

- 마이크로서비스/분산 시스템 (요청 추적 필수)
- ELK, Datadog, Splunk 등 로그 분석 도구 사용
- 프로덕션 로그 모니터링 및 알림
- 민감 정보 처리 시스템 (PCI-DSS, GDPR)
- 성능 모니터링 (실행 시간 측정)

## 문제점 vs 해결책 비교

| 문제점 | 해결책 |
|--------|--------|
| 비정형 텍스트 | JSON 키-값 구조 |
| 컨텍스트 누락 | MDC (requestId, traceId) |
| 민감 정보 노출 | LogSanitizer 자동 마스킹 |
| 로그 레벨 무시 | LogLevel enum + 강제 |
| 성능 문제 | 지연 평가 (람다) |
| 일관성 없음 | LogEntry 통일 포맷 |
| 추적 불가 | MDC + traceId |
| 분석 어려움 | JSON → ELK/Datadog |

## 관련 패턴

- **Chain of Responsibility**: Appender 체인
- **Strategy Pattern**: 포맷 전략 (Text, JSON)
- **Decorator Pattern**: Sanitizer가 로그 메시지 가공
- **Observer Pattern**: Appender가 LogEntry 수신

## 실제 라이브러리

| 라이브러리 | 특징 |
|------------|------|
| **SLF4J + Logback** | Java 표준, MDC 지원 |
| **Log4j2** | 비동기 로깅, 구조화 레이아웃 |
| **Kotlin-logging** | Kotlin 래퍼, 지연 평가 |
| **Logstash Encoder** | JSON 포맷 출력 |
| **Timber (Android)** | Android 로깅 라이브러리 |

## 참고 자료

- [Structured Logging Best Practices](https://www.thoughtworks.com/insights/blog/microservices/structured-logging)
- [SLF4J MDC](https://www.slf4j.org/api/org/slf4j/MDC.html)
- [ELK Stack](https://www.elastic.co/what-is/elk-stack)
- [OpenTelemetry Logging](https://opentelemetry.io/docs/specs/otel/logs/)
