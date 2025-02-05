package raii.resource

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock

/**
 * 문제점
 * - 수동적인 리소스 관리
 * - 리소스 누수 위험
 * - 예외 처리의 복잡성
 */
class Problem {
    // 파일 및 잠금 리소스를 수동으로 관리하는 예제
    class FileProcessor {
        fun processFile(filename: String) {
            val file = File(filename)
            val lock = ReentrantLock()

            try {
                // 파일 열기
                val reader = file.bufferedReader()

                // 잠금 얻기
                lock.lock()

                try {
                    // 파일 처리
                    val content = reader.readText()
                    println("File content: $content")
                } finally {
                    // 잠금 해제
                    lock.unlock()

                    // 파일 닫기
                    reader.close()
                }

            } catch (e: Exception) {
                println("Error processing file: ${e.message}")
            }
        }
    }

    // 데이터베이스 연결 관리
    class DatabaseConnection {
        private var connection: Connection? = null

        fun connect() {
            connection = DriverManager.getConnection(
                "jdbc:postgresql://localhost/mydb",
                "username",
                "password"
            )
        }

        fun executeQuery(query: String) {
            connection?.let {
                val statement = it.createStatement()
                val result = statement.executeQuery(query)
                // 결과 처리
            }
        }

        fun disconnect() {
            connection?.close()
        }
    }
}

fun main() {
    // 성공 예제
    try {
        val fileProcessor = Problem.FileProcessor()
        fileProcessor.processFile("src/main/kotlin/raii/resource/example.txt")
    } catch (e: Exception) {
        println("File processing error: ${e.message}")
    }

    // 리소스 관리 문제 예제
    try {
        val dbConnection = Problem.DatabaseConnection()
        dbConnection.connect()
        dbConnection.executeQuery("SELECT * FROM users")
        // 연결 닫기를 잊어버림 (잠재적 리소스 누수)
    } catch (e: Exception) {
        println("Database connection error: ${e.message}")
    }
}