package behavioral.interceptor.http

class Problem {
    // Request handler with mixed concerns
    class RequestHandler {
        fun handleRequest(request: Request) {
            // Authentication logic mixed with business logic
            if (!authenticateRequest(request)) {
                println("Authentication failed")
                return
            }

            // Logging mixed with business logic
            println("Processing request: ${request.path}")

            // Performance tracking mixed with business logic
            val startTime = System.currentTimeMillis()

            try {
                // Business logic
                when (request.path) {
                    "/users" -> {
                        println("Fetching user data")
                        // More business logic...
                    }
                    "/products" -> {
                        println("Fectching product data")
                        // More business logic...
                    }
                    else -> println("Unknown path: ${request.path}")
                }

                // More logging
                println("Request processed successfully")
            } catch (e: Exception) {
                // Error logging mixed with business logic
                println("Error processing request: ${e.message}")
                throw e
            } finally {
                // Performance logging mixed with business logic
                val endTime = System.currentTimeMillis()
                println("Request processing took ${endTime - startTime}")
            }
        }

        private fun authenticateRequest(request: Request): Boolean {
            return request.headers["Authorization"] != null
        }
    }
}

fun main() {
    val handler = Problem.RequestHandler()

    // Test with valid request
    val validRequest = Request(
        path = "/users",
        method = "GET",
        headers = mapOf("Authorization" to "Bearer token123"),
        body = null
    )

    println("=== Processing Valid Request ===")
    handler.handleRequest(validRequest)

    // Test with invalid request
    val invalidRequest = Request(
        path = "/products",
        method = "GET",
        headers = mapOf(),
        body = null
    )

    println("\n=== Processing Invalid Request ===")
    handler.handleRequest(invalidRequest)
}