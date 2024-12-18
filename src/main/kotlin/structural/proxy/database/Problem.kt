package structural.proxy.database

/**
 * 문제 코드: 보안 및 성능 이슈가 있는 데이터 접근 시스템
 *
 * 문제점
 * - 직접적인 객체 접근으로 인한 보안 취약성
 * - 리소스 집약적인 객체에 대한 비효율적인 접근
 * - 성능 최적화 및 로깅의 어려움
 * - 객체 접근에 대한 세분화된 제어 부족
 */
class Problem {
    class RealDatabase {
        private val sensitiveData = mutableMapOf(
            "admin" to "Confidential Admin Information",
            "user" to "Regular User Data",
            "guest" to "Limited Access Information"
        )

        fun getData(userRole: String): String {
            // 보안 검증 없이 모든 데이터에 직접 접근 가능
            return sensitiveData[userRole] ?: "No Data"
        }
    }
}

fun main() {
    val database = Problem.RealDatabase()
    println(database.getData("admin"))
    println(database.getData("user"))
    println(database.getData("guest"))
}