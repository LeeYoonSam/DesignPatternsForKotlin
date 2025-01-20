package structural.cache.expensive

/**
 * 문제점
 * - 반복적인 데이터 접근으로 인한 성능 저하
 * - 비용이 많이 드는 연산의 중복 실행
 * - 외부 서비스 의존성으로 인한 지연
 * - 메모리 사용량 관리의 어려움
 * - 데이터 일관성 유지의 어려움
 */
class Problem {
    class ExpensiveDataService {
        fun fetchData(id: String): Data {
            // 데이터베이스 조회 시뮬레이션
            Thread.sleep(1000)
            return Data(id, "Data for $id", System.currentTimeMillis())
        }

        fun processData(data: Data): ProcessedResult {
            // 복잡한 계산 시뮬레이션
            Thread.sleep(500)
            return ProcessedResult(data.id, "Processed ${data.content}")
        }
    }

    data class Data(val id: String, val content: String, val timestamp: Long)
    data class ProcessedResult(val id: String, val result: String)

    // 캐시 없는 데이터 처리
    class DataProcessor(private val service: ExpensiveDataService) {
        fun getProcessedData(id: String): ProcessedResult {
            val data = service.fetchData(id) // 매번 데이터베이스 조회
            return service.processData(data) // 매번 계산 수행
        }
    }
}

fun main() {
    val originalService = Problem.ExpensiveDataService()

    // 캐시 없는 처리
    println("Without Cache:")
    val processor = Problem.DataProcessor(originalService)
    val startTime1 = System.currentTimeMillis()
    repeat(3) {
        processor.getProcessedData("test")
    }
    println("Total time without cache: ${System.currentTimeMillis() - startTime1}ms\n")
}