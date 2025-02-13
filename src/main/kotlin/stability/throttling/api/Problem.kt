package stability.throttling.api

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 제한 없이 요청을 처리
 */
class Problem {
    class ApiService {
        suspend fun fetchData(requestId: Int): String {
            delay(100) // Simulating API call
            return "Data for request $requestId"
        }
    }

    class UnThrottledClient(private val apiService: ApiService) {
        suspend fun processRequests() {
            coroutineScope {
                // 많은 요청을 동시에 보냄
                repeat(100) { requestId ->
                    launch {
                        try {
                            val result = apiService.fetchData(requestId)
                            println("Request $requestId: $result")
                        } catch (e: Exception) {
                            println("Request $requestId failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

suspend fun main() {
    val client = Problem.UnThrottledClient(Problem.ApiService())
    client.processRequests()
}