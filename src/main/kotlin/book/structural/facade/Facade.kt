package book.structural.facade

import kotlin.io.path.Path

/**
 * 이 구현은 어댑터 디자인 패턴에서와 완전히 동일하다는 것을 알 수 있습니다.
 * 유일한 차이점은 최종 목표입니다.
 * 어댑터 디자인 패턴의 경우, 다른 방법으로는 사용할 수 없는 클래스를 사용할 수 있게 만드는 것이 목표입니다.
 */
fun Server.startFromConfiguration(fileLocation: String) {
    val path = Path(fileLocation)
    val lines = path.toFile().readLines()
    val configuration = try {
        JSONParser().serve(lines)
    } catch (e: RuntimeException) {
        YamlParser().serve(lines)
    }

    Server.withPort(configuration.port)
}

class JSONParser {
    fun serve(lines: List<String>): Configuration {
        return Configuration(lines.size.toLong())
    }
}

class YamlParser {
    fun serve(lines: List<String>): Configuration {
        return Configuration(lines.size.toLong())
    }
}

data class Configuration(
    val port: Long
)
class Server private constructor(port: Long) {
    init {
        println("Server started on port $port")
    }

    companion object {
        fun withPort(port: Long) = Server(port)
    }
}