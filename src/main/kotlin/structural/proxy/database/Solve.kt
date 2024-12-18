package structural.proxy.database

import java.time.LocalDateTime

class Solve {
    interface Database {
        fun getData(userRole: String): String
    }

    // 실제 데이터베이스 클래스
    class ConcreteDatabase: Database {
        private val sensitiveData = mutableMapOf(
            "admin" to "Confidential Admin Information",
            "user" to "Regular User Data",
            "guest" to "Limited Access Information"
        )

        override fun getData(userRole: String): String {
            // 실제 데이터 접근 로직
            return sensitiveData[userRole] ?: "No Data"
        }
    }

    // 프록시 클래스
    class DatabaseProxy(private val realDatabase: Database) : Database {
        private val userPermissions = mapOf(
            "admin" to setOf("admin", "user", "guest"),
            "user" to setOf("user", "guest"),
            "guest" to setOf("guest")
        )

        private var accessLog = mutableListOf<String>()

        override fun getData(userRole: String): String {
            // 접근 권한 확인
            if (!isAuthorized(userRole)) {
                return "Access Denied"
            }

            // 로깅
            logAccess(userRole)

            // 성능 최적화를 위한 캐싱
            return cachedData(userRole)
        }

        private fun isAuthorized(requestedRole: String): Boolean {
            val currentUser = "user"
            return userPermissions[currentUser]?.contains(requestedRole) ?: false
        }

        private fun logAccess(userRole: String) {
            val logEntry = "Access attempt: $userRole at ${LocalDateTime.now()}"
            accessLog.add(logEntry)
            println(logEntry)
        }

        private fun cachedData(userRole: String): String {
            // 간단한 캐싱 매커니즘 (실제 구현에서는 더 복잡할 수 있음)
            println("Retrieving data for $userRole")
            return realDatabase.getData(userRole)
        }

        fun printAccessLog() {
            println("Access Log:")
            accessLog.forEach { println(it) }
        }
    }
}

fun main() {
    val realDatabase = Solve.ConcreteDatabase()

    // 프록시 생성
    val databaseProxy = Solve.DatabaseProxy(realDatabase)

    // 다양한 접근 시도
    println("Guest 접근 시도:")
    println(databaseProxy.getData("guest"))

    println("\nUser 접근 시도:")
    println(databaseProxy.getData("user"))

    println("\nAdmin 접근 시도:")
    println(databaseProxy.getData("admin"))

    // 접근 로그 출력
    databaseProxy.printAccessLog()
}