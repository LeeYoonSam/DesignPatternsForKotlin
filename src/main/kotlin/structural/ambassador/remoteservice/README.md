# Ambassador Pattern

## 개요

Ambassador Pattern은 원격 서비스 호출 시 발생하는 **횡단 관심사**(로깅, 재시도, 인증, 서킷브레이커, 모니터링 등)를 별도의 프록시 컴포넌트(Ambassador)로 분리하는 패턴입니다.

외교에서 대사(Ambassador)가 본국을 대리하여 외국과 소통하듯, Ambassador가 애플리케이션을 대리하여 원격 서비스와 통신합니다.

## 문제 상황

원격 서비스를 호출하는 클라이언트에 네트워크 관심사가 혼재:

```kotlin
class OrderServiceClient {
    fun getOrder(orderId: String): String {
        // 로깅
        println("주문 조회 요청: $orderId")
        // 서킷브레이커 확인
        if (circuitOpen) throw ServiceUnavailableException()
        // 재시도 루프
        for (attempt in 1..maxRetries) {
            try {
                // 인증 헤더 추가
                val headers = mapOf("Authorization" to "Bearer $token")
                // 실제 호출
                return callRemoteApi("/orders/$orderId", headers)
            } catch (e: Exception) {
                Thread.sleep(attempt * 100L)  // 지수 백오프
            }
        }
        throw RuntimeException("주문 조회 실패")
    }
}
```

**문제점:**
- 비즈니스 로직과 네트워크 관심사가 혼재
- 모든 서비스 클라이언트에서 동일한 코드 중복
- 레거시 서비스에 새로운 정책 추가 어려움

## 해결 방법

네트워크 관심사를 Ambassador로 분리하여 비즈니스 로직을 깔끔하게 유지합니다.

```
┌───────────────────┐          ┌────────────────────┐          ┌───────────────┐
│  Service Client   │          │    Ambassador      │          │ Remote Service│
│                   │          │                    │          │               │
│  비즈니스 로직만    │ ──────► │  • 로깅            │ ──────► │  실제 서비스    │
│  포함             │          │  • 인증            │          │               │
│                   │ ◄─────── │  • 재시도          │ ◄─────── │               │
│                   │          │  • 서킷브레이커     │          │               │
│                   │          │  • 캐싱            │          │               │
│                   │          │  • 메트릭          │          │               │
└───────────────────┘          └────────────────────┘          └───────────────┘
```

## 핵심 구현

### 1. Feature 인터페이스 (플러그인 방식)

```kotlin
interface AmbassadorFeature {
    val name: String
    val order: Int  // 실행 순서

    fun onRequest(request: ServiceRequest): ServiceRequest = request
    fun onResponse(request: ServiceRequest, response: ServiceResponse): ServiceResponse = response
    fun onError(request: ServiceRequest, error: Exception): ServiceResponse? = null
}
```

### 2. Ambassador (프록시)

```kotlin
class Ambassador(
    private val target: RemoteService,
    private val features: List<AmbassadorFeature>
) : RemoteService {

    override fun call(request: ServiceRequest): ServiceResponse {
        // 1. 요청 전처리 파이프라인
        var processed = request
        features.forEach { processed = it.onRequest(processed) }

        return try {
            // 2. 실제 호출
            var response = target.call(processed)
            // 3. 응답 후처리
            features.reversed().forEach { response = it.onResponse(processed, response) }
            response
        } catch (e: Exception) {
            // 4. 에러 처리
            features.forEach { f ->
                f.onError(processed, e)?.let { return it }
            }
            throw e
        }
    }
}
```

### 3. 깔끔한 서비스 클라이언트

```kotlin
// 비즈니스 로직만 포함 (네트워크 관심사 없음!)
class CleanOrderServiceClient(private val service: RemoteService) {
    fun getOrder(orderId: String): String {
        val response = service.call(ServiceRequest("GET", "/orders/$orderId"))
        return response.body
    }
}
```

### 4. Builder 패턴으로 구성

```kotlin
val ambassador = remoteService.withAmbassador()
    .withLogging()
    .withAuthentication { "jwt-token-here" }
    .withCircuitBreaker(failureThreshold = 5)
    .withRetry(maxRetries = 3)
    .withCaching(ttlMs = 5000)
    .withMetrics()
    .withRateLimit(maxRequestsPerSecond = 10)
    .build()

val orderClient = CleanOrderServiceClient(ambassador)
```

## 제공 Feature 목록

| Feature | 역할 | 실행 순서 |
|---------|------|----------|
| **RateLimit** | 초당 요청 수 제한 | -1 (최우선) |
| **CircuitBreaker** | 연속 실패 시 빠른 차단 | 0 |
| **Logging** | 요청/응답 로깅 | 1 |
| **Authentication** | 인증 토큰 자동 추가 | 2 |
| **Metrics** | 요청 수, 성공률, 지연시간 수집 | 3 |
| **Caching** | GET 응답 캐싱 | 5 |
| **Retry** | 실패 시 지수 백오프 재시도 | 10 (최후) |

## 요청/응답 흐름

```
요청 → RateLimit → CircuitBreaker → Logging → Auth → Metrics → Caching → [원격 서비스]
응답 ← RateLimit ← CircuitBreaker ← Logging ← Auth ← Metrics ← Caching ← [원격 서비스]
```

## 용도별 Ambassador 구성

### 내부 서비스
```kotlin
internalService.withAmbassador()
    .withLogging()
    .withMetrics()
    .build()
```

### 외부 API
```kotlin
externalService.withAmbassador()
    .withRateLimit(5)
    .withCircuitBreaker(5)
    .withLogging()
    .withAuthentication { getOAuthToken() }
    .withRetry(3)
    .withCaching(10000)
    .build()
```

### 레거시 서비스
```kotlin
legacyService.withAmbassador()
    .withLogging()
    .withAuthentication { "legacy-token" }
    .withRateLimit(10)
    .build()
// 레거시 코드 수정 없이 인증/로깅 추가!
```

## Sidecar Pattern과의 비교

| 측면 | Ambassador | Sidecar |
|------|-----------|---------|
| 배포 단위 | 같은 프로세스 내 | 별도 프로세스/컨테이너 |
| 통신 방식 | 인프로세스 (메서드 호출) | IPC / localhost 네트워크 |
| 초점 | 원격 서비스 호출 관심사 | 서비스 전체 횡단 관심사 |
| 성능 | 오버헤드 없음 | 네트워크 오버헤드 존재 |
| 언어 독립성 | 동일 언어 | 다른 언어 가능 |

## 장점

1. **관심사 분리**: 비즈니스 로직과 네트워크 관심사 완전 분리
2. **재사용성**: 동일 Ambassador를 여러 서비스 클라이언트에 적용
3. **레거시 호환**: 코드 수정 없이 기존 서비스에 새 기능 추가
4. **조합 가능**: 필요한 Feature만 선택적으로 조합
5. **정책 통합**: 통신 정책 변경 시 Ambassador만 수정

## 단점

1. **지연 시간 증가**: 프록시 레이어 추가로 미세한 오버헤드
2. **복잡성**: 간단한 서비스 호출에는 과도할 수 있음
3. **디버깅**: 중간 레이어 추가로 디버깅 복잡도 증가

## 적용 시점

- 여러 서비스 클라이언트에서 동일한 네트워크 관심사가 반복될 때
- 레거시 서비스에 새로운 정책을 추가해야 할 때
- 서비스 간 통신 정책을 중앙 관리하고 싶을 때
- 마이크로서비스 아키텍처에서 횡단 관심사를 분리할 때

## 관련 패턴

- **Proxy Pattern**: Ambassador는 원격 서비스를 위한 특수한 프록시
- **Decorator Pattern**: Feature를 체인으로 추가하는 방식이 유사
- **Sidecar Pattern**: 별도 프로세스로 분리된 Ambassador
- **Circuit Breaker Pattern**: Ambassador의 핵심 Feature 중 하나
- **Chain of Responsibility**: Feature 파이프라인 구조가 유사

## 참고 자료

- [Microsoft - Ambassador Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/ambassador)
- [Cloud Design Patterns](https://learn.microsoft.com/en-us/azure/architecture/patterns/)
- [Envoy Proxy](https://www.envoyproxy.io/) - Ambassador 개념의 인프라 레벨 구현
