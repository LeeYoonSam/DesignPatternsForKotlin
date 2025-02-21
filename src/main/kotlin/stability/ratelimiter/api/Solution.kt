package stability.ratelimiter.api

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Solution {
    // Rate Limiter Interface
    interface RateLimiter {
        fun tryAcquire(key: String): Boolean
    }

    // Token Bucket Rate Limiter
    class TokenBucketRateLimiter(
        private val bucketCapacity: Int,
        private val refillRate: Int, // tokens per second
    ) : RateLimiter {
        private val buckets = ConcurrentHashMap<String, TokenBucket>()

        inner class TokenBucket(
            var tokens: Int,
            var lastRefillTime: Instant
        )

        override fun tryAcquire(key: String): Boolean {
            val bucket = buckets.computeIfAbsent(key) {
                TokenBucket(bucketCapacity, Instant.now())
            }

            synchronized(bucket) {
                refillTokens(bucket)
                if (bucket.tokens > 0) {
                    bucket.tokens--
                    return true
                }
                return false
            }
        }

        private fun refillTokens(bucket: TokenBucket) {
            val now = Instant.now()
            val timeElapsed = now.epochSecond - bucket.lastRefillTime.epochSecond
            val tokensToAdd = (timeElapsed * refillRate).toInt()

            if (tokensToAdd > 0) {
                bucket.tokens = (bucket.tokens + tokensToAdd).coerceAtMost(bucketCapacity)
                bucket.lastRefillTime = now
            }
        }
    }

    // Sliding Window Rate Limiter
    class SlidingWindowRateLimiter(
        private val windowSizeSeconds: Int,
        private val maxRequests: Int
    ) : RateLimiter {
        private val requestWindows = ConcurrentHashMap<String, ConcurrentLinkedQueue<Instant>>()
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        override fun tryAcquire(key: String): Boolean {
            val lock = locks.computeIfAbsent(key) { ReentrantLock() }
            val requests = requestWindows.computeIfAbsent(key) { ConcurrentLinkedQueue() }

            return lock.withLock {
                val now = Instant.now()
                val windowStart = now.minusSeconds(windowSizeSeconds.toLong())

                // Remove expired requests
                while (requests.isNotEmpty() && requests.peek().isBefore(windowStart)) {
                    requests.poll()
                }

                // Check if we can accept new request
                if (requests.size < maxRequests) {
                    requests.offer(now)
                    true
                } else {
                    false
                }
            }
        }
    }

    // Enhanced API Service with Rate Limiting
    class RateLimitedAPIService(private val rateLimiter: RateLimiter) {
        fun handleRequest(userId: String, requestData: String): Boolean {
            if (rateLimiter.tryAcquire(userId)) {
                processRequest(userId, requestData)
                return true
            }
            return false
        }

        private fun processRequest(userId: String, requestData: String) {
            println("Processing request from user $userId: $requestData")
            Thread.sleep(100) // Simulate processing time
        }
    }
}

fun main() {
    // Test with Token Bucket Rate Limiter
    println("=== Testing Token Bucket Rate Limiter ===")
    val tokenBucketLimiter = Solution.TokenBucketRateLimiter(
        bucketCapacity = 5,
        refillRate = 2
    )
    val tokenBucketAPI = Solution.RateLimitedAPIService(tokenBucketLimiter)
//    testRateLimiter(tokenBucketAPI)

    Thread.sleep(2000) // Wait for refill

    // Test with Sliding Window Rate Limiter
    println("\n=== Testing Sliding Window Rate Limiter ===")
    val slidingWindowLimiter = Solution.SlidingWindowRateLimiter(
        windowSizeSeconds = 1,
        maxRequests = 2
    )
    val slidingWindowAPI = Solution.RateLimitedAPIService(slidingWindowLimiter)
    testRateLimiter(slidingWindowAPI)
}

fun testRateLimiter(api: Solution.RateLimitedAPIService) {
    val userId = "user123"

    repeat(100) { requestNum ->
        val success = api.handleRequest(userId, "Request #$requestNum")
        if (!success) {
            println("Request #$requestNum was rate limited for user $userId")
        }
        Thread.sleep(200) // Small delay between requests
    }
}