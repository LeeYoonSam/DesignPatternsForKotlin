package creational.multiton.registry

class Problem {
    // 문제가 있는 코드: 데이터베이스 연결 관리
    class DatabaseConnection(private val dbType: String) {
        init {
            println("Creating new connection for $dbType")
            // 실제로는 여기서 데이터베이스 연결을 설정
        }

        fun query(sql: String) {
            println("Executing query on $dbType: $sql")
        }
    }
}

fun main() {
    // 문제점: 같은 데이터베이스 타입에 대해 불필요하게 여러 연결이 생성됨
    val mysql1 = Problem.DatabaseConnection("MySQL")
    val mysql2 = Problem.DatabaseConnection("MySQL")
    val postgres1 = Problem.DatabaseConnection("PostgreSQL")
    val postgres2 = Problem.DatabaseConnection("PostgreSQL")

    mysql1.query("SELECT * FROM users")
    mysql2.query("SELECT * FROM products")
    postgres1.query("SELECT * FROM orders")
    postgres2.query("SELECT * FROM inventory")
}