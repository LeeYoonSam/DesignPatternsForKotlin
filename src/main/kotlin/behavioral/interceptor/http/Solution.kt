package behavioral.interceptor.http

import java.util.concurrent.CopyOnWriteArrayList

class Solution {
    // Interceptor interface
    interface RequestInterceptor {
        fun preHandle(request: Request, context: RequestContext): Boolean
        fun postHandle(request: Request, context: RequestContext)
        fun afterCompletion(request: Request, context: RequestContext, exception: Exception?)
    }

    // Context object to share data between interceptors
    class RequestContext {
        private val attributes = mutableMapOf<String, Any>()

        fun setAttribute(key: String, value: Any) {
            attributes[key] = value
        }

        fun getAttribute(key: String): Any? = attributes[key]
    }

    // Interceptor registry
    class InterceptorRegistry {
        private val interceptors = CopyOnWriteArrayList<RequestInterceptor>()

        fun addInterceptor(interceptor: RequestInterceptor) {
            interceptors.add(interceptor)
        }

        fun getInterceptors(): List<RequestInterceptor> = interceptors.toList()
    }

    // Specific interceptors
    class AuthenticationInterceptor : RequestInterceptor {
        override fun preHandle(request: Request, context: RequestContext): Boolean {
            println("AuthenticationInterceptor: Checking authentication")
            val isAuthenticated = request.headers["Authorization"] != null
            context.setAttribute("authenticated", isAuthenticated)
            if (!isAuthenticated) {
                println("AuthenticationInterceptor: Authentication failed")
            }
            return isAuthenticated
        }

        override fun postHandle(request: Request, context: RequestContext) {
            println("AuthenticationInterceptor: Post handle")
        }

        override fun afterCompletion(request: Request, context: RequestContext, exception: Exception?) {
            println("AuthenticationInterceptor: After completion")
        }
    }

    class LoggingInterceptor : RequestInterceptor {
        override fun preHandle(request: Request, context: RequestContext): Boolean {
            println("LoggingInterceptor: Request started - ${request.method} ${request.path}")
            context.setAttribute("startTime", System.currentTimeMillis())
            return true
        }

        override fun postHandle(request: Request, context: RequestContext) {
            println("LoggingInterceptor: Request processed successfully")
        }

        override fun afterCompletion(request: Request, context: RequestContext, exception: Exception?) {
            val startTime = context.getAttribute("startTime") as Long
            val duration = System.currentTimeMillis() - startTime
            println("LoggingInterceptor: Request completed in ${duration}ms")
            exception?.let { println("LoggingInterceptor: Exception occurred - ${it.message}") }
        }
    }

    class PerformanceInterceptor : RequestInterceptor {
        override fun preHandle(request: Request, context: RequestContext): Boolean {
            context.setAttribute("perfStartTime", System.nanoTime())
            return true
        }

        override fun postHandle(request: Request, context: RequestContext) {
            val startTime = context.getAttribute("perfStartTime") as Long
            val duration = (System.nanoTime() - startTime) / 1_000_000.0 // Convert to ms
            println("PerformanceInterceptor: Request processing time: ${String.format("%.2f", duration)}ms")
        }

        override fun afterCompletion(request: Request, context: RequestContext, exception: Exception?) {
            // Additional performance metrics could be added here
        }
    }

    // Enhanced request handler with interceptors
    class InterceptingRequestHandler(private val registry: InterceptorRegistry) {
        fun handleRequest(request: Request) {
            val context = RequestContext()
            val interceptors = registry.getInterceptors()

            try {
                // Pre-handle phase
                for (interceptor in interceptors) {
                    if (!interceptor.preHandle(request, context)) {
                        // If any interceptor returns false, abort the chain
                        return
                    }
                }

                // Business logic
                processRequest(request)

                // Post-handle phase
                for (interceptor in interceptors) {
                    interceptor.postHandle(request, context)
                }

            } catch (e: Exception) {
                // After-completion phase with exception
                for (interceptor in interceptors.reversed()) {
                    interceptor.afterCompletion(request, context, e)
                }
                throw e
            }

            // After-completion phase without exception
            for (interceptor in interceptors.reversed()) {
                interceptor.afterCompletion(request, context, null)
            }
        }

        private fun processRequest(request: Request) {
            when (request.path) {
                "/users" -> println("Business Logic: Fetching user data")
                "/products" -> println("Business Logic: Fetching product data")
                else -> println("Business Logic: Unknown path: ${request.path}")
            }
        }
    }
}

fun main() {
    // Set up interceptor registry
    val registry = Solution.InterceptorRegistry().apply {
        addInterceptor(Solution.AuthenticationInterceptor())
        addInterceptor(Solution.LoggingInterceptor())
        addInterceptor(Solution.PerformanceInterceptor())
    }

    val handler = Solution.InterceptingRequestHandler(registry)

    // Test with valid request
    println("=== Processing Valid Request ===")
    val validRequest = Request(
        path = "/users",
        method = "GET",
        headers = mapOf("Authorization" to "Bearer token123"),
        body = null
    )

    try {
        handler.handleRequest(validRequest)
    } catch (e: Exception) {
        println("Main: Exception caught - ${e.message}")
    }

    // Test with invalid request
    println("\n=== Processing Invalid Request ===")
    val invalidRequest = Request(
        path = "/products",
        method = "GET",
        headers = mapOf(),
        body = null
    )

    try {
        handler.handleRequest(invalidRequest)
    } catch (e: Exception) {
        println("Main: Exception caught - ${e.message}")
    }
}