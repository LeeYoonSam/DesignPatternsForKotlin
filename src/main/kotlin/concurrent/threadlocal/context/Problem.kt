package concurrent.threadlocal.context

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Problem {
    // 웹 서비스 클래스
    class WebService {
        // 사용자의 주문 내역을 가져오는 메서드
        fun getUserOrders(userId: Long): List<String> {
            println("사용자 주문 내역 조회: $userId")
            return listOf("주문-1", "주문-2", "주문-3")
        }

        // 사용자의 프로필 정보를 가져오는 메서드
        fun getUserProfile(userId: Long): Map<String, String> {
            println("사용자 프로필 조회: $userId")
            return mapOf(
                "이름" to "사용자-$userId",
                "이메일" to "user$userId@example.com"
            )
        }

        // 사용자의 권한을 확인하는 메서드
        fun checkUserPermission(userId: Long, resource: String): Boolean {
            println("사용자 권한 확인: $userId, 리소스: $resource")
            return true
        }
    }

    // 로깅 서비스
    class LoggingService {
        fun logAction(userId: Long, action: String, details: String) {
            println("[로그] 사용자: $userId, 작업: $action, 세부정보: $details")
        }
    }

    // 사용자 대시보드 페이지 처리
    class UserDashboardController(
        private val webService: WebService,
        private val loggingService: LoggingService
    ) {
        // 대시보드 데이터를 가져오는 메서드
        fun getDashboardData(userId: Long, username: String, role: String): Map<String, Any> {
            // 1. 사용자 권한 확인
            if (!webService.checkUserPermission(userId, "dashboard")) {
                loggingService.logAction(userId, "대시보드 접근 거부", "권한 없음")
                throw IllegalAccessException("대시보드에 접근할 권한이 없습니다.")
            }

            // 2. 사용자 주문 내역 조회
            val orders = webService.getUserOrders(userId)
            loggingService.logAction(userId, "주문 내역 조회", "항목 수: ${orders.size}")

            // 3. 사용자 프로필 조회
            val profile = webService.getUserProfile(userId)
            loggingService.logAction(userId, "프로필 조회", "필드 수: ${profile.size}")

            // 4. 결과 데이터 구성
            return mapOf(
                "사용자" to mapOf(
                    "id" to userId,
                    "이름" to username,
                    "역할" to role
                ),
                "주문내역" to orders,
                "프로필" to profile
            )
        }

        // 주문 상세 정보를 가져오는 메서드
        fun getOrderDetails(userId: Long, username: String, role: String, orderId: String): Map<String, Any> {
            // 1. 사용자 권한 확인
            if (!webService.checkUserPermission(userId, "orders")) {
                loggingService.logAction(userId, "주문 상세 접근 거부", "권한 없음")
                throw IllegalAccessException("주문 정보에 접근할 권한이 없습니다.")
            }

            // 2. 로그 기록
            loggingService.logAction(userId, "주문 상세 조회", "주문 ID: $orderId")

            // 3. 결과 데이터 구성
            return mapOf(
                "orderId" to orderId,
                "userId" to userId,
                "products" to listOf("상품-1", "상품-2"),
                "total" to 15000
            )
        }
    }
}

fun main() {
    println("=== 문제 상황 시연 ===")

    val webService = Problem.WebService()
    val loggingService = Problem.LoggingService()
    val dashboardController = Problem.UserDashboardController(webService, loggingService)

    // 멀티스레드 환경 시뮬레이션
    val executor = Executors.newFixedThreadPool(3)

    repeat(5) { requestNumber ->
        executor.submit {
            try {
                // 각 요청마다 다른 사용자 정보 (동시에 여러 사용자가 접속하는 상황)
                val userId = Random.nextLong(1000, 9999)
                val username = "사용자-$userId"
                val role = if (Random.nextBoolean()) "ADMIN" else "USER"

                println("\n=== 요청 #$requestNumber (사용자: $userId, 이름: $username, 역할: $role) ===")

                // 대시보드 데이터 요청
                val dashboardData = dashboardController.getDashboardData(userId, username, role)
                println("대시보드 데이터: ${dashboardData.keys}")

                // 주문 상세 정보 요청
                val orderDetails = dashboardController.getOrderDetails(userId, username, role, "주문-1")
                println("주문 상세: ${orderDetails["orderId"]}")

            } catch (e: Exception) {
                println("오류 발생: ${e.message}")
            }
        }
    }

    // 모든 작업이 완료될 때까지 대기
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)

    println("\n=== 문제점 ===")
    println("1. 모든 메서드 호출에 사용자 정보(userId, username, role)를 매번 전달해야 함")
    println("2. 사용자 정보가 서비스 계층까지 전파되지 않아 매번 새로 전달해야 함")
    println("3. 사용자 컨텍스트를 확장하면 모든 메서드 시그니처를 수정해야 함")
    println("4. 로깅할 때마다 사용자 ID를 개별적으로 전달해야 함")
    println("5. 멀티스레드 환경에서 전역 변수로 사용자 정보를 관리하면 스레드 간 간섭이 발생함")
}