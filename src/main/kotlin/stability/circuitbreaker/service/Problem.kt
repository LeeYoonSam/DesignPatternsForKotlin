package stability.circuitbreaker.service

/**
 * 문제점
 * - 연속적인 실패로 인한 시스템 부하 증가
 * - 원격 서비스 장애 전파
 * - 불필요한 리소스 낭비
 * - 응답 지연으로 인한 사용자 경험 저하
 * - 시스템 복구 시점 파악의 어려움
 */
class Problem {
    // 불안정한 외부 서비스
    class UnstableRemoteService {
        private var failureCount = 0

        fun call(): String {
            if (failureCount++ % 3 == 0) {
                throw ServiceException("Service temporarily unavailable")
            }
            return "Service Response"
        }
    }

    // 서비스 호출자
    class ServiceCaller(private val service: UnstableRemoteService) {
        fun executeCall(): String {
            return try {
                service.call()
            } catch (e: ServiceException) {
                "Failed: ${e.message}" // 계속해서 재시도
            }
        }
    }

    class ServiceException(message: String) : Exception(message)
}

fun main() {
    val unstableService = Problem.UnstableRemoteService()

    // 서킷 브레이커 없는 호출
    println("Without Circuit Breaker:")
    val simpleCaller = Problem.ServiceCaller(unstableService)
    repeat(6) {
        println("Call ${it + 1}: ${simpleCaller.executeCall()}")
    }
}