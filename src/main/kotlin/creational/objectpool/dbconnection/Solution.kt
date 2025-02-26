package creational.objectpool.dbconnection

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Solution {
    // 데이터베이스 연결을 표현하는 클래스 (재사용 가능)
    class PooledDatabaseConnection(val id: Int) {
        private var inUse = false

        init {
            println("Creating database connection #$id (expensive operation)")
            TimeUnit.MILLISECONDS.sleep(500) // DB 연결 생성은 비용이 큰 작업
        }

        fun executeQuery(query: String): String {
            println("Connection #$id executing: $query")
            TimeUnit.MILLISECONDS.sleep(100)
            return "Result for '$query'"
        }

        fun markAsInUse() {
            inUse = true
        }

        fun markAsAvailable() {
            inUse = false
        }

        fun reset() {
            // 상태 초기화 로직 (트랜잭션 롤백, 임시 테이블 삭제 등)
            println("Resetting connection #$id")
        }
    }

    // Object Pool 패턴 구현
    class DatabaseConnectionPool private constructor() {
        private val maxConnections = 3 // 최대 연결 수 제한
        private val semaphore = Semaphore(maxConnections) // 사용 가능한 연결 수 제어
        private val available = ConcurrentLinkedQueue<PooledDatabaseConnection>()
        private val inUse = ConcurrentLinkedQueue<PooledDatabaseConnection>()
        private var connectionIdCounter = 0

        companion object {
            private val instance = DatabaseConnectionPool()

            fun getInstance(): DatabaseConnectionPool {
                return instance
            }
        }

        // 객체 대여
        fun borrowConnection(): PooledDatabaseConnection {
            semaphore.acquire() // 사용 가능한 슬롯 확보

            var connection = available.poll()
            if (connection == null) {
                // 풀에 사용 가능한 연결이 없으면 새로 생성
                connection = PooledDatabaseConnection(++connectionIdCounter)
            }

            connection.markAsInUse()
            inUse.add(connection)

            return connection
        }

        // 객체 반환
        fun returnConnection(connection: PooledDatabaseConnection) {
            if (inUse.remove(connection)) {
                connection.reset() // 상태 초기화
                connection.markAsAvailable()
                available.add(connection)
                semaphore.release() // 슬롯 해제
            }
        }

        // 풀 상태 정보
        fun getPoolStatus(): String {
            return "Pool status: ${available.size} available, ${inUse.size} in use"
        }

        // 모든 연결 종료 (애플리케이션 종료 시)
        fun shutdown() {
            println("Shutting down connection pool")
            available.clear()
            inUse.clear()
        }
    }

    // 개선된 서비스 클래스
    class ImprovedDatabaseService {
        private val pool = DatabaseConnectionPool.getInstance()

        fun executeQueries(queries: List<String>): List<String> {
            val results = mutableListOf<String>()

            for (query in queries) {
                val connection = pool.borrowConnection()
                try {
                    results.add(connection.executeQuery(query))
                } finally {
                    pool.returnConnection(connection)
                }
            }

            return results
        }
    }
}

fun main() {
    val service = Solution.ImprovedDatabaseService()
    val pool = Solution.DatabaseConnectionPool.getInstance()

    val queries = listOf(
        "SELECT * FROM users",
        "SELECT * FROM products",
        "UPDATE users SET last_login = NOW()",
        "SELECT COUNT(*) FROM orders",
        "INSERT INTO logs VALUES(...)",
        "SELECT * FROM settings",
        "UPDATE products SET stock = stock - 1"
    )

    println("Executing queries with object pool...")
    val startTime = System.currentTimeMillis()

    // 쿼리 실행
    service.executeQueries(queries)

    val endTime = System.currentTimeMillis()
    println("Total execution time: ${endTime - startTime} ms")

    // 풀 상태 출력
    println(pool.getPoolStatus())

    // 두번째 쿼리 배치 실행 (연결은 이미 생성되어 있으므로 빠름)
    println("\nExecuting second batch of queries...")
    val startTime2 = System.currentTimeMillis()

    service.executeQueries(queries.shuffled().take(5))

    val endTime2 = System.currentTimeMillis()
    println("Second batch execution time: ${endTime2 - startTime2} ms")
    println(pool.getPoolStatus())
}