# Graceful Shutdown Pattern

## 개요

Graceful Shutdown Pattern은 애플리케이션 종료 시 **진행 중인 요청을 완료**하고, **리소스를 안전하게 정리**하며, **데이터 손실 없이** 종료하는 패턴입니다. SIGTERM 신호 감지부터 단계별 종료, 요청 드레인, 역순 리소스 정리까지 체계적인 종료 프로세스를 구현합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **ShutdownManager** | 종료 프로세스 총괄 관리자 |
| **ShutdownPhase** | 단계별 종료 순서 정의 |
| **LifecycleComponent** | 시작/종료 인터페이스 |
| **RequestTracker** | 진행 중 요청 추적 및 드레인 |
| **HealthCheck** | 종료 시 헬스 상태 변경 |
| **GracefulServer** | 안전한 종료를 지원하는 서버 |

## 종료 프로세스 흐름

```
SIGTERM 수신
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 0: PRE_SHUTDOWN                                                       │
│                                                                              │
│   1. Health Check → SHUTTING_DOWN                                            │
│   2. 로드밸런서 인지 대기 (3초)                                               │
│   3. 새 요청 수락 중단                                                        │
│                                                                              │
│   ┌──────────┐       ┌──────────────┐       ┌──────────────┐                │
│   │  LB 체크  │──────►│ SHUTTING_DOWN │──────►│ 트래픽 중단   │                │
│   │  /health  │       │   503 반환    │       │ (서버 제외)   │                │
│   └──────────┘       └──────────────┘       └──────────────┘                │
│                                                                              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 1: DRAIN                                                              │
│                                                                              │
│   진행 중인 요청 완료 대기 (타임아웃: 15초)                                    │
│                                                                              │
│   새 요청 ──► 503 Service Unavailable (Retry-After: 5)                       │
│                                                                              │
│   ┌──────────────────────────────────────────────────────────┐              │
│   │  Active Requests: [REQ-1] [REQ-2] [REQ-3]               │              │
│   │                      ↓       ↓       ↓                   │              │
│   │                   완료     완료     완료                  │              │
│   │                                                          │              │
│   │  Active Requests: 0 → 드레인 완료                        │              │
│   └──────────────────────────────────────────────────────────┘              │
│                                                                              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 2-4: CLOSE (역순 종료)                                                │
│                                                                              │
│   Phase 2: CLOSE_SERVICES                                                    │
│   ┌────────────────────┐  ┌────────────────────┐                             │
│   │  MessageConsumer   │  │  TaskScheduler     │                             │
│   │  • 수신 중단        │  │  • 새 작업 거부     │                             │
│   │  • 처리 중 완료     │  │  • 진행 중 완료     │                             │
│   │  • ACK 전송         │  │  • 스레드풀 종료    │                             │
│   └────────────────────┘  └────────────────────┘                             │
│                                    │                                         │
│   Phase 3: CLOSE_CACHES           │                                         │
│   ┌────────────────────┐          │                                         │
│   │  CacheService      │          │                                         │
│   │  • 더티 키 플러시   │◄─────────┘                                         │
│   │  • 연결 종료        │                                                    │
│   └────────────────────┘                                                    │
│                │                                                             │
│   Phase 4: CLOSE_CONNECTIONS                                                 │
│   ┌────────────────────┐                                                    │
│   │  DatabasePool      │                                                    │
│   │  • 진행 중 쿼리 완료│                                                    │
│   │  • 연결 반환        │                                                    │
│   │  • 풀 종료          │                                                    │
│   └────────────────────┘                                                    │
│                                                                              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 5: POST_SHUTDOWN                                                      │
│                                                                              │
│   ┌────────────────────┐  ┌────────────────────┐                             │
│   │  MetricsReporter   │  │  Custom Hooks      │                             │
│   │  • 메트릭 플러시    │  │  • 로그 기록        │                             │
│   │  • 최종 보고        │  │  • 알림 발송        │                             │
│   └────────────────────┘  └────────────────────┘                             │
│                                                                              │
│   === Graceful Shutdown Completed ===                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Request Tracker 상태 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RequestTracker                                      │
│                                                                              │
│   [accepting=true]                    [accepting=false]                      │
│                                                                              │
│   새 요청 ──► tryAcquire() = true     새 요청 ──► tryAcquire() = false      │
│               activeRequests++                     503 반환                   │
│                                                                              │
│   완료 ────► release()                완료 ────► release()                   │
│               activeRequests--                     activeRequests--           │
│                                                    if 0 → drainLatch 해제    │
│                                                                              │
│   ┌──────────────────────────────────────────────────────────┐              │
│   │  stopAccepting() 호출 시:                                 │              │
│   │                                                          │              │
│   │  accepting = false                                       │              │
│   │  activeRequests > 0 → 드레인 대기                        │              │
│   │  activeRequests = 0 → 즉시 drainLatch 해제               │              │
│   └──────────────────────────────────────────────────────────┘              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 주요 구현

### ShutdownManager

```kotlin
class ShutdownManager(
    private val shutdownTimeout: Long = 30_000,
    private val drainTimeout: Long = 15_000,
    private val preShutdownDelay: Long = 3_000
) {
    fun register(component: LifecycleComponent)
    fun addShutdownHook(hook: () -> Unit)
    fun installShutdownHook()  // JVM Shutdown Hook

    fun shutdown() {
        // Phase 0: PRE_SHUTDOWN - 헬스 변경, 요청 거부
        // Phase 1: DRAIN - 진행 중 요청 대기
        // Phase 2: CLOSE_SERVICES - 서비스 종료
        // Phase 3: CLOSE_CACHES - 캐시 플러시/종료
        // Phase 4: CLOSE_CONNECTIONS - DB 연결 종료
        // Phase 5: POST_SHUTDOWN - 메트릭 플러시
    }
}
```

### RequestTracker

```kotlin
class RequestTracker {
    fun tryAcquire(): Boolean    // 요청 시작 (false면 거부)
    fun release()                // 요청 완료
    fun stopAccepting()          // 새 요청 수락 중단
    fun awaitDrain(timeout, unit): Boolean  // 모든 요청 완료 대기
    fun activeCount(): Int       // 활성 요청 수
}
```

### LifecycleComponent

```kotlin
interface LifecycleComponent {
    val componentName: String
    val shutdownPhase: ShutdownPhase  // 어느 단계에서 종료할지

    fun start()
    fun stop(timeout: Long = 5000)
    fun isRunning(): Boolean
}

// 사용 예시
class DatabasePool : AbstractLifecycleComponent(
    "DatabasePool",
    ShutdownPhase.CLOSE_CONNECTIONS  // 가장 마지막에 종료
) {
    override fun stop(timeout: Long) {
        // 진행 중 쿼리 완료 대기
        // 연결 반환 및 풀 종료
        super.stop(timeout)
    }
}
```

### GracefulServer

```kotlin
class GracefulServer(port: Int, shutdownManager: ShutdownManager) {

    fun handleRequest(requestId: String, handler: () -> String): RequestResult {
        // 1. 요청 수락 가능 확인
        if (!requestTracker.tryAcquire()) {
            return RequestResult(503, "Service Unavailable",
                headers = mapOf("Retry-After" to "5"))
        }

        try {
            return RequestResult(200, handler())
        } finally {
            requestTracker.release()  // 항상 해제
        }
    }
}
```

### Shutdown DSL

```kotlin
val shutdownManager = gracefulShutdown {
    shutdownTimeout = 30_000
    drainTimeout = 15_000
    preShutdownDelay = 3_000

    component(database)   // CLOSE_CONNECTIONS
    component(cache)      // CLOSE_CACHES
    component(consumer)   // CLOSE_SERVICES
    component(scheduler)  // CLOSE_SERVICES
    component(metrics)    // POST_SHUTDOWN

    onShutdown {
        println("Custom cleanup executed")
    }
}

shutdownManager.installShutdownHook()  // SIGTERM 핸들러 등록
```

## 롤링 배포 시나리오

```
시간 ──────────────────────────────────────────────►

인스턴스 A (이전 버전):
├── [정상 서비스] ──► SIGTERM ──► [PRE_SHUTDOWN] ──► [DRAIN] ──► [CLOSE] ──► 종료
│                     │
│                     ▼
│              Health: SHUTTING_DOWN
│              LB: 트래픽 중단 (3초)
│              새 요청: 503 반환
│              진행 중: 완료 대기
│
인스턴스 B (새 버전):
                ├── [시작] ──► Health: HEALTHY ──► LB: 트래픽 수신 ──► [정상 서비스]

결과: 무중단 배포 (Zero Downtime Deployment)
```

## 장점

1. **무중단 배포**: 롤링 배포 시 요청 손실 없음
2. **데이터 보호**: 버퍼 플러시, 트랜잭션 완료
3. **리소스 정리**: DB 연결, 파일 핸들 안전하게 정리
4. **메시지 보호**: 큐 메시지 ACK/NACK 보장
5. **단계별 종료**: 의존성 고려한 역순 종료
6. **타임아웃**: 무한 대기 방지

## 단점

1. **종료 지연**: 즉시 종료보다 시간 소요
2. **복잡성**: 단계별 종료 로직 구현 필요
3. **타임아웃 설정**: 적절한 값 선정 필요
4. **에지 케이스**: 매우 긴 요청 처리 고려 필요

## 적용 시점

- 컨테이너/쿠버네티스 환경 (SIGTERM 처리)
- 롤링/블루-그린 배포 시스템
- 메시지 큐 컨슈머 (메시지 유실 방지)
- 배치 작업 서버 (진행 중 작업 보호)
- 데이터베이스 연결이 있는 서버

## 문제점 vs 해결책 비교

| 문제점 | 해결책 |
|--------|--------|
| 즉시 종료로 요청 중단 | RequestTracker로 드레인 대기 |
| 리소스 정리 안됨 | LifecycleComponent 역순 종료 |
| 데이터 손실 | 버퍼 플러시 후 종료 |
| 메시지 유실 | 처리 완료 후 ACK, 타임아웃 시 NACK |
| 배포 중 에러 | Health Check → LB 트래픽 중단 |
| 무한 대기 | 단계별 타임아웃 적용 |

## Kubernetes 연동

```yaml
# Pod spec
spec:
  terminationGracePeriodSeconds: 30  # SIGTERM 후 대기 시간
  containers:
    - name: app
      livenessProbe:
        httpGet:
          path: /health/live
          port: 8080
      readinessProbe:
        httpGet:
          path: /health/ready
          port: 8080
      lifecycle:
        preStop:
          exec:
            command: ["sh", "-c", "sleep 5"]  # LB 인지 대기
```

## 관련 패턴

- **Circuit Breaker**: 장애 전파 방지
- **Health Check**: 서비스 상태 모니터링
- **Retry Pattern**: 종료 중 503 응답 시 재시도
- **Bulkhead**: 리소스 격리

## 참고 자료

- [Kubernetes Pod Lifecycle](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
- [SIGTERM Handling Best Practices](https://cloud.google.com/blog/products/containers-kubernetes/kubernetes-best-practices-terminating-with-grace)
- [Zero Downtime Deployment](https://martinfowler.com/bliki/BlueGreenDeployment.html)
- [Spring Boot Graceful Shutdown](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.graceful-shutdown)
