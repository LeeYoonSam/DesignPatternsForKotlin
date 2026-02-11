package architecture.errorhandling.api

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime

/**
 * Error Handling Pattern - 해결책
 *
 * API 클라이언트 앱에 체계적인 에러 처리 전략을 적용:
 * - 도메인 에러 계층: sealed class로 타입 안전한 에러 정의
 * - Result 타입: 성공/실패를 명시적으로 표현
 * - 에러 변환: 인프라 에러를 도메인 에러로 변환
 * - 복구 전략: 재시도, 폴백, Circuit Breaker
 * - UI 에러 상태: 사용자 친화적 에러 표시
 *
 * 핵심 구성:
 * - DomainError: sealed class 에러 계층
 * - Result<T, E>: 성공/실패를 담는 컨테이너
 * - ErrorMapper: 인프라 에러 → 도메인 에러 변환
 * - RetryPolicy: 재시도 전략
 * - ErrorRecovery: 폴백 및 복구
 */

// ============================================================
// 1. Domain Error 계층 (sealed class)
// ============================================================

/**
 * 도메인 에러 기본 클래스
 * sealed class로 정의하여 when exhaustive 체크 가능
 */
sealed class DomainError(
    open val message: String,
    open val cause: Throwable? = null,
    open val errorCode: String? = null
) {
    /** 에러 로깅용 상세 정보 */
    open fun toLogString(): String = "${this::class.simpleName}: $message"

    /** 사용자에게 보여줄 메시지 (다국어 지원 가능) */
    abstract fun toUserMessage(): String

    /** 재시도 가능 여부 */
    open val isRetryable: Boolean = false
}

/**
 * 네트워크 관련 에러
 */
sealed class NetworkError(
    override val message: String,
    override val cause: Throwable? = null
) : DomainError(message, cause) {

    /** 연결 실패 */
    data class NoConnection(
        override val cause: Throwable? = null
    ) : NetworkError("인터넷 연결이 없습니다", cause) {
        override fun toUserMessage() = "인터넷 연결을 확인해주세요"
        override val isRetryable = true
    }

    /** 타임아웃 */
    data class Timeout(
        val timeoutMs: Long,
        override val cause: Throwable? = null
    ) : NetworkError("요청 시간 초과: ${timeoutMs}ms", cause) {
        override fun toUserMessage() = "서버 응답이 늦어지고 있습니다. 잠시 후 다시 시도해주세요"
        override val isRetryable = true
    }

    /** 서버 에러 (5xx) */
    data class ServerError(
        val statusCode: Int,
        val serverMessage: String? = null
    ) : NetworkError("서버 에러: $statusCode", null) {
        override fun toUserMessage() = "서버에 문제가 발생했습니다. 잠시 후 다시 시도해주세요"
        override val isRetryable = statusCode in 500..599
        override val errorCode = "SERVER_$statusCode"
    }

    /** 클라이언트 에러 (4xx) */
    data class ClientError(
        val statusCode: Int,
        val serverMessage: String? = null
    ) : NetworkError("클라이언트 에러: $statusCode", null) {
        override fun toUserMessage() = when (statusCode) {
            400 -> "잘못된 요청입니다"
            401 -> "로그인이 필요합니다"
            403 -> "접근 권한이 없습니다"
            404 -> "요청한 정보를 찾을 수 없습니다"
            429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"
            else -> "요청을 처리할 수 없습니다"
        }
        override val isRetryable = statusCode == 429
        override val errorCode = "CLIENT_$statusCode"
    }
}

/**
 * 인증 관련 에러
 */
sealed class AuthError(
    override val message: String
) : DomainError(message) {

    object Unauthorized : AuthError("인증되지 않음") {
        override fun toUserMessage() = "로그인이 필요합니다"
    }

    object TokenExpired : AuthError("토큰 만료") {
        override fun toUserMessage() = "세션이 만료되었습니다. 다시 로그인해주세요"
        override val isRetryable = true  // 토큰 갱신 후 재시도 가능
    }

    data class InvalidCredentials(
        val reason: String
    ) : AuthError("인증 실패: $reason") {
        override fun toUserMessage() = "이메일 또는 비밀번호가 올바르지 않습니다"
    }
}

/**
 * 입력 유효성 검사 에러
 */
sealed class ValidationError(
    override val message: String,
    open val field: String? = null
) : DomainError(message) {

    data class Required(
        override val field: String
    ) : ValidationError("필수 입력: $field", field) {
        override fun toUserMessage() = "${fieldDisplayName(field)}을(를) 입력해주세요"
    }

    data class InvalidFormat(
        override val field: String,
        val expectedFormat: String
    ) : ValidationError("잘못된 형식: $field", field) {
        override fun toUserMessage() = "${fieldDisplayName(field)} 형식이 올바르지 않습니다"
    }

    data class OutOfRange(
        override val field: String,
        val min: Any?,
        val max: Any?
    ) : ValidationError("범위 초과: $field", field) {
        override fun toUserMessage() = when {
            min != null && max != null -> "${fieldDisplayName(field)}은(는) $min ~ $max 사이여야 합니다"
            min != null -> "${fieldDisplayName(field)}은(는) $min 이상이어야 합니다"
            max != null -> "${fieldDisplayName(field)}은(는) $max 이하여야 합니다"
            else -> "${fieldDisplayName(field)} 값이 올바르지 않습니다"
        }
    }

    data class Multiple(
        val errors: List<ValidationError>
    ) : ValidationError("다중 유효성 오류: ${errors.size}개") {
        override fun toUserMessage() = errors.joinToString("\n") { it.toUserMessage() }
    }

    companion object {
        private fun fieldDisplayName(field: String): String = when (field) {
            "email" -> "이메일"
            "password" -> "비밀번호"
            "name" -> "이름"
            "phone" -> "전화번호"
            "amount" -> "금액"
            else -> field
        }
    }
}

/**
 * 비즈니스 로직 에러
 */
sealed class BusinessError(
    override val message: String,
    override val errorCode: String
) : DomainError(message, errorCode = errorCode) {

    data class NotFound(
        val resourceType: String,
        val resourceId: String
    ) : BusinessError("$resourceType 을(를) 찾을 수 없음: $resourceId", "NOT_FOUND") {
        override fun toUserMessage() = "요청하신 정보를 찾을 수 없습니다"
    }

    data class InsufficientBalance(
        val required: Long,
        val available: Long
    ) : BusinessError("잔액 부족: 필요 $required, 가용 $available", "INSUFFICIENT_BALANCE") {
        override fun toUserMessage() = "잔액이 부족합니다"
    }

    data class DuplicateEntry(
        val field: String,
        val value: String
    ) : BusinessError("중복: $field = $value", "DUPLICATE") {
        override fun toUserMessage() = "이미 사용 중인 ${ValidationError.fieldDisplayName(field)}입니다"
    }

    data class OperationNotAllowed(
        val operation: String,
        val reason: String
    ) : BusinessError("허용되지 않는 작업: $operation - $reason", "NOT_ALLOWED") {
        override fun toUserMessage() = reason
    }
}

/**
 * 예상치 못한 에러
 */
data class UnexpectedError(
    override val message: String,
    override val cause: Throwable?
) : DomainError(message, cause, "UNEXPECTED") {
    override fun toUserMessage() = "예상치 못한 오류가 발생했습니다"
    override fun toLogString() = "UnexpectedError: $message\n${cause?.stackTraceToString()}"
}

// ============================================================
// 2. Result 타입
// ============================================================

/**
 * 성공/실패를 명시적으로 표현하는 Result 타입
 * Kotlin 표준 Result<T>와 유사하지만 에러 타입을 지정 가능
 */
sealed class DomainResult<out T, out E : DomainError> {
    data class Success<T>(val data: T) : DomainResult<T, Nothing>()
    data class Failure<E : DomainError>(val error: E) : DomainResult<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): DomainResult<R, E> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> DomainResult<R, @UnsafeVariance E>): DomainResult<R, E> =
        when (this) {
            is Success -> transform(data)
            is Failure -> this
        }

    inline fun <F : DomainError> mapError(transform: (E) -> F): DomainResult<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    inline fun onSuccess(action: (T) -> Unit): DomainResult<T, E> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (E) -> Unit): DomainResult<T, E> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw DomainException(error)
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> default
    }

    inline fun getOrElse(onFailure: (E) -> @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(data: T): DomainResult<T, Nothing> = Success(data)
        fun <E : DomainError> failure(error: E): DomainResult<Nothing, E> = Failure(error)

        inline fun <T> runCatching(block: () -> T): DomainResult<T, DomainError> {
            return try {
                Success(block())
            } catch (e: DomainException) {
                Failure(e.error)
            } catch (e: Exception) {
                Failure(UnexpectedError(e.message ?: "Unknown error", e))
            }
        }
    }
}

class DomainException(val error: DomainError) : Exception(error.message, error.cause)

// 타입 별칭으로 간단하게
typealias ApiResult<T> = DomainResult<T, DomainError>

// ============================================================
// 3. 에러 매퍼 (인프라 → 도메인)
// ============================================================

/**
 * Exception을 DomainError로 변환
 */
object ErrorMapper {
    fun map(exception: Throwable): DomainError = when (exception) {
        is DomainException -> exception.error

        // 네트워크 에러
        is java.net.UnknownHostException,
        is java.net.ConnectException -> NetworkError.NoConnection(exception)

        is java.net.SocketTimeoutException -> NetworkError.Timeout(30_000, exception)

        is java.io.IOException -> NetworkError.NoConnection(exception)

        // HTTP 에러 (실제로는 Retrofit/Ktor에서 변환)
        is HttpException -> when (exception.code) {
            in 400..499 -> NetworkError.ClientError(exception.code, exception.message)
            in 500..599 -> NetworkError.ServerError(exception.code, exception.message)
            else -> UnexpectedError(exception.message ?: "HTTP Error", exception)
        }

        // 기타
        else -> UnexpectedError(exception.message ?: "Unknown error", exception)
    }

    // HTTP 응답에서 에러 매핑 (상태 코드 기반)
    fun mapHttpError(statusCode: Int, body: String?): DomainError = when (statusCode) {
        400 -> ValidationError.InvalidFormat("request", "JSON")
        401 -> AuthError.Unauthorized
        403 -> AuthError.InvalidCredentials("권한 없음")
        404 -> BusinessError.NotFound("resource", "unknown")
        409 -> BusinessError.DuplicateEntry("unknown", "unknown")
        429 -> NetworkError.ClientError(429, "Too Many Requests")
        in 500..599 -> NetworkError.ServerError(statusCode, body)
        else -> UnexpectedError("HTTP $statusCode: $body", null)
    }
}

// 시뮬레이션용 HTTP Exception
data class HttpException(val code: Int, override val message: String) : Exception(message)

// ============================================================
// 4. 재시도 정책 (Retry Policy)
// ============================================================

/**
 * 재시도 정책
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val factor: Double = 2.0,  // Exponential backoff
    val retryOn: (DomainError) -> Boolean = { it.isRetryable }
)

/**
 * 재시도 실행기
 */
suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy(),
    block: suspend (attempt: Int) -> ApiResult<T>
): ApiResult<T> {
    var currentDelay = policy.initialDelayMs
    var lastError: DomainError? = null

    repeat(policy.maxAttempts) { attempt ->
        val result = block(attempt + 1)

        when (result) {
            is DomainResult.Success -> return result
            is DomainResult.Failure -> {
                lastError = result.error

                if (!policy.retryOn(result.error) || attempt == policy.maxAttempts - 1) {
                    return result
                }

                println("    [Retry] 시도 ${attempt + 1} 실패: ${result.error.message}")
                println("    [Retry] ${currentDelay}ms 후 재시도...")
                delay(currentDelay)
                currentDelay = minOf((currentDelay * policy.factor).toLong(), policy.maxDelayMs)
            }
        }
    }

    return DomainResult.failure(lastError ?: UnexpectedError("재시도 실패", null))
}

// ============================================================
// 5. 폴백 및 복구 전략
// ============================================================

/**
 * 폴백 전략
 */
suspend fun <T> ApiResult<T>.recoverWith(
    fallback: suspend (DomainError) -> ApiResult<T>
): ApiResult<T> = when (this) {
    is DomainResult.Success -> this
    is DomainResult.Failure -> fallback(error)
}

/**
 * 캐시 폴백
 */
class CacheFallback<T>(
    private val cache: MutableMap<String, Pair<T, LocalDateTime>> = mutableMapOf(),
    private val ttlMinutes: Long = 60
) {
    fun get(key: String): T? {
        val (value, timestamp) = cache[key] ?: return null
        return if (timestamp.plusMinutes(ttlMinutes).isAfter(LocalDateTime.now())) {
            value
        } else {
            cache.remove(key)
            null
        }
    }

    fun put(key: String, value: T) {
        cache[key] = value to LocalDateTime.now()
    }

    suspend fun getOrFetch(
        key: String,
        fetch: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        // 캐시 확인
        get(key)?.let { return DomainResult.success(it) }

        // fetch 시도
        return fetch().onSuccess { put(key, it) }
    }

    suspend fun fetchWithCacheFallback(
        key: String,
        fetch: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        return fetch().recoverWith { error ->
            // 네트워크 에러 시 캐시 폴백
            if (error is NetworkError) {
                get(key)?.let {
                    println("    [Cache] 폴백: 캐시에서 반환")
                    return@recoverWith DomainResult.success(it)
                }
            }
            DomainResult.failure(error)
        }
    }
}

// ============================================================
// 6. 유효성 검사 빌더
// ============================================================

/**
 * 유효성 검사 빌더
 */
class ValidationBuilder {
    private val errors = mutableListOf<ValidationError>()

    fun require(field: String, value: String?, message: String? = null): ValidationBuilder {
        if (value.isNullOrBlank()) {
            errors.add(ValidationError.Required(field))
        }
        return this
    }

    fun email(field: String, value: String?): ValidationBuilder {
        if (value != null && !value.contains("@")) {
            errors.add(ValidationError.InvalidFormat(field, "email"))
        }
        return this
    }

    fun minLength(field: String, value: String?, min: Int): ValidationBuilder {
        if (value != null && value.length < min) {
            errors.add(ValidationError.OutOfRange(field, min, null))
        }
        return this
    }

    fun range(field: String, value: Number?, min: Number?, max: Number?): ValidationBuilder {
        if (value != null) {
            val v = value.toDouble()
            val minV = min?.toDouble()
            val maxV = max?.toDouble()
            if ((minV != null && v < minV) || (maxV != null && v > maxV)) {
                errors.add(ValidationError.OutOfRange(field, min, max))
            }
        }
        return this
    }

    fun build(): ApiResult<Unit> {
        return when {
            errors.isEmpty() -> DomainResult.success(Unit)
            errors.size == 1 -> DomainResult.failure(errors.first())
            else -> DomainResult.failure(ValidationError.Multiple(errors))
        }
    }
}

fun validate(block: ValidationBuilder.() -> Unit): ApiResult<Unit> {
    return ValidationBuilder().apply(block).build()
}

// ============================================================
// 7. UI 에러 상태
// ============================================================

/**
 * UI에서 사용할 에러 상태
 */
sealed class UiErrorState {
    object None : UiErrorState()

    data class Snackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : UiErrorState()

    data class Dialog(
        val title: String,
        val message: String,
        val positiveButton: String = "확인",
        val onPositive: () -> Unit = {},
        val negativeButton: String? = null,
        val onNegative: (() -> Unit)? = null
    ) : UiErrorState()

    data class FullScreen(
        val title: String,
        val message: String,
        val iconRes: String? = null,
        val retryButton: Boolean = false,
        val onRetry: (() -> Unit)? = null
    ) : UiErrorState()

    data class InlineField(
        val field: String,
        val message: String
    ) : UiErrorState()
}

/**
 * 에러를 UI 상태로 변환
 */
object UiErrorMapper {
    fun map(error: DomainError, onRetry: (() -> Unit)? = null): UiErrorState = when (error) {
        is NetworkError.NoConnection -> UiErrorState.FullScreen(
            title = "연결 오류",
            message = error.toUserMessage(),
            iconRes = "ic_no_connection",
            retryButton = true,
            onRetry = onRetry
        )

        is NetworkError.Timeout -> UiErrorState.Snackbar(
            message = error.toUserMessage(),
            actionLabel = "재시도",
            onAction = onRetry
        )

        is NetworkError.ServerError -> UiErrorState.Dialog(
            title = "서버 오류",
            message = error.toUserMessage(),
            positiveButton = "재시도",
            onPositive = { onRetry?.invoke() },
            negativeButton = "취소"
        )

        is AuthError.Unauthorized, is AuthError.TokenExpired -> UiErrorState.Dialog(
            title = "로그인 필요",
            message = error.toUserMessage(),
            positiveButton = "로그인",
            onPositive = { /* 로그인 화면으로 이동 */ }
        )

        is ValidationError -> when (error) {
            is ValidationError.Multiple -> UiErrorState.Dialog(
                title = "입력 오류",
                message = error.toUserMessage()
            )
            else -> UiErrorState.InlineField(
                field = error.field ?: "unknown",
                message = error.toUserMessage()
            )
        }

        is BusinessError -> UiErrorState.Snackbar(message = error.toUserMessage())

        else -> UiErrorState.Snackbar(message = error.toUserMessage())
    }
}

// ============================================================
// 8. 사용 예시 - Repository
// ============================================================

data class User(val id: String, val name: String, val email: String)

/**
 * UserRepository - 에러 처리 적용
 */
class UserRepository(
    private val cache: CacheFallback<User> = CacheFallback()
) {
    suspend fun getUser(userId: String): ApiResult<User> {
        // 유효성 검사
        val validation = validate {
            require("userId", userId)
        }
        if (validation.isFailure) return validation.mapError { it }

        // 캐시 + 네트워크 with 재시도
        return cache.fetchWithCacheFallback(userId) {
            withRetry(RetryPolicy(maxAttempts = 3)) { attempt ->
                println("  [API] 사용자 조회 시도 $attempt: $userId")
                fetchUserFromApi(userId)
            }
        }
    }

    private suspend fun fetchUserFromApi(userId: String): ApiResult<User> {
        delay(100) // 네트워크 지연 시뮬레이션

        // 에러 시뮬레이션
        return when {
            userId == "error" -> DomainResult.failure(NetworkError.NoConnection())
            userId == "timeout" -> DomainResult.failure(NetworkError.Timeout(30000))
            userId == "notfound" -> DomainResult.failure(BusinessError.NotFound("User", userId))
            userId == "server" -> DomainResult.failure(NetworkError.ServerError(500))
            Math.random() > 0.7 -> DomainResult.failure(NetworkError.Timeout(30000))
            else -> DomainResult.success(User(userId, "홍길동", "hong@example.com"))
        }
    }

    suspend fun createUser(name: String, email: String, password: String): ApiResult<User> {
        // 다중 유효성 검사
        val validation = validate {
            require("name", name)
            require("email", email)
            email("email", email)
            require("password", password)
            minLength("password", password, 8)
        }

        return validation.flatMap {
            // 유효성 통과 시 API 호출
            delay(100)
            if (email == "existing@example.com") {
                DomainResult.failure(BusinessError.DuplicateEntry("email", email))
            } else {
                DomainResult.success(User("new-id", name, email))
            }
        }
    }
}

// ============================================================
// 9. ViewModel 예시
// ============================================================

/**
 * UI 상태
 */
data class UserUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: UiErrorState = UiErrorState.None
)

/**
 * ViewModel - 에러 처리 및 UI 상태 관리
 */
class UserViewModel(
    private val repository: UserRepository = UserRepository()
) {
    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun loadUser(userId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = UiErrorState.None) }

            repository.getUser(userId)
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                }
                .onFailure { error ->
                    val uiError = UiErrorMapper.map(error) { loadUser(userId) }
                    _uiState.update { it.copy(isLoading = false, error = uiError) }

                    // 에러 로깅
                    println("  [Error] ${error.toLogString()}")
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = UiErrorState.None) }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() = runBlocking {
    println("=== Error Handling Pattern - API 클라이언트 ===\n")

    val repository = UserRepository()

    // --- 시나리오 1: 성공 ---
    println("--- 1. 성공 케이스 ---")
    val successResult = repository.getUser("user123")
    successResult
        .onSuccess { println("  사용자: ${it.name} (${it.email})") }
        .onFailure { println("  에러: ${it.toUserMessage()}") }

    // --- 시나리오 2: 재시도 후 성공 ---
    println("\n--- 2. 재시도 후 성공 ---")
    repository.getUser("retry-user")
        .onSuccess { println("  사용자: ${it.name}") }
        .onFailure { println("  최종 에러: ${it.toUserMessage()}") }

    // --- 시나리오 3: 유효성 검사 실패 ---
    println("\n--- 3. 유효성 검사 실패 ---")
    val validationResult = repository.createUser("", "invalid-email", "short")
    when (validationResult) {
        is DomainResult.Success -> println("  생성됨: ${validationResult.data}")
        is DomainResult.Failure -> {
            val error = validationResult.error
            println("  에러 타입: ${error::class.simpleName}")
            println("  사용자 메시지: ${error.toUserMessage()}")
        }
    }

    // --- 시나리오 4: 비즈니스 에러 ---
    println("\n--- 4. 비즈니스 에러 (Not Found) ---")
    repository.getUser("notfound")
        .onFailure { error ->
            println("  에러 타입: ${error::class.simpleName}")
            println("  에러 코드: ${error.errorCode}")
            println("  사용자 메시지: ${error.toUserMessage()}")
        }

    // --- 시나리오 5: UI 에러 매핑 ---
    println("\n--- 5. UI 에러 상태 ---")
    listOf(
        NetworkError.NoConnection(),
        NetworkError.Timeout(30000),
        AuthError.TokenExpired,
        ValidationError.Required("email"),
        BusinessError.InsufficientBalance(10000, 5000)
    ).forEach { error ->
        val uiError = UiErrorMapper.map(error) { println("재시도!") }
        println("  ${error::class.simpleName} → ${uiError::class.simpleName}")
        when (uiError) {
            is UiErrorState.Snackbar -> println("    Snackbar: ${uiError.message}")
            is UiErrorState.Dialog -> println("    Dialog: ${uiError.title} - ${uiError.message}")
            is UiErrorState.FullScreen -> println("    FullScreen: ${uiError.title}")
            is UiErrorState.InlineField -> println("    InlineField [${uiError.field}]: ${uiError.message}")
            is UiErrorState.None -> println("    None")
        }
    }

    // --- 시나리오 6: Result 체이닝 ---
    println("\n--- 6. Result 체이닝 ---")
    repository.getUser("user456")
        .map { it.email.uppercase() }
        .flatMap { email ->
            if (email.contains("EXAMPLE")) DomainResult.success("유효한 이메일: $email")
            else DomainResult.failure(ValidationError.InvalidFormat("email", "example.com 도메인 필요"))
        }
        .onSuccess { println("  체이닝 결과: $it") }
        .onFailure { println("  체이닝 에러: ${it.message}") }

    // --- 시나리오 7: ViewModel 통합 ---
    println("\n--- 7. ViewModel 통합 ---")
    val viewModel = UserViewModel()

    launch {
        viewModel.uiState.take(3).collect { state ->
            println("  UI State: loading=${state.isLoading}, user=${state.user?.name}, error=${state.error::class.simpleName}")
        }
    }

    delay(50)
    viewModel.loadUser("user789")
    delay(500)

    println("\n=== Error Handling 핵심 원칙 ===")
    println("1. sealed class: 타입 안전한 에러 계층 정의")
    println("2. Result 타입: 성공/실패를 명시적으로 표현")
    println("3. 에러 변환: 인프라 에러를 도메인 에러로 변환 (계층 분리)")
    println("4. 재시도 정책: isRetryable로 재시도 가능 여부 판단")
    println("5. 폴백 전략: 캐시 폴백, 기본값 등 복구 전략")
    println("6. UI 매핑: 에러 종류에 따라 적절한 UI 표시")
    println("7. 유효성 검사: 빌더 패턴으로 다중 검증")
}
