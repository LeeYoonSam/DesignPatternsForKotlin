package behavioral.interpreter.calculator

/**
 * 문제점
 * - 복잡한 문법 해석 로직
 * - 새로운 표현식 추가의 어려움
 * - 규칙 확장성 부족
 * - 유지보수의 어려움
 * - 코드 중복
 */
class Problem {
    class Calculator {
        fun evaluate(expression: String): Int {
            val tokens = expression.split(" ")

            // 단순한 사칙연산만 처리하는 계산기
            when (tokens[1]) {
                "+" -> return tokens[0].toInt() + tokens[2].toInt()
                "-" -> return tokens[0].toInt() - tokens[2].toInt()
                "*" -> return tokens[0].toInt() * tokens[2].toInt()
                "/" -> return tokens[0].toInt() / tokens[2].toInt()
                else -> throw IllegalArgumentException("Unknown operator")
            }
        }
    }
}

fun main() {
    val calculator = Problem.Calculator()

    // 단순 계산만 가능
    println("3 + 2 = ${calculator.evaluate("3 + 2")}")
    println("10 - 5 = ${calculator.evaluate("10 - 5")}")
    println("4 * 3 = ${calculator.evaluate("4 * 3")}")

    // 복잡한 수식은 처리 불가
    // calculator.evaluate("3 + 2 * 4")  // 에러 발생
    // calculator.evaluate("(10 - 5) * 2")  // 에러 발생
}