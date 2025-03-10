package structural.decorator.authentication

class Solution {
    // Step 1: Define the Component interface
    interface AuthService {
        fun authenticate(credentials: Map<String, String>): AuthResult
    }

    // Step 2: Create Concrete Components

    // Basic Username/Password Authentication
    class BasicAuthServiceImpl : AuthService {
        private val users = mutableMapOf<String, User>()

        init {
            // Add some users for testing
            users["1"] = User("1", "admin", "admin123")
            users["2"] = User("2", "user", "user123")
        }

        override fun authenticate(credentials: Map<String, String>): AuthResult {
            val username = credentials["username"] ?: return AuthResult(false, message = "Username is required")
            val password = credentials["password"] ?: return AuthResult(false, message = "Password is required")

            // Find user by username
            val user = users.values.find { it.username == username }

            // Check if user exists and password matches
            return if (user != null && user.password == password) {
                AuthResult(true, user, "basic-auth-token-${user.id}", "Authentication successful")
            } else {
                AuthResult(false, message = "Invalid username or password")
            }
        }
    }

    // Token Authentication
    class TokenAuthServiceImpl : AuthService {
        private val validTokens = mutableMapOf<String, User>()

        init {
            // Add some tokens for testing
            validTokens["token-1"] = User("1", "admin")
            validTokens["token-2"] = User("2", "user")
        }

        override fun authenticate(credentials: Map<String, String>): AuthResult {
            val token = credentials["token"] ?: return AuthResult(false, message = "Token is required")

            return if (validTokens.containsKey(token)) {
                val user = validTokens[token]
                AuthResult(true, user, token, "Token authentication successful")
            } else {
                AuthResult(false, message = "Invalid token")
            }
        }
    }

    // Step 3: Create the Decorator base class
    abstract class AuthServiceDecorator(private val authService: AuthService) : AuthService {
        // By default, just delegate to the wrapped authService
        override fun authenticate(credentials: Map<String, String>): AuthResult {
            return authService.authenticate(credentials)
        }
    }

    // Step 4: Create Concrete Decorators

    // Logging Decorator
    class LoggingAuthDecorator(authService: AuthService) : AuthServiceDecorator(authService) {
        override fun authenticate(credentials: Map<String, String>): AuthResult {
            // Print authentication attempt
            println("LOG: Authentication attempt with credentials: ${credentials.keys.joinToString()}")

            // Call the wrapped service's method
            val result = super.authenticate(credentials)

            // Print authentication result
            println("LOG: Authentication ${if (result.success) "successful" else "failed"} - ${result.message}")

            return result
        }
    }

    // Caching Decorator
    class CachingAuthDecorator(authService: AuthService) : AuthServiceDecorator(authService) {
        private val cache = mutableMapOf<String, AuthResult>()

        override fun authenticate(credentials: Map<String, String>): AuthResult {
            // Create a cache key from credentials
            val cacheKey = credentials.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }

            // Check cache
            if (cache.containsKey(cacheKey)) {
                println("CACHE: Using cached authentication result")
                return cache[cacheKey]!!
            }

            // If not in cache, authenticate using the wrapped service
            val result = super.authenticate(credentials)

            // Cache successful results
            if (result.success) {
                println("CACHE: Caching authentication result")
                cache[cacheKey] = result
            }

            return result
        }
    }

    // Rate Limiting Decorator
    class RateLimitingAuthDecorator(
        authService: AuthService,
        private val maxAttempts: Int = 3,
        private val resetTimeMs: Long = 60000 // 1 minute
    ) : AuthServiceDecorator(authService) {

        private data class AttemptInfo(var count: Int, var firstAttemptTime: Long)
        private val attemptsByKey = mutableMapOf<String, AttemptInfo>()

        override fun authenticate(credentials: Map<String, String>): AuthResult {
            // Create a key for rate limiting (username or IP could be used)
            val limitKey = credentials["username"] ?: credentials["token"] ?: "anonymous"
            val currentTime = System.currentTimeMillis()

            // Initialize or get attempts info
            val attemptInfo = attemptsByKey.getOrPut(limitKey) { AttemptInfo(0, currentTime) }

            // Reset attempts if reset time has passed
            if (currentTime - attemptInfo.firstAttemptTime > resetTimeMs) {
                attemptInfo.count = 0
                attemptInfo.firstAttemptTime = currentTime
            }

            // Check rate limiting
            if (attemptInfo.count >= maxAttempts) {
                val waitTime = (resetTimeMs - (currentTime - attemptInfo.firstAttemptTime)) / 1000
                return AuthResult(
                    false,
                    message = "Rate limit exceeded. Try again in $waitTime seconds."
                )
            }

            // Increment attempt counter
            attemptInfo.count++

            // Proceed with authentication using the wrapped service
            return super.authenticate(credentials)
        }
    }

    // Auditing Decorator
    class AuditingAuthDecorator(authService: AuthService) : AuthServiceDecorator(authService) {
        private val auditLog = mutableListOf<String>()

        override fun authenticate(credentials: Map<String, String>): AuthResult {
            val startTime = System.currentTimeMillis()
            val identifier = credentials["username"] ?: credentials["token"] ?: "anonymous"

            // Call the wrapped service's method
            val result = super.authenticate(credentials)

            // Record audit information
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val status = if (result.success) "SUCCESS" else "FAILURE"

            val auditEntry = "AUDIT: [$endTime] $status - User: $identifier - Duration: ${duration}ms - Message: ${result.message}"
            auditLog.add(auditEntry)
            println(auditEntry)

            return result
        }

        fun getAuditLog(): List<String> = auditLog.toList()
    }
}

fun main() {
    // Test basic authentication
    println("=== Basic Authentication ===")
    val basicAuth = Solution.BasicAuthServiceImpl()
    val basicResult = basicAuth.authenticate(mapOf("username" to "admin", "password" to "admin123"))
    println("Result: ${basicResult.message}\n")

    // Test token authentication
    println("=== Token Authentication ===")
    val tokenAuth = Solution.TokenAuthServiceImpl()
    val tokenResult = tokenAuth.authenticate(mapOf("token" to "token-1"))
    println("Result: ${tokenResult.message}\n")

    // Test with a single decorator
    println("=== Basic Authentication with Logging ===")
    val loggingBasicAuth = Solution.LoggingAuthDecorator(Solution.BasicAuthServiceImpl())
    val loggingResult = loggingBasicAuth.authenticate(mapOf("username" to "admin", "password" to "admin123"))
    println("Result: ${loggingResult.message}\n")

    // Test with multiple decorators - note how easily we can stack them
    println("=== Token Authentication with Logging and Caching ===")

    // Create a service decorated with both logging and caching
    val decoratedTokenAuth = Solution.LoggingAuthDecorator(
        Solution.CachingAuthDecorator(
            Solution.TokenAuthServiceImpl()
        )
    )

    println("First call (not cached):")
    val decoratedResult1 = decoratedTokenAuth.authenticate(mapOf("token" to "token-2"))
    println("Result: ${decoratedResult1.message}\n")

    println("Second call (should be cached):")
    val decoratedResult2 = decoratedTokenAuth.authenticate(mapOf("token" to "token-2"))
    println("Result: ${decoratedResult2.message}\n")

    // Test with rate limiting
    println("=== Basic Authentication with Rate Limiting ===")
    val rateLimitedAuth = Solution.RateLimitingAuthDecorator(Solution.BasicAuthServiceImpl(), 3)

    // Try multiple authentication attempts with wrong password
    for (i in 1..4) {
        println("Attempt $i:")
        val result = rateLimitedAuth.authenticate(mapOf("username" to "user", "password" to "wrong-password"))
        println("Result: ${result.message}\n")
    }

    // Demo all features together in a complex auth service
    println("=== Complex Authentication Service with Multiple Decorators ===")

    // Notice how easily we can compose different decorators in any order
    val complexAuthService = Solution.LoggingAuthDecorator(
        Solution.AuditingAuthDecorator(
            Solution.RateLimitingAuthDecorator(
                Solution.CachingAuthDecorator(
                    Solution.BasicAuthServiceImpl()
                ),
                2  // Lower limit for demo
            )
        )
    )

    println("First attempt with valid credentials:")
    val complexResult1 = complexAuthService.authenticate(mapOf("username" to "admin", "password" to "admin123"))
    println("Result: ${complexResult1.message}\n")

    println("Second attempt with same credentials (should use cache):")
    val complexResult2 = complexAuthService.authenticate(mapOf("username" to "admin", "password" to "admin123"))
    println("Result: ${complexResult2.message}\n")

    println("Third attempt with invalid credentials:")
    val complexResult3 = complexAuthService.authenticate(mapOf("username" to "admin", "password" to "wrong"))
    println("Result: ${complexResult3.message}\n")

    println("Fourth attempt with invalid credentials (should hit rate limit):")
    val complexResult4 = complexAuthService.authenticate(mapOf("username" to "admin", "password" to "wrong"))
    println("Result: ${complexResult4.message}\n")

    // Show how easy it is to create different combinations of features
    println("=== Flexibility of Decorators ===")
    println("We can easily create various combinations of features:")
    println("1. Basic Auth with Logging only")
    println("2. Token Auth with Caching only")
    println("3. Basic Auth with Logging and Rate Limiting")
    println("4. Token Auth with Auditing, Caching, and Logging")
    println("5. Any other combination without creating new classes\n")

    // Example of different order of decorators
    println("=== Decorator Order Matters ===")

    // Caching before rate limiting (caching happens first)
    println("Caching before Rate Limiting:")
    val orderA = Solution.LoggingAuthDecorator(
        Solution.RateLimitingAuthDecorator(
            Solution.CachingAuthDecorator(
                Solution.BasicAuthServiceImpl()
            )
        )
    )

    // First call - normal auth flow
    println("First call:")
    orderA.authenticate(mapOf("username" to "user", "password" to "user123"))

    // Second call - should be cached and bypass rate limiting
    println("\nSecond call (same credentials):")
    orderA.authenticate(mapOf("username" to "user", "password" to "user123"))

    // Rate limiting before caching (rate limiting happens first)
    println("\nRate Limiting before Caching:")
    val orderB = Solution.LoggingAuthDecorator(
        Solution.CachingAuthDecorator(
            Solution.RateLimitingAuthDecorator(
                Solution.BasicAuthServiceImpl()
            )
        )
    )

    // Reset for demo
    println("\nFirst call:")
    orderB.authenticate(mapOf("username" to "user", "password" to "user123"))

    // Second call - rate limiting increments before checking cache
    println("\nSecond call (same credentials):")
    orderB.authenticate(mapOf("username" to "user", "password" to "user123"))

    println("\n=== Conclusion ===")
    println("The Decorator pattern allows us to:")
    println("1. Add functionality dynamically at runtime")
    println("2. Combine features in any order without modifying existing code")
    println("3. Apply the Single Responsibility Principle by separating concerns")
    println("4. Avoid class explosion when multiple feature combinations are needed")
}