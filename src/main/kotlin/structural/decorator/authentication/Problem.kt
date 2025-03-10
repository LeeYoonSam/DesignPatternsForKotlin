package structural.decorator.authentication

class Problem {
    // Basic authentication service
    class BasicAuthService {
        private val users = mutableMapOf<String, User>()

        init {
            // Add some users for testing
            users["1"] = User("1", "admin", "admin123")
            users["2"] = User("2", "user", "user123")
        }

        fun authenticate(username: String, password: String): AuthResult {
            // Find user by username
            val user = users.values.find { it.username == username }

            // Check if user exists and password matches
            return if (user != null && user.password == password) {
                AuthResult(
                    success = true,
                    user = user,
                    token = "basic-auth-token-${user.id}",
                    message = "Authentication successful"
                )
            } else {
                AuthResult(success = false, message = "Authentication successful")
            }
        }
    }

    // Token authentication service
    class TokenAuthService {
        private val validTokens = mutableMapOf<String, User>()
        private val users = mutableMapOf<String, User>()

        init {
            // Add some users for testing
            val user1 = User("1", "admin", "")
            val user2 = User("2", "user", "")
            users["1"] = user1
            users["2"] = user2

            // Add tokens for testing
            validTokens["token-1"] = user1
            validTokens["token-2"] = user2
        }

        fun authenticateWithToken(token: String): AuthResult {
            return if (validTokens.containsKey(token)) {
                val user = validTokens[token]
                AuthResult(true, user, token, "Token authentication successful")
            } else {
                AuthResult(false, message = "Invalid token")
            }
        }
    }

    // Extended Basic Auth Service with Logging
    class LoggingBasicAuthService {
        private val basicAuthService = BasicAuthService()

        fun authenticate(username: String, password: String): AuthResult {
            println("LOG: Authentication attempt for user: $username")
            val result = basicAuthService.authenticate(username, password)
            println("LOG: Authentication ${if (result.success) "successful" else "failed"} for user: $username")
            return result
        }
    }

    // Extended Token Auth Service with Logging
    class LoggingTokenAuthService {
        private val tokenAuthService = TokenAuthService()

        fun authenticateWithToken(token: String): AuthResult {
            println("LOG: Token authentication attempt with token: ${token.take(5)}...")
            val result = tokenAuthService.authenticateWithToken(token)
            println("LOG: Token authentication ${if (result.success) "successful" else "failed"}")
            return result
        }
    }

    // Extended Basic Auth Service with Rate Limiting
    class RateLimitedBasicAuthService {
        private val basicAuthService = BasicAuthService()
        private val attemptsByUser = mutableMapOf<String, Int>()
        private val maxAttempts = 3

        fun authenticate(username: String, password: String): AuthResult {
            // Check rate limiting
            val attempts = attemptsByUser.getOrDefault(username, 0)
            if (attempts >= maxAttempts) {
                return AuthResult(false, message = "Rate limit exceeded. Try again later.")
            }

            // Increment attempt counter
            attemptsByUser[username] = attempts + 1

            return basicAuthService.authenticate(username, password)
        }
    }

    // Extended TokenAuth Service with Caching
    class CachedTokenAuthService {
        private val tokenAuthService = TokenAuthService()
        private val cache = mutableMapOf<String, AuthResult>()

        fun authenticateWithToken(token: String): AuthResult {
            // Check cache first
            if (cache.containsKey(token)) {
                println("CACHE: Using cached authentication result for token: ${token.take(5)}...")
                return cache[token]!!
            }

            // If not in cache, authenticate
            val result = tokenAuthService.authenticateWithToken(token)

            // Add successful results to cache
            if (result.success) {
                cache[token] = result
                println("CACHE: Cached authentication result for token: ${token.take(5)}...")
            }

            return result
        }
    }
}

// 로깅 + 속도 제한 + 캐싱과 같은 조합이 필요하다면 어떻게 해야 할까요?
// 더 많은 클래스를 만들어야 합니다:
// - LoggingAndRateLimitedBasicAuthService
// - LoggingAndCachedTokenAuthService
// - RateLimitedAndCachedTokenAuthService
// - LoggingAndRateLimitedAndCachedBasicAuthService
// 그리고... 등등...
fun main() {
    println("=== Basic Authentication ===")
    val basicAuth = Problem.BasicAuthService()
    val basicResult = basicAuth.authenticate("admin", "admin123")
    println("Basic Auth Result: ${basicResult.message}")

    println("\n=== Token Authentication ===")
    val tokenAuth = Problem.TokenAuthService()
    val tokenResult = tokenAuth.authenticateWithToken("token-1")
    println("Token Auth Result: ${tokenResult.message}")

    println("\n=== Basic Authentication with Logging ===")
    val loggingBasicAuth = Problem.LoggingBasicAuthService()
    val loggingBasicResult = loggingBasicAuth.authenticate("admin", "admin123")
    println("Logging Basic Auth Result: ${loggingBasicResult.message}")

    println("\n=== Token Authentication with Caching ===")
    val cachedTokenAuth = Problem.CachedTokenAuthService()
    println("First call (not cached):")
    val cachedTokenResult1 = cachedTokenAuth.authenticateWithToken("token-1")
    println("Cached Token Auth Result: ${cachedTokenResult1.message}")

    println("\nSecond call (should be cached):")
    val cachedTokenResult2 = cachedTokenAuth.authenticateWithToken("token-1")
    println("Cached Token Auth Result: ${cachedTokenResult2.message}")

    println("\n=== Basic Authentication with Rate Limiting ===")
    val rateLimitedAuth = Problem.RateLimitedBasicAuthService()

    println("First attempt:")
    val rateLimitedResult1 = rateLimitedAuth.authenticate("user", "wrong-password")
    println("Rate Limited Auth Result: ${rateLimitedResult1.message}")

    println("\nSecond attempt:")
    val rateLimitedResult2 = rateLimitedAuth.authenticate("user", "wrong-password")
    println("Rate Limited Auth Result: ${rateLimitedResult2.message}")

    println("\nThird attempt:")
    val rateLimitedResult3 = rateLimitedAuth.authenticate("user", "wrong-password")
    println("Rate Limited Auth Result: ${rateLimitedResult3.message}")

    println("\nFourth attempt (should be rate limited):")
    val rateLimitedResult4 = rateLimitedAuth.authenticate("user", "user123")
    println("Rate Limited Auth Result: ${rateLimitedResult4.message}")

    // Problem: What if we need to combine these features?
    // For example, a service that has logging, rate limiting, and caching all together?
    // We would need to create many combination classes, leading to class explosion

    println("\n=== Problem ===")
    println("If we need many combinations of these features, we would have to create many classes.")
    println("For example: LoggingAndRateLimitedBasicAuth, LoggingAndCachedTokenAuth, etc.")
    println("This leads to a 'class explosion' problem, which is where the Decorator pattern can help.")
}