# Error Handling Pattern

## 개요

Error Handling Pattern은 API 클라이언트 앱에서 발생하는 다양한 에러를 체계적으로 처리하는 패턴입니다. **sealed class 기반 에러 계층**, **Result 타입**, **에러 변환**, **재시도 정책**, **폴백 전략**을 통합하여 타입 안전하고 복구 가능한 에러 처리를 구현합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **DomainError** | sealed class 기반 에러 계층 (타입 안전) |
| **DomainResult** | 성공/실패를 명시적으로 표현하는 Result 타입 |
| **ErrorMapper** | 인프라 에러 → 도메인 에러 변환 |
| **RetryPolicy** | 지수 백오프 기반 재시도 정책 |
| **CacheFallback** | 네트워크 실패 시 캐시 폴백 |
| **ValidationBuilder** | DSL 기반 유효성 검사 |
| **UiErrorMapper** | 에러 → UI 상태 변환 |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              UI Layer                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         ViewModel                                      │  │
│  │                                                                        │  │
│  │   ┌─────────────────────────────────────────────────────────────┐     │  │
│  │   │                      UiState                                 │     │  │
│  │   │  • data: T?                                                  │     │  │
│  │   │  • isLoading: Boolean                                        │     │  │
│  │   │  • error: UiErrorState                                       │     │  │
│  │   └──────────────────────────┬──────────────────────────────────┘     │  │
│  │                              │                                         │  │
│  │   ┌──────────────────────────▼──────────────────────────────────┐     │  │
│  │   │                   UiErrorMapper                              │     │  │
│  │   │  DomainError → UiErrorState                                  │     │  │
│  │   │  • NoConnection → FullScreen (재시도 버튼)                   │     │  │
│  │   │  • Timeout → Snackbar (재시도 액션)                          │     │  │
│  │   │  • ValidationError → InlineField (필드별 에러)               │     │  │
│  │   │  • AuthError → Dialog (로그인 유도)                          │     │  │
│  │   └─────────────────────────────────────────────────────────────┘     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ DomainResult<T, DomainError>
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Domain Layer                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          Repository                                    │  │
│  │                                                                        │  │
│  │   1. Validation ─────────────────────────────────────────┐            │  │
│  │      │                                                    │            │  │
│  │      │  validate {                                        │            │  │
│  │      │      require("email", email)                       │            │  │
│  │      │      email("email", email)                         │            │  │
│  │      │      minLength("password", password, 8)            │            │  │
│  │      │  }                                                 │            │  │
│  │      │                                                    │            │  │
│  │      ▼ ApiResult<Unit>                                    │            │  │
│  │   2. Retry + Fallback ────────────────────────────────────┤            │  │
│  │      │                                                    │            │  │
│  │      │  cache.fetchWithCacheFallback(key) {              │            │  │
│  │      │      withRetry(RetryPolicy) { attempt ->          │            │  │
│  │      │          fetchFromApi(...)                        │            │  │
│  │      │      }                                            │            │  │
│  │      │  }                                                │            │  │
│  │      │                                                    │            │  │
│  │      ▼ ApiResult<T>                                       │            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Infrastructure Layer                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         ErrorMapper                                    │  │
│  │                                                                        │  │
│  │   Exception ─────────────────────────────────────────► DomainError    │  │
│  │                                                                        │  │
│  │   • UnknownHostException ──────────► NetworkError.NoConnection        │  │
│  │   • SocketTimeoutException ────────► NetworkError.Timeout             │  │
│  │   • HttpException(401) ────────────► AuthError.Unauthorized           │  │
│  │   • HttpException(404) ────────────► BusinessError.NotFound           │  │
│  │   • HttpException(500) ────────────► NetworkError.ServerError         │  │
│  │   • Other Exception ───────────────► UnexpectedError                  │  │
│  │                                                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 에러 계층 구조

```
DomainError (sealed class)
├── NetworkError
│   ├── NoConnection      (isRetryable = true)
│   ├── Timeout           (isRetryable = true)
│   ├── ServerError       (isRetryable = 5xx)
│   └── ClientError       (isRetryable = 429)
├── AuthError
│   ├── Unauthorized
│   ├── TokenExpired      (isRetryable = true, 토큰 갱신 후)
│   └── InvalidCredentials
├── ValidationError
│   ├── Required
│   ├── InvalidFormat
│   ├── OutOfRange
│   └── Multiple          (여러 에러 집계)
├── BusinessError
│   ├── NotFound
│   ├── InsufficientBalance
│   ├── DuplicateEntry
│   └── OperationNotAllowed
└── UnexpectedError
```

## Result 타입 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                     DomainResult<T, E>                           │
│                                                                  │
│   ┌─────────────────────┐     ┌─────────────────────┐           │
│   │      Success<T>     │     │     Failure<E>      │           │
│   │   • data: T         │     │   • error: E        │           │
│   └──────────┬──────────┘     └──────────┬──────────┘           │
│              │                           │                       │
│              └───────────┬───────────────┘                       │
│                          │                                       │
│   ┌──────────────────────▼──────────────────────────┐           │
│   │                  Operations                      │           │
│   │                                                  │           │
│   │   map { transform(it) }         → Result<R, E>  │           │
│   │   flatMap { operation(it) }     → Result<R, E>  │           │
│   │   mapError { convertError(it) } → Result<T, F>  │           │
│   │   onSuccess { handle(it) }      → Result<T, E>  │           │
│   │   onFailure { handle(it) }      → Result<T, E>  │           │
│   │   recoverWith { fallback(it) }  → Result<T, E>  │           │
│   │   getOrDefault(default)         → T             │           │
│   │   getOrElse { compute(it) }     → T             │           │
│   │   getOrThrow()                  → T (throws)    │           │
│   │                                                  │           │
│   └──────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

## 재시도 전략 (Exponential Backoff)

```
RetryPolicy(maxAttempts = 3, initialDelayMs = 1000, factor = 2.0)

시도 1 ──────► 실패 (isRetryable)
              │
              ▼ delay 1000ms
시도 2 ──────► 실패 (isRetryable)
              │
              ▼ delay 2000ms
시도 3 ──────► 성공 또는 최종 실패
              │
              ▼
         ApiResult<T>
```

## 주요 구현

### DomainError (sealed class)

```kotlin
sealed class DomainError(
    open val message: String,
    open val cause: Throwable? = null,
    open val errorCode: String? = null
) {
    abstract fun toUserMessage(): String
    open val isRetryable: Boolean = false
}

sealed class NetworkError(...) : DomainError(...) {
    data class NoConnection(...) : NetworkError(...) {
        override fun toUserMessage() = "인터넷 연결을 확인해주세요"
        override val isRetryable = true
    }

    data class Timeout(val timeoutMs: Long, ...) : NetworkError(...) {
        override fun toUserMessage() = "서버 응답이 늦어지고 있습니다"
        override val isRetryable = true
    }
}
```

### DomainResult (Result Type)

```kotlin
sealed class DomainResult<out T, out E : DomainError> {
    data class Success<T>(val data: T) : DomainResult<T, Nothing>()
    data class Failure<E : DomainError>(val error: E) : DomainResult<Nothing, E>()

    inline fun <R> map(transform: (T) -> R): DomainResult<R, E>
    inline fun <R> flatMap(transform: (T) -> DomainResult<R, E>): DomainResult<R, E>
    inline fun onSuccess(action: (T) -> Unit): DomainResult<T, E>
    inline fun onFailure(action: (E) -> Unit): DomainResult<T, E>
}

// 사용
repository.getUser(userId)
    .map { it.email.uppercase() }
    .flatMap { validateEmail(it) }
    .onSuccess { println("이메일: $it") }
    .onFailure { println("에러: ${it.toUserMessage()}") }
```

### ErrorMapper

```kotlin
object ErrorMapper {
    fun map(exception: Throwable): DomainError = when (exception) {
        is UnknownHostException,
        is ConnectException -> NetworkError.NoConnection(exception)

        is SocketTimeoutException -> NetworkError.Timeout(30_000, exception)

        is HttpException -> when (exception.code) {
            in 400..499 -> NetworkError.ClientError(exception.code)
            in 500..599 -> NetworkError.ServerError(exception.code)
            else -> UnexpectedError(exception.message, exception)
        }

        else -> UnexpectedError(exception.message ?: "Unknown", exception)
    }
}
```

### RetryPolicy

```kotlin
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val factor: Double = 2.0,
    val retryOn: (DomainError) -> Boolean = { it.isRetryable }
)

suspend fun <T> withRetry(
    policy: RetryPolicy,
    block: suspend (attempt: Int) -> ApiResult<T>
): ApiResult<T> {
    var currentDelay = policy.initialDelayMs

    repeat(policy.maxAttempts) { attempt ->
        val result = block(attempt + 1)

        when (result) {
            is Success -> return result
            is Failure -> {
                if (!policy.retryOn(result.error)) return result
                delay(currentDelay)
                currentDelay = minOf((currentDelay * policy.factor).toLong(), policy.maxDelayMs)
            }
        }
    }
    return Failure(lastError)
}
```

### ValidationBuilder (DSL)

```kotlin
class ValidationBuilder {
    private val errors = mutableListOf<ValidationError>()

    fun require(field: String, value: String?): ValidationBuilder
    fun email(field: String, value: String?): ValidationBuilder
    fun minLength(field: String, value: String?, min: Int): ValidationBuilder
    fun range(field: String, value: Number?, min: Number?, max: Number?): ValidationBuilder

    fun build(): ApiResult<Unit>
}

// 사용
val result = validate {
    require("name", name)
    require("email", email)
    email("email", email)
    require("password", password)
    minLength("password", password, 8)
}
// → Success(Unit) 또는 Failure(ValidationError.Multiple)
```

### CacheFallback

```kotlin
class CacheFallback<T> {
    suspend fun fetchWithCacheFallback(
        key: String,
        fetch: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        return fetch().recoverWith { error ->
            if (error is NetworkError) {
                get(key)?.let { return@recoverWith Success(it) }
            }
            Failure(error)
        }
    }
}
```

### UiErrorMapper

```kotlin
object UiErrorMapper {
    fun map(error: DomainError, onRetry: (() -> Unit)?): UiErrorState = when (error) {
        is NetworkError.NoConnection -> UiErrorState.FullScreen(
            title = "연결 오류",
            message = error.toUserMessage(),
            retryButton = true,
            onRetry = onRetry
        )

        is NetworkError.Timeout -> UiErrorState.Snackbar(
            message = error.toUserMessage(),
            actionLabel = "재시도",
            onAction = onRetry
        )

        is ValidationError -> UiErrorState.InlineField(
            field = error.field,
            message = error.toUserMessage()
        )

        else -> UiErrorState.Snackbar(message = error.toUserMessage())
    }
}
```

## 장점

1. **타입 안전성**: sealed class로 컴파일 타임에 에러 처리 강제
2. **when exhaustive**: 새 에러 추가 시 처리 누락 방지
3. **계층 분리**: 인프라 에러가 UI까지 전파되지 않음
4. **복구 가능**: isRetryable로 재시도 가능 여부 표현
5. **사용자 친화적**: toUserMessage()로 다국어 지원 용이
6. **테스트 용이**: Result 타입으로 에러 케이스 테스트 쉬움
7. **체이닝**: map, flatMap으로 함수형 에러 처리

## 단점

1. **보일러플레이트**: 에러 계층 정의에 코드량 증가
2. **러닝 커브**: Result 타입과 에러 계층 이해 필요
3. **에러 전파**: 매번 Result를 반환하고 처리해야 함
4. **sealed class 제한**: 외부 모듈에서 확장 불가

## 적용 시점

- API 클라이언트 앱
- 복잡한 에러 복구가 필요한 시스템
- 다국어 에러 메시지 지원
- 에러 타입별 다른 UI 표시 필요
- 재시도/폴백 전략이 중요한 네트워크 앱

## 문제점 vs 해결책 비교

| 문제점 | 해결책 |
|--------|--------|
| Exception 삼키기 | Result 타입으로 에러 명시적 전달 |
| 넓은 catch | sealed class로 에러 타입 구분 |
| 문자열 에러 | DomainError 객체 사용 |
| Nullable 남용 | Result.Success vs Failure 구분 |
| 에러 코드 | sealed class + when exhaustive |
| 일관성 없음 | 통일된 Result + DomainError 패턴 |
| 복구 전략 없음 | RetryPolicy + CacheFallback |
| Exception 전파 | ErrorMapper로 도메인 에러 변환 |

## 관련 패턴

- **Railway Oriented Programming**: Result 타입 체이닝
- **Strategy Pattern**: RetryPolicy가 재시도 전략
- **Builder Pattern**: ValidationBuilder
- **Chain of Responsibility**: 에러 복구 체인
- **Decorator Pattern**: recoverWith로 폴백 감싸기

## 실제 라이브러리

| 라이브러리 | 특징 |
|------------|------|
| **Arrow-kt (Either)** | Kotlin 함수형 라이브러리의 Either 타입 |
| **Result (kotlin-result)** | 가벼운 Result 타입 라이브러리 |
| **Kotlin Result** | 표준 라이브러리 Result<T> |
| **Retrofit + Response** | HTTP Response 래퍼 |
| **Ktor + HttpResponse** | Ktor 클라이언트 응답 처리 |

## 참고 자료

- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/)
- [Arrow-kt Either](https://arrow-kt.io/docs/apidocs/arrow-core/arrow.core/-either/)
- [Kotlin Result](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-result/)
- [Android Error Handling](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [Effective Kotlin: Error Handling](https://kt.academy/article/ek-exceptions)
