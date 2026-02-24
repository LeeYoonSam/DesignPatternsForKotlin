package architecture.structuredlogging.logging

/**
 * Structured Logging Pattern - 문제점
 *
 * 비구조적 로깅으로 인해 발생하는 문제들:
 * 1. 비정형 텍스트: 파싱/검색/분석 어려움
 * 2. 컨텍스트 누락: 요청 ID, 사용자 정보 없음
 * 3. 일관성 없음: 개발자마다 다른 포맷
 * 4. 민감 정보 노출: 비밀번호, 카드번호 로그 출력
 * 5. 로그 레벨 무시: 모든 것을 INFO로 출력
 * 6. 성능 문제: 불필요한 문자열 생성
 * 7. 추적 불가: 분산 시스템에서 요청 흐름 추적 불가
 * 8. 로그 집계 어려움: ELK, Datadog 등 도구 활용 불가
 */

// ============================================================
// 문제 1: 비정형 텍스트 로그
// ============================================================

class UnstructuredLogProblem {
    // 개발자마다 다른 형식
    fun developerA() {
        println("2024-01-15 10:30:00 - User login: john@example.com")
        println("2024-01-15 10:30:01 - Payment processed: $99.99 for order #1234")
        println("2024-01-15 10:30:02 ERROR - Database connection failed!")
    }

    fun developerB() {
        println("[INFO] user john logged in at 10:30")
        println("[WARN] Payment: 99.99 USD, Order: 1234")
        println("*** ERROR *** DB down!!!")
    }

    fun developerC() {
        println("Login OK -> john@example.com")
        println("PAY OK 99.99")
        println("!!! DB ERROR !!!")
    }

    // 이 로그들에서 "지난 1시간 동안 실패한 결제" 를 어떻게 찾을까?
    // grep? 정규식? → 불가능에 가까움
}

// ============================================================
// 문제 2: 컨텍스트 누락
// ============================================================

class NoContextProblem {
    // 누가, 어디서, 왜?
    fun processOrder() {
        println("Processing order...")           // 어떤 주문?
        println("Payment successful")            // 얼마? 어떤 수단?
        println("Sending confirmation email")    // 누구에게?
        println("Order completed")               // 걸린 시간?
    }

    // 에러 발생 시
    fun handleError() {
        println("Error occurred")                // 무슨 에러? 어디서?
        println("Retrying...")                   // 몇 번째? 최대 몇 번?
        println("Failed after retries")          // 최종 에러 원인?
    }

    // 분산 시스템에서
    fun microserviceA() {
        println("Calling service B")             // 어떤 요청? traceId?
    }

    fun microserviceB() {
        println("Received request")              // 어디서 온 요청?
        println("Processing...")                 // 어떤 데이터?
    }
    // 두 로그를 연결할 방법이 없음!
}

// ============================================================
// 문제 3: 민감 정보 노출
// ============================================================

class SensitiveInfoInLogProblem {
    fun loginUser(email: String, password: String) {
        // 비밀번호가 로그에!
        println("Login attempt: email=$email, password=$password")
    }

    fun processPayment(cardNumber: String, amount: Double) {
        // 카드 번호가 로그에!
        println("Payment: card=$cardNumber, amount=$amount")
    }

    fun storeSession(userId: String, token: String) {
        // 세션 토큰이 로그에!
        println("Session created: userId=$userId, token=$token")
    }

    data class User(val name: String, val email: String, val ssn: String)

    fun logUser(user: User) {
        // 주민번호가 로그에!
        println("User: $user")  // toString()이 모든 필드 출력
    }
}

// ============================================================
// 문제 4: 로그 레벨 무시
// ============================================================

class WrongLogLevelProblem {
    // 모든 것이 INFO
    fun doEverything() {
        println("[INFO] Application starting...")
        println("[INFO] Database connection failed")      // ERROR여야 함!
        println("[INFO] User clicked button")             // DEBUG여야 함!
        println("[INFO] SQL: SELECT * FROM users")        // TRACE여야 함!
        println("[INFO] OutOfMemoryError occurred")       // ERROR여야 함!
        println("[INFO] Request took 15000ms")            // WARN이어야 함!
        println("[INFO] Cache miss for key user:123")     // DEBUG여야 함!
    }

    // 반대: 과도한 DEBUG 로그
    fun tooMuchDebug() {
        println("[DEBUG] Entering method processOrder()")
        println("[DEBUG] Parameter: orderId=123")
        println("[DEBUG] Calling repository.findById()")
        println("[DEBUG] Repository returned: Order(id=123, ...)")
        println("[DEBUG] Validating order...")
        println("[DEBUG] Order is valid")
        println("[DEBUG] Calling payment service...")
        // 프로덕션에서 이 로그가 다 출력되면? 디스크/네트워크 폭발!
    }
}

// ============================================================
// 문제 5: 성능 문제
// ============================================================

class LogPerformanceProblem {
    // 비활성 레벨에도 문자열 생성
    fun expensiveLogging() {
        val enabled = false  // DEBUG 비활성

        // 항상 문자열 생성 비용 발생!
        if (enabled) {
            println("[DEBUG] Large object: ${createExpensiveString()}")
        }

        // 이렇게 해도 마찬가지
        val message = "[DEBUG] Data: ${heavyComputation()}"  // 항상 실행!
        if (enabled) println(message)
    }

    private fun createExpensiveString(): String {
        // 큰 컬렉션 toString() - 수천 개 요소
        return (1..10000).toList().toString()
    }

    private fun heavyComputation(): String {
        Thread.sleep(100)  // 무거운 연산
        return "result"
    }
}

// ============================================================
// 문제 6: 로그 검색/분석 어려움
// ============================================================

class LogAnalysisProblem {
    // 이런 로그에서 분석하려면?
    fun generateLogs() {
        println("2024-01-15 10:30:00 User john logged in")
        println("2024-01-15 10:30:01 Order 1234 created")
        println("2024-01-15 10:30:02 Payment failed for order 1234")
        println("2024-01-15 10:30:03 User jane logged in")
        println("2024-01-15 10:30:04 Order 1235 created by jane")
    }

    // 질문들:
    // - "오늘 로그인 실패 횟수는?" → 텍스트 파싱 필요
    // - "평균 결제 소요 시간은?" → 로그에 시간 정보 없음
    // - "특정 사용자의 모든 활동은?" → grep으로 찾아야 함
    // - "500 에러의 원인 분포는?" → 에러 코드 구조 없음
    // - "P99 응답 시간은?" → 로그에 수치 데이터 없음
}

// ============================================================
// 문제 7: 멀티스레드 로그 혼재
// ============================================================

class ConcurrentLogProblem {
    fun handleConcurrentRequests() {
        // 여러 스레드의 로그가 섞임
        // Thread-1: "Processing order 100"
        // Thread-3: "Payment for order 300"
        // Thread-1: "Payment successful"       ← 어떤 주문?
        // Thread-2: "Processing order 200"
        // Thread-3: "Error: timeout"           ← 어떤 주문에서?
        // Thread-1: "Order completed"

        // 어떤 로그가 어떤 요청에 속하는지 알 수 없음
    }
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== 비구조적 로깅 문제점 ===\n")

    println("1. 비정형 텍스트")
    val unstructured = UnstructuredLogProblem()
    unstructured.developerA()
    println("---")
    unstructured.developerB()

    println("\n2. 컨텍스트 누락")
    NoContextProblem().processOrder()

    println("\n3. 민감 정보 노출")
    SensitiveInfoInLogProblem().loginUser("john@test.com", "password123")
    SensitiveInfoInLogProblem().processPayment("4111-2222-3333-4444", 99.99)

    println("\n4. 로그 레벨 무시")
    WrongLogLevelProblem().doEverything()

    println("\n5. 성능 문제: 비활성 레벨에도 문자열 생성 비용 발생")

    println("\n6. 로그 분석 어려움: 텍스트 파싱으로만 가능")

    println("\n7. 멀티스레드 로그 혼재: 요청 추적 불가")

    println("\n→ 해결책: Structured Logging Pattern으로 구조화된 로깅!")
}
