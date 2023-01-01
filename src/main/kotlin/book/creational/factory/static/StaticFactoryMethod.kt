package book.creational.factory.static

class Server private constructor(port: Long) {
    init {
        println("Server started on port $port")
    }

    companion object {
        fun withPort(port: Long) = Server(port)
    }
}

fun main() {
    Server.withPort(8080)
//    Server(8080) // 생성자가 private 으로 되어있어 컴파일 되지 않는다.
}