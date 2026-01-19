package concurrent.activeobject.imagedownloader

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/**
 * 이미지 다운로드 시스템 - Active Object 패턴 적용
 *
 * Active Object 패턴의 장점:
 * - 메서드 호출과 실행의 분리 (비동기 처리)
 * - 클라이언트는 블로킹 없이 요청 후 다른 작업 수행 가능
 * - 스케줄러가 요청을 효율적으로 관리
 * - 스레드 안전한 접근 (직렬화된 실행)
 * - Future를 통한 깔끔한 결과 처리
 * - 취소 및 타임아웃 지원
 */
class Solution {

    // 이미지 데이터 클래스
    data class Image(
        val url: String,
        val data: ByteArray,
        val size: Int,
        val downloadTime: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return url == other.url
        }

        override fun hashCode(): Int = url.hashCode()

        override fun toString(): String {
            return "Image(url='$url', size=${size}bytes, downloadTime=${downloadTime}ms)"
        }
    }

    // ===== Method Request: 메서드 호출을 캡슐화 =====
    sealed class MethodRequest {
        abstract val id: Int
        abstract suspend fun execute(servant: ImageDownloaderServant): Any
    }

    class DownloadRequest(
        override val id: Int,
        private val url: String,
        private val result: CompletableDeferred<Image>
    ) : MethodRequest() {
        override suspend fun execute(servant: ImageDownloaderServant): Image {
            return try {
                val image = servant.downloadImage(url)
                result.complete(image)
                image
            } catch (e: Exception) {
                result.completeExceptionally(e)
                throw e
            }
        }
    }

    class ResizeRequest(
        override val id: Int,
        private val image: Image,
        private val width: Int,
        private val height: Int,
        private val result: CompletableDeferred<Image>
    ) : MethodRequest() {
        override suspend fun execute(servant: ImageDownloaderServant): Image {
            return try {
                val resized = servant.resizeImage(image, width, height)
                result.complete(resized)
                resized
            } catch (e: Exception) {
                result.completeExceptionally(e)
                throw e
            }
        }
    }

    class DownloadAndResizeRequest(
        override val id: Int,
        private val url: String,
        private val width: Int,
        private val height: Int,
        private val result: CompletableDeferred<Image>
    ) : MethodRequest() {
        override suspend fun execute(servant: ImageDownloaderServant): Image {
            return try {
                val image = servant.downloadImage(url)
                val resized = servant.resizeImage(image, width, height)
                result.complete(resized)
                resized
            } catch (e: Exception) {
                result.completeExceptionally(e)
                throw e
            }
        }
    }

    // ===== Servant: 실제 작업을 수행하는 객체 =====
    class ImageDownloaderServant {

        suspend fun downloadImage(url: String): Image {
            println("[${Thread.currentThread().name}] Servant: 다운로드 시작 - $url")
            val startTime = System.currentTimeMillis()

            // 네트워크 다운로드 시뮬레이션
            val downloadTime = simulateNetworkDelay(url)
            delay(downloadTime)

            val imageData = "fake_image_data_for_$url".toByteArray()
            val elapsed = System.currentTimeMillis() - startTime

            println("[${Thread.currentThread().name}] Servant: 다운로드 완료 - $url (${elapsed}ms)")

            return Image(url, imageData, imageData.size, elapsed)
        }

        suspend fun resizeImage(image: Image, width: Int, height: Int): Image {
            println("[${Thread.currentThread().name}] Servant: 리사이즈 시작 - ${image.url}")

            // 이미지 처리 시뮬레이션
            delay(100)

            val resizedData = "resized_${width}x${height}_${image.url}".toByteArray()
            println("[${Thread.currentThread().name}] Servant: 리사이즈 완료 - ${image.url}")

            return image.copy(data = resizedData, size = resizedData.size)
        }

        private fun simulateNetworkDelay(url: String): Long {
            return when {
                url.contains("large") -> 800L
                url.contains("medium") -> 500L
                else -> 300L
            }
        }
    }

    // ===== Scheduler: Activation Queue에서 요청을 꺼내 실행 =====
    class Scheduler(
        private val servant: ImageDownloaderServant,
        private val concurrency: Int = 4
    ) {
        private val activationQueue = Channel<MethodRequest>(Channel.UNLIMITED)
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val activeRequests = AtomicInteger(0)
        private var isRunning = false

        fun start() {
            isRunning = true
            println("Scheduler: 시작 (동시성: $concurrency)")

            // 여러 워커 코루틴 시작
            repeat(concurrency) { workerId ->
                scope.launch {
                    while (isRunning) {
                        try {
                            val request = activationQueue.receive()
                            activeRequests.incrementAndGet()
                            println("Scheduler: 요청 #${request.id} 실행 시작 (Worker-$workerId)")

                            try {
                                request.execute(servant)
                                println("Scheduler: 요청 #${request.id} 실행 완료")
                            } catch (e: CancellationException) {
                                println("Scheduler: 요청 #${request.id} 취소됨")
                            } catch (e: Exception) {
                                println("Scheduler: 요청 #${request.id} 실패 - ${e.message}")
                            } finally {
                                activeRequests.decrementAndGet()
                            }
                        } catch (e: CancellationException) {
                            break
                        }
                    }
                }
            }
        }

        suspend fun enqueue(request: MethodRequest) {
            activationQueue.send(request)
            println("Scheduler: 요청 #${request.id} 큐에 추가됨")
        }

        fun stop() {
            isRunning = false
            scope.cancel()
            activationQueue.close()
            println("Scheduler: 종료됨")
        }

        fun getActiveRequestCount(): Int = activeRequests.get()
    }

    // ===== Proxy: 클라이언트가 사용하는 인터페이스 =====
    class ImageDownloaderProxy(
        private val scheduler: Scheduler
    ) {
        private val requestIdCounter = AtomicInteger(0)

        // 비동기 다운로드 - Future 반환
        suspend fun downloadAsync(url: String): Deferred<Image> {
            val result = CompletableDeferred<Image>()
            val request = DownloadRequest(
                id = requestIdCounter.incrementAndGet(),
                url = url,
                result = result
            )
            scheduler.enqueue(request)
            return result
        }

        // 비동기 리사이즈 - Future 반환
        suspend fun resizeAsync(image: Image, width: Int, height: Int): Deferred<Image> {
            val result = CompletableDeferred<Image>()
            val request = ResizeRequest(
                id = requestIdCounter.incrementAndGet(),
                image = image,
                width = width,
                height = height,
                result = result
            )
            scheduler.enqueue(request)
            return result
        }

        // 비동기 다운로드 + 리사이즈 - Future 반환
        suspend fun downloadAndResizeAsync(
            url: String,
            width: Int,
            height: Int
        ): Deferred<Image> {
            val result = CompletableDeferred<Image>()
            val request = DownloadAndResizeRequest(
                id = requestIdCounter.incrementAndGet(),
                url = url,
                width = width,
                height = height,
                result = result
            )
            scheduler.enqueue(request)
            return result
        }

        // 여러 이미지 병렬 다운로드
        suspend fun downloadAllAsync(urls: List<String>): List<Deferred<Image>> {
            return urls.map { url -> downloadAsync(url) }
        }

        // 타임아웃 지원 다운로드
        suspend fun downloadWithTimeout(url: String, timeoutMs: Long): Image? {
            val deferred = downloadAsync(url)
            return try {
                withTimeout(timeoutMs) {
                    deferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                println("다운로드 타임아웃: $url")
                deferred.cancel()
                null
            }
        }
    }

    // ===== Active Object: 모든 구성요소를 조합 =====
    class ActiveImageDownloader(concurrency: Int = 4) {
        private val servant = ImageDownloaderServant()
        private val scheduler = Scheduler(servant, concurrency)
        val proxy = ImageDownloaderProxy(scheduler)

        fun start() {
            scheduler.start()
        }

        fun stop() {
            scheduler.stop()
        }
    }
}

fun main(): Unit = runBlocking {
    val activeDownloader = Solution.ActiveImageDownloader(concurrency = 4)
    activeDownloader.start()

    val proxy = activeDownloader.proxy

    val urls = listOf(
        "https://example.com/small_image1.jpg",
        "https://example.com/medium_image2.jpg",
        "https://example.com/large_image3.jpg",
        "https://example.com/small_image4.jpg",
        "https://example.com/medium_image5.jpg"
    )

    println("\n========== 1. 단일 이미지 비동기 다운로드 ==========")
    val singleStart = System.currentTimeMillis()

    // 비동기로 요청 후 즉시 반환
    val future = proxy.downloadAsync(urls[0])
    println("요청 후 다른 작업 수행 가능...")

    // 필요할 때 결과 대기
    val image = future.await()
    println("다운로드 완료: $image")
    println("소요 시간: ${System.currentTimeMillis() - singleStart}ms")

    println("\n========== 2. 여러 이미지 병렬 다운로드 ==========")
    val parallelStart = System.currentTimeMillis()

    // 모든 요청을 비동기로 제출
    val futures = proxy.downloadAllAsync(urls)
    println("${futures.size}개 요청 제출 완료 - 다른 작업 수행 가능...")

    // 모든 결과 대기
    val images = futures.map { it.await() }
    val parallelTime = System.currentTimeMillis() - parallelStart

    println("병렬 다운로드 완료: ${images.size}개")
    println("총 소요 시간: ${parallelTime}ms")

    println("\n========== 3. 다운로드 + 리사이즈 체이닝 ==========")
    val chainStart = System.currentTimeMillis()

    val processedFuture = proxy.downloadAndResizeAsync(
        url = "https://example.com/chain_image.jpg",
        width = 800,
        height = 600
    )

    val processedImage = processedFuture.await()
    println("처리 완료: $processedImage")
    println("소요 시간: ${System.currentTimeMillis() - chainStart}ms")

    println("\n========== 4. 타임아웃 처리 ==========")
    val timeoutResult = proxy.downloadWithTimeout(
        url = "https://example.com/large_timeout_image.jpg",
        timeoutMs = 200 // 매우 짧은 타임아웃
    )

    if (timeoutResult != null) {
        println("다운로드 성공: $timeoutResult")
    } else {
        println("타임아웃으로 인한 실패 처리됨")
    }

    println("\n========== 5. 병렬 처리 + 개별 결과 처리 ==========")
    val mixedStart = System.currentTimeMillis()

    // 병렬로 요청 제출
    val mixedFutures = listOf(
        proxy.downloadAsync("https://example.com/mixed1.jpg"),
        proxy.downloadAndResizeAsync("https://example.com/mixed2.jpg", 640, 480),
        proxy.downloadAsync("https://example.com/mixed3.jpg")
    )

    // 완료되는 순서대로 처리
    mixedFutures.forEachIndexed { index, deferred ->
        launch {
            val result = deferred.await()
            println("작업 #${index + 1} 완료: ${result.url}")
        }
    }

    // 모든 작업 완료 대기
    mixedFutures.forEach { it.await() }
    println("모든 혼합 작업 완료: ${System.currentTimeMillis() - mixedStart}ms")

    println("\n========== Active Object 패턴의 장점 ==========")
    println("1. 비동기 호출: 요청 후 즉시 반환, 블로킹 없음")
    println("2. Future 패턴: 깔끔한 결과 처리 및 예외 처리")
    println("3. 스케줄러: 효율적인 요청 관리 및 동시성 제어")
    println("4. 타임아웃 지원: 쉬운 타임아웃 및 취소 처리")
    println("5. 스레드 안전: 내부적으로 직렬화된 실행")
    println("6. 확장성: 동시성 수준 조절 가능")

    // 정리
    delay(500)
    activeDownloader.stop()
    println("\n시스템 종료")
}
