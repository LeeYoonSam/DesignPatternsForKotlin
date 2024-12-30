package behavioral.chain.logger

/**
 * 문제점
 * - 요청 처리 로직의 중앙 집중화
 * - 처리기 변경의 어려움
 * - 처리 순서 변경의 어려움
 * - 처리 과정의 모니터링 부족
 */
class Problem {
    // 문제가 있는 코드: 조건문을 사용한 로깅 처리
    class SimpleLogger {
        fun log(level: String, message: String) {
            when (level) {
                "DEBUG" -> {
                    println("[DEBUG] $message")
                }
                "INFO" -> {
                    println("[INFO] $message")
                    // 파일에 저장
                    saveToFile("[INFO] $message")
                }
                "ERROR" -> {
                    println("[ERROR] $message")
                    // 파일에 저장
                    saveToFile("[ERROR] $message")
                    // 관리자에게 알림
                    notifyAdmin("[ERROR] $message")
                }
            }
        }

        private fun saveToFile(message: String) {
            println("파일에 저장: $message")
        }

        private fun notifyAdmin(message: String) {
            println("관리자에게 알림 전송: $message")
        }
    }
}

fun main() {
    val logger = Problem.SimpleLogger()
    logger.log("DEBUG", "Debug message test")
    logger.log("INFO", "INFO message test")
    logger.log("ERROR", "ERROR message test")
}