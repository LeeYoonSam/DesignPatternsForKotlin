package creational.multiton.registry

class Solution {
    enum class DatabaseType {
        MYSQL,
        POSTGRESQL,
        MONGODB
    }

    class DatabaseRegistry private constructor(private val dbType: DatabaseType) {
        companion object {
            @Volatile
            private var instances: MutableMap<DatabaseType, DatabaseRegistry> = mutableMapOf()

            fun getInstance(dbType: DatabaseType): DatabaseRegistry {
                return instances.computeIfAbsent(dbType) {
                    synchronized(DatabaseRegistry::class.java) {
                        DatabaseRegistry(it)
                    }
                }
            }

            fun clearInstance() {
                instances.clear()
            }
        }

        init {
            println("Creating new database connection for ${dbType.name}")
            // 실제 데이터베이스 연결 설정
        }

        fun query(sql: String) {
            println("Executing query on ${dbType.name}: $sql")
        }
    }
}

fun main() {
    // 이제 같은 데이터베이스 타입에 대해 단일 인스턴스만 생성됨
    val mysql1 = Solution.DatabaseRegistry.getInstance(Solution.DatabaseType.MYSQL)
    val mysql2 = Solution.DatabaseRegistry.getInstance(Solution.DatabaseType.MYSQL)
    val postgres1 = Solution.DatabaseRegistry.getInstance(Solution.DatabaseType.POSTGRESQL)
    val postgres2 = Solution.DatabaseRegistry.getInstance(Solution.DatabaseType.POSTGRESQL)

    println("mysql1 === mysql2: ${mysql1 === mysql2}") // true
    println("postgres1 === postgres2: ${postgres1 === postgres2}") // true

    mysql1.query("SELECT * FROM users")
    mysql2.query("SELECT * FROM products")
    postgres1.query("SELECT * FROM orders")
    postgres2.query("SELECT * FROM inventory")
}