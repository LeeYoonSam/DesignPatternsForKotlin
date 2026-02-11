package architecture.errorhandling.api

import java.io.IOException

/**
 * Error Handling Pattern - 문제 상황
 *
 * API 클라이언트 앱을 개발하고 있습니다.
 * 네트워크 요청, 데이터 파싱, 비즈니스 로직 등에서 다양한 에러가 발생하는데,
 * 체계적인 에러 처리 전략 없이 구현하면 여러 문제가 발생합니다.
 */

// ============================================================
// ❌ 문제 1: Exception을 무시하거나 삼키는 패턴
// ============================================================

class SwallowingExceptionProblem {
    fun fetchUserData(userId: String): String? {
        return try {
            // 네트워크 요청 시뮬레이션
            if (userId.isEmpty()) throw IllegalArgumentException("userId가 비어있음")
            if (Math.random() > 0.5) throw IOException("네트워크 오류")
            """{"id": "$userId", "name": "홍길동"}"""
        } catch (e: Exception) {
            // ❌ Exception을 삼키고 null 반환
            // → 왜 실패했는지 알 수 없음
            // → 로그도 없음
            null
        }
    }

    fun demonstrate() {
        println("--- Exception 삼키기 문제 ---")
        println()

        val result = fetchUserData("")
        println("  결과: $result")
        println()
        println("  ❌ 문제점:")
        println("    • 실패 원인을 알 수 없음 (입력 오류? 네트워크 오류?)")
        println("    • 디버깅 불가능")
        println("    • null이 정상인지 에러인지 구분 불가")
        println("    • 에러 복구 전략을 세울 수 없음")
    }
}

// ============================================================
// ❌ 문제 2: 너무 넓은 Exception catch
// ============================================================

class BroadCatchProblem {
    fun processPayment(amount: Int): Boolean {
        return try {
            validateAmount(amount)
            chargeCard(amount)
            sendReceipt()
            true
        } catch (e: Exception) {
            // ❌ 모든 Exception을 같은 방식으로 처리
            println("  결제 실패: ${e.message}")
            false
        }
    }

    private fun validateAmount(amount: Int) {
        if (amount <= 0) throw IllegalArgumentException("금액은 0보다 커야 합니다")
        if (amount > 1_000_000) throw IllegalArgumentException("금액 한도 초과")
    }

    private fun chargeCard(amount: Int) {
        if (Math.random() > 0.7) throw IOException("카드사 통신 오류")
        if (Math.random() > 0.8) throw RuntimeException("잔액 부족")
    }

    private fun sendReceipt() {
        if (Math.random() > 0.9) throw IOException("이메일 발송 실패")
    }

    fun demonstrate() {
        println("--- 넓은 Exception catch 문제 ---")
        println()
        println("  catch (e: Exception)으로 모든 에러 처리:")
        println()
        println("  ❌ 문제점:")
        println("    • 입력 오류 vs 네트워크 오류 vs 비즈니스 오류 구분 불가")
        println("    • 재시도 가능한 에러와 불가능한 에러 구분 불가")
        println("    • 사용자에게 적절한 메시지를 줄 수 없음")
        println("    • OutOfMemoryError 같은 치명적 오류도 잡힘")
    }
}

// ============================================================
// ❌ 문제 3: Exception을 문자열로 전파
// ============================================================

class StringErrorProblem {
    fun login(email: String, password: String): Pair<Boolean, String> {
        if (email.isEmpty()) {
            return Pair(false, "이메일을 입력하세요")
        }
        if (!email.contains("@")) {
            return Pair(false, "올바른 이메일 형식이 아닙니다")
        }
        if (password.length < 8) {
            return Pair(false, "비밀번호는 8자 이상이어야 합니다")
        }
        if (Math.random() > 0.5) {
            return Pair(false, "네트워크 오류가 발생했습니다")
        }
        if (Math.random() > 0.5) {
            return Pair(false, "이메일 또는 비밀번호가 일치하지 않습니다")
        }
        return Pair(true, "성공")
    }

    fun demonstrate() {
        println("--- 문자열 에러 전파 문제 ---")
        println()
        println("  return Pair(false, \"에러 메시지\")")
        println()
        println("  ❌ 문제점:")
        println("    • 에러 타입을 프로그래밍적으로 구분할 수 없음")
        println("    • 문자열 비교로 분기해야 함 (취약)")
        println("    • 다국어 지원 어려움")
        println("    • IDE 자동완성/리팩토링 불가")
        println("    • 에러 계층 구조 표현 불가")

        // 문자열로 에러 분기하는 나쁜 예
        val (success, message) = login("test", "short")
        if (!success) {
            when {
                message.contains("네트워크") -> println("  → 재시도 필요")
                message.contains("이메일") -> println("  → 입력 검증")
                else -> println("  → 기타 에러")
            }
        }
    }
}

// ============================================================
// ❌ 문제 4: Nullable 남용
// ============================================================

class NullableAbuseProblem {
    data class User(val id: String, val name: String, val email: String?)

    fun getUser(id: String): User? {
        // 실패 시 null 반환
        // → 왜 null인지 알 수 없음
        return if (Math.random() > 0.5) {
            User(id, "홍길동", "hong@example.com")
        } else {
            null  // 사용자 없음? 네트워크 오류? 권한 없음?
        }
    }

    fun getUserEmail(userId: String): String? {
        val user = getUser(userId) ?: return null
        return user.email  // null일 수도 있음 (이메일 미등록)
    }

    fun demonstrate() {
        println("--- Nullable 남용 문제 ---")
        println()
        println("  fun getUser(id: String): User?")
        println("  fun getUserEmail(userId: String): String?")
        println()
        println("  ❌ 문제점:")
        println("    • null이 정상 값인지 에러인지 구분 불가")
        println("    • 체인된 nullable 호출은 에러 추적 불가")
        println("    • 호출자가 null 체크를 빠뜨리기 쉬움")
        println("    • '사용자 없음'과 '조회 실패'가 같은 null")
    }
}

// ============================================================
// ❌ 문제 5: 에러 코드 사용
// ============================================================

class ErrorCodeProblem {
    companion object {
        const val SUCCESS = 0
        const val ERROR_NETWORK = 1
        const val ERROR_INVALID_INPUT = 2
        const val ERROR_NOT_FOUND = 3
        const val ERROR_UNAUTHORIZED = 4
        const val ERROR_UNKNOWN = 99
    }

    fun fetchData(id: String): Pair<Int, String?> {
        if (id.isEmpty()) return Pair(ERROR_INVALID_INPUT, null)
        if (Math.random() > 0.7) return Pair(ERROR_NETWORK, null)
        if (Math.random() > 0.8) return Pair(ERROR_NOT_FOUND, null)
        return Pair(SUCCESS, """{"data": "value"}""")
    }

    fun demonstrate() {
        println("--- 에러 코드 문제 ---")
        println()
        println("  return Pair(ERROR_CODE, data)")
        println()
        println("  ❌ 문제점:")
        println("    • 타입 안전성 없음 (Int 값 아무거나 가능)")
        println("    • 에러 코드 목록 관리 어려움")
        println("    • 에러에 추가 정보 포함 어려움")
        println("    • C 스타일 - 코틀린답지 않음")
        println("    • 컴파일러가 모든 케이스 처리를 강제하지 않음")

        val (code, data) = fetchData("123")
        // 새 에러 코드 추가 시 이 when을 찾아서 수정해야 함
        when (code) {
            SUCCESS -> println("  성공: $data")
            ERROR_NETWORK -> println("  네트워크 오류")
            ERROR_INVALID_INPUT -> println("  입력 오류")
            // ERROR_NOT_FOUND, ERROR_UNAUTHORIZED 처리 누락!
            else -> println("  알 수 없는 오류")
        }
    }
}

// ============================================================
// ❌ 문제 6: 일관성 없는 에러 처리
// ============================================================

class InconsistentErrorHandling {
    // 어떤 함수는 Exception
    fun method1(): String {
        throw IOException("오류")
    }

    // 어떤 함수는 null
    fun method2(): String? {
        return null
    }

    // 어떤 함수는 에러 코드
    fun method3(): Pair<Int, String?> {
        return Pair(-1, null)
    }

    // 어떤 함수는 Result
    fun method4(): Result<String> {
        return Result.failure(Exception("오류"))
    }

    // 어떤 함수는 sealed class
    sealed class Method5Result {
        data class Success(val data: String) : Method5Result()
        data class Error(val message: String) : Method5Result()
    }
    fun method5(): Method5Result = Method5Result.Error("오류")

    fun demonstrate() {
        println("--- 일관성 없는 에러 처리 ---")
        println()
        println("  method1(): throws Exception")
        println("  method2(): String? (nullable)")
        println("  method3(): Pair<Int, String?> (에러 코드)")
        println("  method4(): Result<String>")
        println("  method5(): sealed class")
        println()
        println("  ❌ 문제점:")
        println("    • 팀 내 혼란")
        println("    • 에러 처리 코드가 일관성 없음")
        println("    • 새 개발자가 어떤 방식을 써야 할지 모름")
        println("    • 에러 변환 로직 중복")
    }
}

// ============================================================
// ❌ 문제 7: 에러 복구 전략 없음
// ============================================================

class NoRecoveryStrategyProblem {
    fun fetchWithoutRetry(url: String): String? {
        return try {
            if (Math.random() > 0.3) throw IOException("일시적 네트워크 오류")
            "data"
        } catch (e: IOException) {
            // 재시도 없이 바로 실패
            null
        }
    }

    fun demonstrate() {
        println("--- 에러 복구 전략 없음 ---")
        println()
        println("  일시적 오류에도 바로 실패 처리:")
        repeat(5) { i ->
            val result = fetchWithoutRetry("https://api.example.com")
            println("  시도 ${i + 1}: ${if (result != null) "성공" else "실패"}")
        }
        println()
        println("  ❌ 문제점:")
        println("    • 일시적 오류에 재시도하면 성공할 수 있는데 바로 실패")
        println("    • 폴백 전략 없음")
        println("    • 부분 실패 처리 없음")
        println("    • Circuit Breaker 없음")
    }
}

// ============================================================
// ❌ 문제 8: UI 레이어까지 Exception 전파
// ============================================================

class ExceptionPropagationProblem {
    // Repository
    fun fetchUser(id: String): String {
        if (Math.random() > 0.5) throw IOException("네트워크 오류")
        return """{"id": "$id"}"""
    }

    // UseCase
    fun getUserProfile(id: String): String {
        // Exception 그대로 전파
        return fetchUser(id)
    }

    // ViewModel
    fun loadProfile(id: String) {
        try {
            val profile = getUserProfile(id)
            println("  프로필 로드: $profile")
        } catch (e: IOException) {
            // UI 레이어에서 인프라 Exception 처리
            println("  에러: ${e.message}")
        }
    }

    fun demonstrate() {
        println("--- Exception 전파 문제 ---")
        println()
        println("  Repository → UseCase → ViewModel")
        println("  IOException이 UI 레이어까지 전파")
        println()
        println("  ❌ 문제점:")
        println("    • UI가 인프라 세부사항(IOException)에 의존")
        println("    • 계층 간 결합도 증가")
        println("    • Repository 구현 변경 시 UI도 변경 필요")
        println("    • 테스트 어려움")
    }
}

fun main() {
    println("=== Error Handling Pattern - 문제 상황 ===\n")

    SwallowingExceptionProblem().demonstrate()
    println()

    BroadCatchProblem().demonstrate()
    println()

    StringErrorProblem().demonstrate()
    println()

    NullableAbuseProblem().demonstrate()
    println()

    ErrorCodeProblem().demonstrate()
    println()

    InconsistentErrorHandling().demonstrate()
    println()

    NoRecoveryStrategyProblem().demonstrate()
    println()

    ExceptionPropagationProblem().demonstrate()

    println("\n핵심 문제:")
    println("• Exception을 삼키거나 무시하면 디버깅 불가")
    println("• 넓은 catch는 에러 구분을 불가능하게 함")
    println("• 문자열/에러코드/null은 타입 안전성 없음")
    println("• 일관성 없는 에러 처리는 팀 혼란 유발")
    println("• 복구 전략 없이 바로 실패하면 UX 저하")
    println("• 인프라 Exception이 UI까지 전파되면 결합도 증가")
}
