package architecture.hexagonal.payment

/**
 * Hexagonal Architecture (Ports & Adapters) Pattern - Problem
 *
 * 이 파일은 Hexagonal Architecture를 적용하지 않았을 때 발생하는 문제점을 보여줍니다.
 *
 * 문제점:
 * 1. 비즈니스 로직이 인프라스트럭처(DB, 외부 API)에 직접 의존
 * 2. 외부 시스템 변경 시 비즈니스 로직도 수정 필요
 * 3. 테스트가 어려움 (실제 DB, 외부 서비스 필요)
 * 4. 기술적 관심사와 비즈니스 관심사가 혼재
 * 5. 다른 인터페이스(REST, CLI, 메시지 큐) 추가가 어려움
 */

import java.sql.DriverManager
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

// ========================================
// 문제가 있는 구현: 모든 것이 하나로 결합됨
// ========================================

/**
 * 문제 1: 비즈니스 로직이 특정 데이터베이스에 직접 의존
 * - SQL 쿼리가 비즈니스 로직에 포함
 * - 데이터베이스 변경 시 전체 클래스 수정 필요
 */
class PaymentServiceWithDatabase {
    private val dbUrl = "jdbc:mysql://localhost:3306/payments"
    private val dbUser = "root"
    private val dbPassword = "password"

    fun processPayment(orderId: String, amount: Double, cardNumber: String): Boolean {
        // 비즈니스 검증과 DB 접근이 혼재
        if (amount <= 0) {
            throw IllegalArgumentException("결제 금액은 0보다 커야 합니다")
        }

        // DB 연결 코드가 비즈니스 로직에 직접 포함
        val connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)

        try {
            // 주문 조회 - SQL이 비즈니스 로직에 포함
            val orderStmt = connection.prepareStatement(
                "SELECT * FROM orders WHERE id = ? AND status = 'PENDING'"
            )
            orderStmt.setString(1, orderId)
            val orderResult = orderStmt.executeQuery()

            if (!orderResult.next()) {
                return false
            }

            // 결제 처리 - 외부 API 호출이 직접 포함
            val paymentGatewayUrl = "https://payment-gateway.example.com/charge"
            val url = URL(paymentGatewayUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true

            val payload = """{"card": "$cardNumber", "amount": $amount}"""
            conn.outputStream.write(payload.toByteArray())

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                return false
            }

            // 결제 완료 후 DB 업데이트
            val updateStmt = connection.prepareStatement(
                "UPDATE orders SET status = 'PAID', paid_at = ? WHERE id = ?"
            )
            updateStmt.setString(1, LocalDateTime.now().toString())
            updateStmt.setString(2, orderId)
            updateStmt.executeUpdate()

            // 영수증 발송 - 이메일 서비스 직접 호출
            sendEmailDirectly(orderId, amount)

            return true
        } finally {
            connection.close()
        }
    }

    private fun sendEmailDirectly(orderId: String, amount: Double) {
        // 이메일 서비스 직접 연동
        val emailApiUrl = "https://email-service.example.com/send"
        val url = URL(emailApiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true

        val payload = """{"to": "customer@example.com", "subject": "결제 완료", "body": "주문 $orderId, 금액: $amount"}"""
        conn.outputStream.write(payload.toByteArray())
        conn.responseCode
    }
}

/**
 * 문제 2: 컨트롤러가 비즈니스 로직과 강하게 결합
 * - REST API 형식이 비즈니스 로직에 영향
 * - 다른 인터페이스(CLI, 메시지 큐) 추가 시 코드 중복
 */
class PaymentController {
    private val paymentService = PaymentServiceWithDatabase()

    // REST API 형식에 종속된 처리
    fun handlePaymentRequest(requestBody: String): String {
        // JSON 파싱이 컨트롤러에 직접 포함
        val orderId = extractFromJson(requestBody, "orderId")
        val amount = extractFromJson(requestBody, "amount").toDouble()
        val cardNumber = extractFromJson(requestBody, "cardNumber")

        return try {
            val result = paymentService.processPayment(orderId, amount, cardNumber)
            if (result) {
                """{"status": "success", "message": "결제가 완료되었습니다"}"""
            } else {
                """{"status": "failure", "message": "결제에 실패했습니다"}"""
            }
        } catch (e: Exception) {
            """{"status": "error", "message": "${e.message}"}"""
        }
    }

    private fun extractFromJson(json: String, key: String): String {
        // 간단한 JSON 파싱 (실제로는 라이브러리 사용)
        val pattern = """"$key"\s*:\s*"?([^",}]+)"?""".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}

/**
 * 문제 3: 테스트가 매우 어려움
 * - 실제 DB 연결 필요
 * - 실제 외부 API 호출 필요
 * - 테스트 환경 구성이 복잡
 */
class PaymentServiceTest {
    fun testProcessPayment() {
        // 실제 데이터베이스가 필요
        // 실제 결제 게이트웨이 연결 필요
        // 실제 이메일 서비스 연결 필요
        // 테스트 데이터 정리 필요

        val service = PaymentServiceWithDatabase()

        // 이 테스트는 실행이 어렵고 느리며 불안정함
        // val result = service.processPayment("order-1", 10000.0, "4111111111111111")
        // assert(result)

        println("테스트 실행 불가: 실제 인프라가 필요합니다")
    }
}

/**
 * 문제 4: 기술 변경이 어려움
 * - MySQL에서 PostgreSQL로 변경하려면 전체 코드 수정
 * - 결제 게이트웨이 변경 시 비즈니스 로직 수정 필요
 * - 이메일에서 SMS로 변경하려면 코드 전체 수정
 */
class PaymentServiceWithDifferentDB {
    // MySQL에서 PostgreSQL로 변경하려면 새 클래스 작성 필요
    // 비즈니스 로직이 동일한데도 전체 코드가 중복됨
    private val dbUrl = "jdbc:postgresql://localhost:5432/payments"

    fun processPayment(orderId: String, amount: Double, cardNumber: String): Boolean {
        // MySQL 버전과 거의 동일한 코드가 중복
        // 단지 DB 드라이버와 일부 SQL 문법만 다름
        if (amount <= 0) {
            throw IllegalArgumentException("결제 금액은 0보다 커야 합니다")
        }

        val connection = DriverManager.getConnection(dbUrl, "postgres", "password")

        try {
            // PostgreSQL 문법으로 변경된 쿼리들...
            // 비즈니스 로직은 동일하지만 전체 코드 중복
            return true
        } finally {
            connection.close()
        }
    }
}

/**
 * 문제 5: 새로운 인터페이스 추가가 어려움
 * - CLI 인터페이스 추가 시 비즈니스 로직 접근 방식이 달라짐
 * - 코드 중복 또는 강한 결합 발생
 */
class PaymentCLI {
    private val paymentService = PaymentServiceWithDatabase()

    // CLI에서 동일한 비즈니스 로직 호출하지만 형식이 다름
    fun processFromCommandLine(args: Array<String>) {
        if (args.size < 3) {
            println("사용법: payment <orderId> <amount> <cardNumber>")
            return
        }

        val orderId = args[0]
        val amount = args[1].toDouble()
        val cardNumber = args[2]

        // 컨트롤러와 중복된 에러 처리
        try {
            val result = paymentService.processPayment(orderId, amount, cardNumber)
            if (result) {
                println("결제 완료!")
            } else {
                println("결제 실패!")
            }
        } catch (e: Exception) {
            println("오류: ${e.message}")
        }
    }
}

/**
 * 문제점 요약:
 *
 * 1. 비즈니스 로직이 인프라에 직접 의존
 *    - DB 연결, HTTP 호출이 비즈니스 로직에 포함
 *    - 관심사 분리 불가
 *
 * 2. 테스트 불가능
 *    - 실제 인프라 없이 테스트 불가
 *    - 단위 테스트 작성이 어려움
 *
 * 3. 기술 변경 어려움
 *    - DB, 외부 서비스 변경 시 비즈니스 로직 수정 필요
 *    - 전체 코드 중복 발생
 *
 * 4. 확장성 부족
 *    - 새로운 인터페이스(REST, CLI, 메시지) 추가 어려움
 *    - 어댑터 패턴 적용 불가
 *
 * 5. 유지보수 어려움
 *    - 하나의 변경이 여러 곳에 영향
 *    - 코드 이해가 어려움
 *
 * Hexagonal Architecture로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 포트와 어댑터를 사용한 구현을 확인하세요.
 */

fun main() {
    println("=== Hexagonal Architecture 적용 전 문제점 ===")
    println()
    println("1. PaymentServiceWithDatabase 클래스 확인:")
    println("   - 비즈니스 로직 + DB 코드 + HTTP 코드가 모두 혼재")
    println("   - 단일 책임 원칙 위반")
    println()
    println("2. 테스트 실행:")
    val test = PaymentServiceTest()
    test.testProcessPayment()
    println()
    println("3. 기술 변경의 어려움:")
    println("   - MySQL → PostgreSQL 변경 시 전체 클래스 재작성 필요")
    println("   - 비즈니스 로직이 동일한데도 코드 중복")
    println()
    println("4. 다중 인터페이스 지원의 어려움:")
    println("   - REST API와 CLI에서 동일 로직 사용하지만 중복 코드 발생")
    println()
    println("Solution.kt에서 Hexagonal Architecture를 적용한 해결책을 확인하세요.")
}
