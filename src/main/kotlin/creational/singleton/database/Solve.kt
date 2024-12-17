package creational.singleton.database

class Solve {
    // 객체 생성 방식 1: Kotlin의 object 키워드 활용 (권장)
    object DatabaseConnectionManagerSingleton {
        private var connectionCount = 0
        private var connection: Any? = null

        fun connectToDatabase(): Any {
            // 최초 1회만 연결, 이후 동일 인스턴스 반환
            if (connection == null) {
                connection = Any()
                connectionCount++
                println("Creating first database connection.")
            }
            return connection!!
        }

        fun getConnectionCount() = connectionCount
    }

    // 객체 생성 방식 2: 컴패니언 오브젝트를 활용한 싱글톤
    class ApplicationConfigSingleton private constructor() {
        private val configs: MutableMap<String, String> = mutableMapOf()

        companion object {
            @Volatile
            private var instance: ApplicationConfigSingleton? = null

            fun getInstance(): ApplicationConfigSingleton {
                // 이중 검사 락킹 패턴(Thread-Safe)
                return instance ?: synchronized(this) {
                    instance ?: ApplicationConfigSingleton().also { instance = it }
                }
            }
        }

        fun setConfig(key: String, value: String) {
            configs[key] = value
        }

        fun getConfig(key: String): String? {
            return configs[key]
        }
    }

}

fun main() {
    // 싱글톤 오브젝트 사용 예시
    Solve.DatabaseConnectionManagerSingleton.connectToDatabase()
    Solve.DatabaseConnectionManagerSingleton.connectToDatabase()
    println("Connection count: ${Solve.DatabaseConnectionManagerSingleton.getConnectionCount()}")

    // 컴패니언 오브젝트 싱글톤 사용 예시
    val config1 = Solve.ApplicationConfigSingleton.getInstance()
    val config2 = Solve.ApplicationConfigSingleton.getInstance()

    config1.setConfig("database.url", "jdbc:mysql://localhost:3306/mydb")
    println(config2.getConfig("database.url")) // 동일한 인스턴스에서 설정 공유

    // 동일성 비교
    println("Are configs the same instance? ${config1 === config2}")
}