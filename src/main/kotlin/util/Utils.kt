package util

fun linebreak(line: Int = 1) {
    repeat(line) {
        println()
    }
}

fun divider() {
    println("------------------------------------")
}

fun dividerWithMessage(message: String) {
    println("------------- $message -------------")
}