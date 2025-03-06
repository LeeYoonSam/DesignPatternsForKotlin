package behavioral.interpreter.dslparser

fun evaluate(expression: String): Int {
    val tokens = expression.split(" ")
    val stack = mutableListOf<Int>()

    for (token in tokens) {
        when (token) {
            "+" -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.add(a + b)
            }

            "-" -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.add(a - b)
            }

            "*" -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.add(a * b)
            }

            "/" -> {
                val b = stack.removeLast()
                val a = stack.removeLast()
                stack.add(a / b)
            }

            else -> stack.add(token.toInt())
        }
    }

    return stack.last()
}

fun main() {
    println(evaluate("5 3 + 2 *")) // (5 + 3) * 2 = 16
}