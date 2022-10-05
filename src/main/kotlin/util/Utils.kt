package util

fun linebreak(line: Int = 1) {
    repeat(line) {
        println()
    }
}

fun divider(message: String? = null) {
    if (message.isNullOrEmpty()) {
        println("------------------------------------")
    } else {
        println("--------------- $message ---------------")
    }
}

fun dividerWithMessage(message: String) {
    println("------------- $message -------------")
}