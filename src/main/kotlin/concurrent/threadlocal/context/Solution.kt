package concurrent.threadlocal.context

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Solution {
    // 사용자 컨텍스트 홀더 클래스
    object UserContextHolder {
        private val userContextThreadLocal = ThreadLocal<UserContext>()

        fun setContext(context: UserContext) {
            userContextThreadLocal.set(context)
        }

        fun getContext(): UserContext {
            return userContextThreadLocal.get() ?: throw IllegalStateException("UserContext가 설정되지 않았습니다.")
        }

        fun clearContext() {
            userContextThreadLocal.remove()
        }
    }

    // 요청 ID 관리를 위한 컨텍스트 홀더
    object RequestContextHolder {
        private val requestIdThreadLocal = ThreadLocal<String>()

        fun setRequestId(requestId: String) {
            requestIdThreadLocal.set(requestId)
        }

        fun getRequestId(): String {
            return requestIdThreadLocal.get() ?: "unknown"
        }

        fun clearRequestId() {
            requestIdThreadLocal.remove()
        }
    }

    // 웹 서비스 클래스 - ThreadLocal 활용
    class WebServiceWithThreadLocal {
        // 사용자의 주문 내역을 가져오는 메서드
        fun getUserOrders(): List<String> {
            val userId = UserContextHolder.getContext().userId
            println("사용자 주문 내역 조회: $userId")
            return listOf("주문-1, 주문-2, 주문-3")
        }

        // 사용자의 프로필 정보를 가져오는 메서드
        fun getUserProfile(): Map<String, String> {
            val userId = UserContextHolder.getContext().userId
            println("사용자 프로필 조회: $userId")
            return mapOf(
                "이름" to "사용자-$userId",
                "이메일" to "user$userId@example.com"
            )
        }

        // 사용자 권한을 확인하는 메서드
        fun checkUserPermission(resource: String): Boolean {
            val context = UserContextHolder.getContext()
            println("사용자 권한 확인: ${context.userId}, 리소스: $resource")
            return true
        }
    }

    // 로깅 서비스 = ThreadLocal 활용
    class LoggingServiceWithThreadLocal {
        fun logAction(action: String, details: String) {
            val context = UserContextHolder.getContext()
            val requestId = RequestContextHolder.getRequestId()
            println("[로그] 요청ID: $requestId, 사용자: ${context.userId}, 작업: $action, 세부정보: $details")
        }
    }

    // 컨텍스트 관리를 자동화하는 인터셉터
    class ContextInterceptor {
        fun <T> withContext(userId: Long, username: String, role: String, requestId: String, block: () -> T): T {
            try {
                // 컨텍스트 설정
                UserContextHolder.setContext(UserContext(userId, username, role))
                RequestContextHolder.setRequestId(requestId)

                // 실제 작업 실행
                return block()
            } finally {
                // 컨텍스트 정리
                UserContextHolder.clearContext()
                RequestContextHolder.clearRequestId()
            }
        }
    }

    // 사용자 대시보드 페이지 처리 - ThreadLocal 활용
    class UserDashboardControllerWithThreadLocal(
        private val webService: WebServiceWithThreadLocal,
        private val loggingService: LoggingServiceWithThreadLocal
    ) {
        // 대시보드 데이터를 가져오는 메서드
        fun getDashboardData(): Map<String, Any> {
            // 1. 사용자 권한 확인
            if (!webService.checkUserPermission("dashboard")) {
                loggingService.logAction("대시보드 접근 거부", "권한 없음")
                throw IllegalAccessException("대시보드에 접근할 권한이 없습니다.")
            }

            // 2. 사용자 주문 내역 조회
            val orders = webService.getUserOrders()
            loggingService.logAction("주문 내역 조회", "항목 수: ${orders.size}")

            // 3. 사용자 프로필 조회
            val profile = webService.getUserProfile()
            loggingService.logAction("프로필 조회", "필드 수: ${profile.size}")

            // 4. 결과 데이터 구성
            val context = UserContextHolder.getContext()
            return mapOf(
                "사용자" to mapOf(
                    "id" to context.userId,
                    "이름" to context.username,
                    "역할" to context.role
                ),
                "주문내역" to orders,
                "프로필" to profile
            )
        }

        // 주문 상세 정보를 가져오는 메서드
        fun getOrderDetails(orderId: String): Map<String, Any> {
            // 1. 사용자 권한 확인
            if (!webService.checkUserPermission("orders")) {
                loggingService.logAction("주문 상세 접근 거부", "권한 없음")
                throw IllegalAccessException("주문 정보에 접근할 권한이 없습니다.")
            }

            // 2. 로그 기록
            loggingService.logAction("주문 상세 조회", "주문 ID: $orderId")

            // 3. 결과 데이터 구성
            val context = UserContextHolder.getContext()
            return mapOf(
                "orderId" to orderId,
                "userId" to context.userId,
                "products" to listOf("상품-1", "상품-2"),
                "total" to 15000
            )
        }
    }
}

fun main() {
    println("=== 스레드로컬 스토리지 패턴 해결책 ===")

    val webService = Solution.WebServiceWithThreadLocal()
    val loggingService = Solution.LoggingServiceWithThreadLocal()
    val dashboardController = Solution.UserDashboardControllerWithThreadLocal(webService, loggingService)
    val contextInterceptor = Solution.ContextInterceptor()

    // 멀티스레드 환경 시뮬레이션
    val executor = Executors.newFixedThreadPool(3)

    repeat(5) { requestNumber ->
        // executor.submit을 통해 실행되는 각 작업은 자신만의 스레드에서 실행되며, ThreadLocal은 각 스레드별로 독립적인 저장 공간을 제공
        executor.submit {
            try {
                // 각 요청마다 다른 사용자 정보 (동시에 여러 사용자가 접속하는 상황)
                val userId = Random.nextLong(1000, 9999)
                val username = "사용자-$userId"
                val role = if (Random.nextBoolean()) "ADMIN" else "USER"
                val requestId = "REQ-${System.currentTimeMillis()}-$requestNumber"

                println("\n=== 요청 #$requestNumber (사용자: $userId, 이름: $username, 역할: $role, 요청ID: $requestId) ===")

                // 컨텍스트 인터셉터를 사용하여 ThreadLocal 설정 및 정리 자동화
                contextInterceptor.withContext(userId, username, role, requestId) {
                    // 대시보드 데이터 요청 - 사용자 정보를 매개변수로 전달하지 않음
                    val dashboardData = dashboardController.getDashboardData()
                    println("대시보드 데이터: ${dashboardData.keys}")

                    // 주문 상세 정보 요청 - 사용자 정보를 매개변수로 전달하지 않음
                    val orderDetails = dashboardController.getOrderDetails("주문-1")
                    println("주문 상세: ${orderDetails["orderId"]}")
                }

            } catch (e: Exception) {
                println("오류 발생: ${e.message}")
            }
        }
    }

    // 모든 작업이 완료될 때까지 대기
    executor.shutdown()
    executor.awaitTermination(10, TimeUnit.SECONDS)

    println("\n=== 장점 ===")
    println("1. 메서드 호출 시 사용자 정보를 명시적으로 전달할 필요가 없음")
    println("2. 컨텍스트 정보가 애플리케이션 전체에 암묵적으로 전파됨")
    println("3. 컨텍스트 확장 시 메서드 시그니처를 변경할 필요가 없음")
    println("4. 각 스레드가 독립적인 컨텍스트를 가지므로 스레드 간 간섭이 발생하지 않음")
    println("5. 인터셉터 패턴을 함께 사용하여 컨텍스트 설정/정리를 자동화할 수 있음")

    println("\n=== 주의사항 ===")
    println("1. ThreadLocal 사용 후 반드시 정리(remove)해야 메모리 누수를 방지할 수 있음")
    println("2. 스레드 풀 환경에서는 스레드가 재사용되므로 컨텍스트 정리가 더욱 중요함")
    println("3. 코드의 명시성이 다소 감소할 수 있으므로 문서화가 중요함")
}