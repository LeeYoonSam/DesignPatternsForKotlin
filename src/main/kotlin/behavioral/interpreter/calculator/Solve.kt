package behavioral.interpreter.calculator

class Solve {
    // Expression 인터페이스
    interface Expression {
        fun interpret(): Int
    }

    // Terminal Expression - 숫자
    class NumberExpression(private val number: Int) : Expression {
        override fun interpret(): Int = number
    }

    // Non-terminal Expression - 덧셈
    class AddExpression(
        private val left: Expression,
        private val right: Expression
    ): Expression {
        override fun interpret(): Int = left.interpret() + right.interpret()
    }

    // Non-terminal Expression - 뺄셈
    class SubtractExpression(
        private val left: Expression,
        private val right: Expression
    ): Expression {
        override fun interpret(): Int = left.interpret() - right.interpret()
    }

    // Non-terminal Expression - 곱셈
    class MultiplyExpression(
        private val left: Expression,
        private val right: Expression
    ): Expression {
        override fun interpret(): Int = left.interpret() * right.interpret()
    }

    // Non-terminal Expression - 나눗셈
    class DivideExpression(
        private val left: Expression,
        private val right: Expression
    ): Expression {
        override fun interpret(): Int = left.interpret() / right.interpret()
    }

    // Parser - 문자열을 Expression 객체로 변환
    class ExpressionParser {
        private var pos = 0
        private lateinit var tokens: List<String>

        fun parser(expression: String): Expression {
            tokens = tokenize(expression)
            pos = 0
            return parseExpression()
        }

        private fun tokenize(expression: String): List<String> {
            return expression
                .replace("(", " ( ")
                .replace(")", " ) ")
                .trim() // 문자열 앞뒤 공백 제거
                .split("\\s+".toRegex()) // 공백을 기준으로 문자열 분리
        }

        private fun parseExpression(): Expression {
            var left = parseTerm()

            while (pos < tokens.size) {
                val token = tokens[pos]
                when (token) {
                    "+" -> {
                        pos++
                        left = AddExpression(left, parseTerm())
                    }
                    "-" -> {
                        pos++
                        left = SubtractExpression(left, parseTerm())
                    }
                    ")" -> break
                    else -> break
                }
            }

            return left
        }

        private fun parseTerm(): Expression {
            var left = parseFactor()

            while (pos < tokens.size) {
                val token = tokens[pos]
                when (token) {
                    "*" -> {
                        pos++
                        left = MultiplyExpression(left, parseTerm())
                    }
                    "/" -> {
                        pos++
                        left = DivideExpression(left, parseTerm())
                    }
                    ")" -> break
                    else -> break
                }
            }

            return left
        }

        private fun parseFactor(): Expression {
            val token = tokens[pos++]

            return when {
                token == "(" -> {
                    val expression = parseExpression()
                    pos++ // skip closing parenthesis
                    expression
                }
                // 숫자 정규식(마이너스 옵션)
                token.matches("-?\\d+".toRegex()) -> NumberExpression(token.toInt())
                else -> throw IllegalArgumentException("Unexpected token: $token")
            }
        }
    }

    // 계산기 클래스
    class Calculator {
        private val parser = ExpressionParser()

        fun evaluate(expression: String): Int {
            val expr = parser.parser(expression)
            return expr.interpret()
        }
    }
}

fun main() {
    val calculator = Solve.Calculator()

    // 단순 계산
    println("3 + 2 = ${calculator.evaluate("3 + 2")}")
    println("10 - 5 = ${calculator.evaluate("10 - 5")}")
    println("4 * 3 = ${calculator.evaluate("4 * 3")}")

    // 복잡한 수식도 처리 가능
    println("3 + 2 * 4 = ${calculator.evaluate("3 + 2 * 4")}")
    println("(10 - 5) * 2 = ${calculator.evaluate("(10 - 5) * 2")}")
    println("15 / 3 + 2 * 4 = ${calculator.evaluate("15 / 3 + 2 * 4")}")
}