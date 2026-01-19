package concurrent.activeobject.imagedownloader

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * 이미지 다운로드 시스템 - Active Object 패턴 적용 전
 *
 * 문제점:
 * - 동기적 블로킹 호출로 인한 성능 저하
 * - 메인 스레드가 다운로드 완료까지 대기
 * - 여러 이미지 동시 다운로드 시 비효율적
 * - 스레드 직접 관리의 복잡성
 * - 결과 처리와 에러 핸들링이 복잡함
 * - 취소 및 타임아웃 처리가 어려움
 */
class Problem {

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

    // 문제가 있는 코드: 동기적 블로킹 이미지 다운로더
    class SyncImageDownloader {

        // 동기적으로 이미지 다운로드 - 블로킹
        fun downloadImage(url: String): Image {
            println("[${Thread.currentThread().name}] 다운로드 시작: $url")
            val startTime = System.currentTimeMillis()

            // 네트워크 다운로드 시뮬레이션 (블로킹)
            val downloadTime = simulateNetworkDelay(url)
            Thread.sleep(downloadTime)

            val imageData = "fake_image_data_for_$url".toByteArray()
            val elapsed = System.currentTimeMillis() - startTime

            println("[${Thread.currentThread().name}] 다운로드 완료: $url (${elapsed}ms)")

            return Image(url, imageData, imageData.size, elapsed)
        }

        // 여러 이미지 순차 다운로드 - 매우 느림
        fun downloadImages(urls: List<String>): List<Image> {
            println("\n=== 순차 다운로드 시작 ===")
            val startTime = System.currentTimeMillis()

            val images = urls.map { url ->
                downloadImage(url)
            }

            val totalTime = System.currentTimeMillis() - startTime
            println("=== 순차 다운로드 완료: 총 ${totalTime}ms ===\n")

            return images
        }

        // 수동 스레드 관리로 병렬 다운로드 - 복잡함
        fun downloadImagesParallel(urls: List<String>): List<Image> {
            println("\n=== 병렬 다운로드 시작 (수동 스레드 관리) ===")
            val startTime = System.currentTimeMillis()

            val results = mutableListOf<Image>()
            val errors = mutableListOf<Exception>()
            val latch = CountDownLatch(urls.size)
            val lock = Object()

            // 각 URL에 대해 별도 스레드 생성 - 리소스 낭비 가능
            val threads = urls.map { url ->
                thread {
                    try {
                        val image = downloadImage(url)
                        synchronized(lock) {
                            results.add(image)
                        }
                    } catch (e: Exception) {
                        synchronized(lock) {
                            errors.add(e)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // 모든 스레드 완료 대기 - 블로킹
            latch.await()

            val totalTime = System.currentTimeMillis() - startTime
            println("=== 병렬 다운로드 완료: 총 ${totalTime}ms ===")

            if (errors.isNotEmpty()) {
                println("발생한 오류: ${errors.size}개")
                errors.forEach { println("  - ${it.message}") }
            }

            return results
        }

        // 이미지 처리 - 리사이즈
        fun resizeImage(image: Image, width: Int, height: Int): Image {
            println("[${Thread.currentThread().name}] 리사이즈 시작: ${image.url}")

            // 이미지 처리 시뮬레이션 (CPU 작업)
            Thread.sleep(100)

            val resizedData = "resized_${width}x${height}_${image.url}".toByteArray()
            println("[${Thread.currentThread().name}] 리사이즈 완료: ${image.url}")

            return image.copy(data = resizedData, size = resizedData.size)
        }

        // 다운로드 후 처리 - 콜백 지옥 발생 가능
        fun downloadAndProcess(
            url: String,
            onSuccess: (Image) -> Unit,
            onError: (Exception) -> Unit
        ) {
            thread {
                try {
                    val image = downloadImage(url)
                    val resized = resizeImage(image, 800, 600)
                    onSuccess(resized)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }

        private fun simulateNetworkDelay(url: String): Long {
            // URL에 따라 다른 다운로드 시간 시뮬레이션
            return when {
                url.contains("large") -> 800L
                url.contains("medium") -> 500L
                else -> 300L
            }
        }
    }
}

fun main() {
    val downloader = Problem.SyncImageDownloader()

    val urls = listOf(
        "https://example.com/small_image1.jpg",
        "https://example.com/medium_image2.jpg",
        "https://example.com/large_image3.jpg",
        "https://example.com/small_image4.jpg",
        "https://example.com/medium_image5.jpg"
    )

    // 1. 순차 다운로드 - 매우 느림
    println("========== 1. 순차 다운로드 ==========")
    val sequentialStart = System.currentTimeMillis()
    val sequentialImages = downloader.downloadImages(urls)
    val sequentialTime = System.currentTimeMillis() - sequentialStart
    println("순차 다운로드 총 소요 시간: ${sequentialTime}ms")
    println("다운로드된 이미지: ${sequentialImages.size}개")

    println()

    // 2. 병렬 다운로드 (수동 스레드 관리) - 복잡하고 위험함
    println("========== 2. 병렬 다운로드 (수동 스레드) ==========")
    val parallelStart = System.currentTimeMillis()
    val parallelImages = downloader.downloadImagesParallel(urls)
    val parallelTime = System.currentTimeMillis() - parallelStart
    println("병렬 다운로드 총 소요 시간: ${parallelTime}ms")
    println("다운로드된 이미지: ${parallelImages.size}개")

    println()

    // 3. 콜백 기반 비동기 처리 - 콜백 지옥
    println("========== 3. 콜백 기반 비동기 처리 ==========")
    val latch = CountDownLatch(1)

    downloader.downloadAndProcess(
        url = "https://example.com/callback_image.jpg",
        onSuccess = { image ->
            println("콜백 성공: $image")
            latch.countDown()
        },
        onError = { error ->
            println("콜백 실패: ${error.message}")
            latch.countDown()
        }
    )

    latch.await()

    println()
    println("========== 문제점 요약 ==========")
    println("1. 순차 다운로드: ${sequentialTime}ms (모든 이미지를 순서대로 대기)")
    println("2. 병렬 다운로드: ${parallelTime}ms (스레드 직접 관리 필요)")
    println("3. 콜백 방식: 콜백 지옥, 에러 처리 복잡")
    println("4. 취소/타임아웃 처리가 어려움")
    println("5. 스레드 풀 관리가 없어 리소스 낭비 가능")
}
