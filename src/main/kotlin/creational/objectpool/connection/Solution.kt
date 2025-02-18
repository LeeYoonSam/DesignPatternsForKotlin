package creational.objectpool.connection

import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 연결을 재사용하는 스레드 안전 연결 풀을 구현
 */
class Solution {
    class ConnectionPool private constructor(
        private val connectionString: String,
        private val maxSize: Int
    ) {
        private val lock = ReentrantLock()
        private val semaphore = Semaphore(maxSize)
        private val pool: ConcurrentLinkedQueue<Problem.DatabaseConnection> = ConcurrentLinkedQueue()

        companion object {
            @Volatile
            private var instance: ConnectionPool? = null

            fun getInstance(connectionString: String, maxSize: Int): ConnectionPool {
                return instance ?: synchronized(this) {
                    instance ?: ConnectionPool(connectionString, maxSize).also { instance = it }
                }
            }
        }

        init {
            // Pre-create connection
            repeat(maxSize) {
                pool.offer(Problem.DatabaseConnection(connectionString))
            }
        }

        suspend fun acquire(): Problem.DatabaseConnection {
            semaphore.acquire()
            return lock.withLock {
                pool.poll() ?: Problem.DatabaseConnection(connectionString)
            }
        }

        fun release(connection: Problem.DatabaseConnection) {
            lock.withLock {
                pool.offer(connection)
            }
            semaphore.release()
        }
    }

    // Helper class to ensure connection is always returned to pool
    class PooledConnection(
        private val connection: Problem.DatabaseConnection,
        private val pool: ConnectionPool
    ) : AutoCloseable {
        fun executeQuery(query: String): String = connection.executeQuery(query)

        override fun close() {
            pool.release(connection)
        }
    }
}

suspend fun main() {
    val startTime = System.currentTimeMillis()
    val pool = Solution.ConnectionPool.getInstance("jdbc:postgresql://localhost:5432/mydb", 3)

    // Simulate multiple concurrent requests using connection pool
    repeat(5) {
        Solution.PooledConnection(pool.acquire(), pool).use { connection ->
            val result = connection.executeQuery("SELECT * FROM users")
            println(result)
        }
    }

    val endTime = System.currentTimeMillis()
    println("Total time taken: ${endTime - startTime}ms")
}