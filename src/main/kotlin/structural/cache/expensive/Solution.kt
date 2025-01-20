package structural.cache.expensive

import structural.cache.expensive.Problem.Data
import structural.cache.expensive.Problem.ProcessedResult

/**
 * 해결책: 캐시 패턴을 사용한 효율적인 데이터 처리 시스템
 */
class Solution {
    // 캐시 엔트리 클래스
    data class CacheEntry<T>(
        val value: T,
        val timestamp: Long,
        val expirationTime: Long
    ) {
        fun isExpired() = System.currentTimeMillis() - timestamp > expirationTime
    }

    // 범용 캐시 인터페이스
    interface Cache<K, V> {
        fun get(key: K): V?
        fun put(key: K, value: V)
        fun remove(key: K)
        fun clear()
    }

    // LRU 캐시 구현
    class LRUCache<K, V>(
        private val capacity: Int,
        private val expirationTime: Long = 60000 // 기본 1분
    ): Cache<K, CacheEntry<V>> {
        private val cache = LinkedHashMap<K, CacheEntry<V>>(capacity, 0.75f, true)

        @Synchronized
        override fun get(key: K): CacheEntry<V>? {
            val entry = cache[key]
            if (entry != null && entry.isExpired()) {
                cache.remove(key)
                return null
            }
            return entry
        }

        @Synchronized
        override fun put(key: K, value: CacheEntry<V>) {
            if (cache.size >= capacity) {
                val firstKey = cache.keys.first()
                cache.remove(firstKey)
            }
            cache[key] = value
        }

        @Synchronized
        override fun remove(key: K) {
            cache.remove(key)
        }

        @Synchronized
        override fun clear() {
            cache.clear()
        }
    }

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

    // 캐시를 적용한 데이터 서비스
    class CachedDataService(
        private val originalService: ExpensiveDataService,
        private val dataCache: LRUCache<String, Data>,
        private val resultCache: LRUCache<String, ProcessedResult>
    ) {
        fun fetchData(id: String): Data {
            // 캐시에서 데이터 확인
            val cachedData = dataCache.get(id)?.value
            if (cachedData != null) {
                println("Cache hit for data: $id")
                return cachedData
            }

            // 캐시 미스: 원본 서비스에서 데이터 가져오기
            println("Cache miss for data: $id")
            val data = originalService.fetchData(id)
            dataCache.put(id, CacheEntry(data, System.currentTimeMillis(), 300000)) // 5분 캐시
            return data
        }

        fun processData(data: Data): ProcessedResult {
            // 캐시에서 결과 확인
            val cachedResult = resultCache.get(data.id)?.value
            if (cachedResult != null) {
                println("Cache hit for result: ${data.id}")
                return cachedResult
            }

            // 캐시 미스: 데이터 처리
            println("Cache miss for result: ${data.id}")
            val result = originalService.processData(data)
            resultCache.put(data.id, CacheEntry(result, System.currentTimeMillis(), 600000)) // 10분 캐시
            return result
        }
    }

    // 캐시를 사용하는 데이터 프로세서
    class CachedDataProcessor(private val cachedService: CachedDataService) {
        fun getProcessedData(id: String): ProcessedResult {
            val data = cachedService.fetchData(id)
            return cachedService.processData(data)
        }
    }
}

fun main() {
    val originalService = Solution.ExpensiveDataService()

    // 캐시를 사용한 처리
    println("With Cache:")
    val cachedService = Solution.CachedDataService(
        originalService,
        Solution.LRUCache(100),  // 데이터 캐시
        Solution.LRUCache(100)   // 결과 캐시
    )
    val cachedProcessor = Solution.CachedDataProcessor(cachedService)

    val startTime = System.currentTimeMillis()
    repeat(3) {
        cachedProcessor.getProcessedData("test")
    }
    println("Total time with cache: ${System.currentTimeMillis() - startTime}ms")
}