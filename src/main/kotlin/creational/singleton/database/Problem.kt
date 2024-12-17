package creational.singleton.database

/**
 * 문제 코드: 다중 인스턴스로 인한 비효율적인 리소스 관리
 *
 * 문제점
 * - 전역 상태 관리의 복잡성
 * - 다중 스레드 환경에서의 동시성 문제
 * - 과도한 전역 상태로 인한 결합도 증가
 * - 테스트의 어려움
 * - 불필요한 메모리 점유
 */
class Problem {
    class DatabaseConnectionManager {
        private var connectionCount = 0

        fun connectToDatabase() {
            // 매번 새로운 연결을 생성하는 비효율적인 방식
            connectionCount++
            println("Creating new database connection. Total connections: $connectionCount")
        }
    }

    class ApplicationConfig {
        private var configs: MutableMap<String, String> = mutableMapOf()

        fun setConfig(key: String, value: String) {
            // 여러 인스턴스로 인해 설정 동기화 문제 발생
            configs[key] = value
        }

        fun getConfig(key: String): String? {
            return configs[key]
        }
    }
}

fun main() {
    Problem.DatabaseConnectionManager().connectToDatabase()
    Problem.DatabaseConnectionManager().connectToDatabase()

    val config1 = Problem.ApplicationConfig()
    val config2 = Problem.ApplicationConfig()

    config1.setConfig("database.url", "jdbc:mysql://localhost:3306/mydb")
    println(config1.getConfig("database.url"))
    println(config2.getConfig("database.url"))

    // 동일성 비교
    println("Are configs the same instance? ${config1 === config2}")
}