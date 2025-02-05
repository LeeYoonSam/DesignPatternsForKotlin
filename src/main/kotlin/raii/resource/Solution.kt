package raii.resource

import raii.resource.Solution.Companion.use
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.concurrent.locks.ReentrantLock

/**
 * 해결책: RAII 패턴을 사용한 리소스 관리
 */
class Solution {
    // 파일 리소스 RAII 관리
    class FileResource(filename: String) : Closeable {
        private val reader = File(filename).bufferedReader()

        fun readContent(): String {
            return reader.readText()
        }

        override fun close() {
            reader.close()
            println("File resource closed")
        }
    }

    // 잠금 리소스 RAII 관리
    class LockResource(private val lock: ReentrantLock) : Closeable {
        init {
            lock.lock()
            println("Lock acquired")
        }

        override fun close() {
            lock.unlock()
            println("Lock released")
        }
    }

    // 데이터베이스 연결 RAII 관리
    class DatabaseResource(
        url: String,
        username: String,
        password: String
    ) : Closeable {
        private val connection: Connection =
            DriverManager.getConnection(url, username, password)

        fun executeQuery(query: String): ResultSet {
            val statement = connection.createStatement()
            return statement.executeQuery(query)
        }

        override fun close() {
            connection.close()
            println("Database connection closed")
        }
    }

    // RAII 유틸리티 함수
    companion object {
        inline fun<T : Closeable, R> T.use(block: (T) -> R): R {
            try {
                return block(this)
            } finally {
                close()
            }
        }
    }
}

fun main() {
    // 파일 리소스 RAII 관리
    try {
        Solution.FileResource("src/main/kotlin/raii/resource/example.txt").use { resource ->
            val content = resource.readContent()
            println("File content: $content")
        }
    } catch (e: Exception) {
        println("File processing error: ${e.message}")
    }

    // 잠금 리소스 RAII 관리
    val lock = ReentrantLock()
    try {
        Solution.LockResource(lock).use {
            // 잠금이 필요한 작업 수행
            println("Performing locked operation")
        }
    } catch (e: Exception) {
        println("Lock error: ${e.message}")
    }

    // 데이터베이스 연결 RAII 관리
    try {
        Solution.DatabaseResource(
            "jdbc:postgresql://localhost/mydb",
            "username",
            "password"
        ).use { db ->
            val resultSet = db.executeQuery("SELECT * FROM users")
            // 결과 처리
            println("Query executed successfully")
        }
    } catch (e: Exception) {
        println("Database connection error: ${e.message}")
    }
}