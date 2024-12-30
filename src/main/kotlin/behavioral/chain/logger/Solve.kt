package behavioral.chain.logger

class Solve {
    enum class LogLevel {
        DEBUG, INFO, ERROR
    }

    abstract class LogHandler(protected val logLevel: LogLevel) {
        private var nextHandler: LogHandler? = null

        fun setNext(handler: LogHandler): LogHandler {
            nextHandler = handler
            return handler
        }

        fun logMessage(level: LogLevel, message: String) {
            if (level.ordinal >= logLevel.ordinal) {
                write(message)
            }
            nextHandler?.logMessage(level, message)
        }

        protected abstract fun write(message: String)
    }

    class ConsoleLogHandler(level: LogLevel) : LogHandler(level) {
        override fun write(message: String) {
            println("[Console] ${logLevel.name}: $message")
            // 실제로는 파일 저장하는 로직이 들어갈 자리
        }
    }

    class FileLogHandler(level: LogLevel) : LogHandler(level) {
        override fun write(message: String) {
            println("[File] ${logLevel.name}: $message")
            // 실제로는 파일에 저장하는 로직이 들어갈 자리
        }
    }

    class EmailLogHandler(level: LogLevel) : LogHandler(level) {
        override fun write(message: String) {
            println("[Email] ${logLevel.name}: $message")
            // 실제로는 이메일 보내는 로직이 들어갈 자리
        }
    }

    class Logger {
        private val chain: LogHandler

        init {
            // 로그 처리 체인 구성
            val consoleHandler = ConsoleLogHandler(LogLevel.DEBUG)
            val fileHandler = FileLogHandler(LogLevel.INFO)
            val emailHandler = EmailLogHandler(LogLevel.ERROR)

            consoleHandler.setNext(fileHandler).setNext(emailHandler)
            chain = consoleHandler
        }

        // 호출시 체인 안의 핸들러를 모두 순회함
        fun log(level: LogLevel, message: String) {
            chain.logMessage(level, message)
        }
    }
}

fun main() {
    val logger = Solve.Logger()

    println("1. Debug 레벨 로그 테스트:")
    logger.log(Solve.LogLevel.DEBUG, "디버그 메시지입니다.")

    println("\n2. Info 레벨 로그 테스트:")
    logger.log(Solve.LogLevel.INFO, "정보 메시지입니다.")

    println("\n3. Error 레벨 로그 테스트:")
    logger.log(Solve.LogLevel.ERROR, "에러가 발생했습니다.")

    // 실행 결과:
    // Debug 레벨: Console에만 출력
    // Info 레벨: Console과 File에 출력
    // Error 레벨: Console, File, Email 모두에 출력
}