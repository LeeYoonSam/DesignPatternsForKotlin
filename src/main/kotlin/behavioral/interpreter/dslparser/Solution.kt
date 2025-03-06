package behavioral.interpreter.dslparser

class Solution {
    interface Expression {
        fun interpret(): Int
    }

    class NumberExpression(private val number: Int): Expression {
        override fun interpret(): Int = number
    }

    class AddExpression(private val left: Expression, private val right: Expression) : Expression {
        override fun interpret(): Int = left.interpret() + right.interpret()
    }

    class SubtractExpression(private val left: Expression, private val right: Expression) : Expression {
        override fun interpret(): Int = left.interpret() - right.interpret()
    }

    class MultiplyExpression(private val left: Expression, private val right: Expression) : Expression {
        override fun interpret(): Int = left.interpret() * right.interpret()
    }

    class DivideExpression(private val left: Expression, private val right: Expression) : Expression {
        override fun interpret(): Int = left.interpret() / right.interpret()
    }

    class Interpreter {
        fun parse(expression: String): Expression {
            val tokens = expression.split(" ")
            val stack = mutableListOf<Expression>()

            for (token in tokens) {
                when (token) {
                    "+" -> {
                        val right = stack.removeLast()
                        val left = stack.removeLast()
                        stack.add(AddExpression(left, right))
                    }
                    "-" -> {
                        val right = stack.removeLast()
                        val left = stack.removeLast()
                        stack.add(SubtractExpression(left, right))
                    }
                    "*" -> {
                        val right = stack.removeLast()
                        val left = stack.removeLast()
                        stack.add(MultiplyExpression(left, right))
                    }
                    "/" -> {
                        val right = stack.removeLast()
                        val left = stack.removeLast()
                        stack.add(DivideExpression(left, right))
                    }
                    else -> stack.add(NumberExpression(token.toInt()))
                }
            }
            return stack.last()
        }
    }
}

fun main() {
    val interpreter = Solution.Interpreter()
    val expression = interpreter.parse("5 3 + 2 *")
    println(expression.interpret()) // 16
}