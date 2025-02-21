package stability.ratelimiter.api

class Problem {
    class APIService {
        fun handleRequest(userId: String, requestData: String) {
            // No rate limiting - vulnerable to abuse
            processRequest(userId, requestData)
        }

        private fun processRequest(userId: String, requestData: String) {
            // Simulate API processing
            println("Processing request from user $userId: $requestData")
            Thread.sleep(100) // Simulate processing time
        }
    }
}

fun main() {
    val api = Problem.APIService()
    val userId = "user123"

    // Simulate rapid requests from a single user
    println("=== Simulating rapid API requests ===")
    repeat(100) { requestNum ->
        Thread {
            api.handleRequest(userId, "Request #$requestNum")
        }.start()
    }

    // System becomes overwhelmed with requests
    // No protection against:
    // 1. DoS attacks
    // 2. Resource exhaustion
    // 3. Cost overruns
    // 4. Unfair resource allocation
}