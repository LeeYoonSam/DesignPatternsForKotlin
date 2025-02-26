package creational.objectpool.dbconnection

import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Problem {
    // 데이터베이스 연결을 표현하는 클래스
    class DatabaseConnection(val id: Int) {
        init {
            // 실제 환경에서는 DB 연결 설정에 시간이 걸림을 시뮬레이션
            println("Creating database connection #$id (expensive operation)")
            TimeUnit.MILLISECONDS.sleep(500) // DB 연결 생성은 비용이 큰 작업
        }

        fun executeQuery(query: String): String {
            println("Connection #$id executing: $query")
            // 실제 쿼리 실행 시간 시뮬레이션
            TimeUnit.MILLISECONDS.sleep(100)
            return "Result for '$query'"
        }

        fun close() {
            println("Closing connection #$id")
        }
    }

    // 문제가 있는 코드: 매 번 새로운 DB 연결 생성 및 파괴
    class DatabaseService {
        fun executeQueries(queries: List<String>): List<String> {
            val results = mutableListOf<String>()

            for (query in queries) {
                // 매 쿼리마다 새 연결 생성 (비효율적)
                val connection = DatabaseConnection(Random.nextInt(1000))
                results.add(connection.executeQuery(query))
                connection.close()
            }

            return results
        }
    }
}

fun main() {
    val service = Problem.DatabaseService()
    val queries = listOf(
        "SELECT * FROM users",
        "SELECT * FROM products",
        "UPDATE users SET last_login = NOW()",
        "SELECT COUNT(*) FROM orders",
        "INSERT INTO logs VALUES(...)"
    )

    println("Executing queries without object pool...")
    val startTime = System.currentTimeMillis()
    service.executeQueries(queries)
    val endTime = System.currentTimeMillis()

    println("Total execution time: ${endTime - startTime} ms")
    // 문제점:
    // 1. 매 쿼리마다 새 연결 생성 (느림)
    // 2. 연결 수 제한 없음 (리소스 과다 사용 가능성)
    // 3. 연결 생성/소멸 오버헤드 큼
}

