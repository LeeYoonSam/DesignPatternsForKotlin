package creational.objectpool.connection

/**
 * 각 요청에 대해 새 연결을 만들 때 발생하는 성능 문제 발생
 */
class Problem {
    class DatabaseConnection(private val connectionString: String) {
        init {
            // Simulate expensive connection creation
            Thread.sleep(1000)
            println("Creating a new database connection to: $connectionString")
        }

        fun executeQuery(query: String): String {
            return "Result for query: $query"
        }

        fun close() {
            println("Closing database connection")
        }
    }
}

fun main() {
    val startTime = System.currentTimeMillis()

    // Simulate multiple concurrent requests needing databse connections
    repeat(5) {
        val connection = Problem.DatabaseConnection("jdbc:postgresql://localhost:5432/mydb")
        val result = connection.executeQuery("SELECT * FROM users")
        println(result)
        connection.close()
    }

    val endTime = System.currentTimeMillis()
    println("Total time taken: ${endTime - startTime}")
}