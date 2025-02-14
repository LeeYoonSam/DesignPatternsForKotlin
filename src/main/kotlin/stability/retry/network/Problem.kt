package stability.retry.network

import kotlinx.coroutines.delay
import kotlin.random.Random

class Problem {
    class NetworkService {
        private var failureCount = 0

        suspend fun fetchData(id: String): String {
            delay(100) // 네트워크 지연 시뮬레이션

            // 간헐적인 실패 시뮬레이션
            if (Random.nextInt(100) < 70) {
                failureCount++
                throw NetworkException("일시적인 네트워크 오류 (실패 횟수: $failureCount)")
            }

            return "Data for $id"
        }
    }

    class SimpleClient(private val service: NetworkService) {
        suspend fun fetchDataFromNetwork(id: String): String {
            return service.fetchData(id) // 재시도 없이 한 번만 시도
        }
    }

    class NetworkException(message: String) : Exception(message)
}

suspend fun main() {
    val client = Problem.SimpleClient(Problem.NetworkService())

    repeat(5) { index ->
        try {
            val result = client.fetchDataFromNetwork("request-$index")
            println("성공: $result")
        } catch (e: Problem.NetworkException) {
            println("실패: ${e.message}")
        }
    }
}