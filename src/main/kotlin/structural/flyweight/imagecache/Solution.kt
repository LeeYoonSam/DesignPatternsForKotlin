package structural.flyweight.imagecache

import kotlinx.coroutines.runBlocking
import java.util.Date
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 플라이웨이트 패턴을 적용한 이미지 구현
 */
class Solution {
    class ImageImpl(
        override val url: String,
        override val width: Int,
        override val height: Int
    ): Image {
        // 이미지 로딩을 시뮬레이션
        private val imageData: ByteArray = runBlocking { loadImageFromUrl(url) }
        private val loadedAt: Date = Date()

        init {
            log("새 이미지 객체 생성: $url ($width x $height)")
        }

        override fun display() {
            log("이미지 표시 중: $url ($width x $height)")
        }

        override fun getMetadata(): String {
            return "URL: $url, 크기: ${width}x${height}, 로드 시간: $loadedAt"
        }
    }

    /**
     * 플라이웨이트 팩토리 역할을 하는 이미지 캐시 구현
     */
    class ImageCache {
        private val cache = ConcurrentHashMap<String, Image>()

        fun getImage(url: String, width: Int, height: Int): Image {
            // 캐시 키 생성 (URL, 너비, 높이를 조합)
            val cacheKey = "$url-$width-$height"

            // 캐시에서 이미지 조회
            return cache.computeIfAbsent(cacheKey) {
                log("캐시 미스: $url")
                ImageImpl(url, width, height)
            }.also {
                log("캐시 히트: $url")
            }
        }

        fun getCacheSize(): Int = cache.size

        fun clearCache() {
            cache.clear()
            log("캐시 비움")
        }
    }

    /**
     * 플라이웨이트 패턴을 사용하여 이미지를 로드하고 렌더링하는 클래스
     */
    class ImageRenderer(private val imageCache: ImageCache) {
        fun loadAndRenderImage(url: String, width: Int, height: Int) {
            val image = imageCache.getImage(url, width, height)
            image.display()
        }
    }

    /**
     * LRU(Least Recently Used) 캐시 정책을 구현한 확장 이미지 캐시
     */
    class LRUImageCache(private val maxSize: Int) {
        private val cache = object : LinkedHashMap<String, Image>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Image>?): Boolean {
                val tooMany = size > maxSize
                if (tooMany) {
                    log("LRU 캐시: 가장 오래된 항목 제거 - ${eldest?.key}")
                }
                return tooMany
            }
        }

        @Synchronized
        fun getImage(url: String, width: Int, height: Int): Image {
            val cacheKey = "$url-$width-$height"

            return cache[cacheKey] ?: run {
                log("LRU 캐시 미스: $url")
                ImageImpl(url, width, height).also {
                    cache[cacheKey] = it
                }
            }.also {
                log("LRU 캐시 히트 또는 저장 완료: $url")
            }
        }

        fun getCacheSize(): Int = cache.size

        @Synchronized
        fun clearCache() {
            cache.clear()
            log("LRU 캐시 비움")
        }
    }
}

/**
 * 해결책을 보여주는 메인 함수
 */
fun main() {
    log("이미지 로딩 시작 - 플라이웨이트 패턴 적용")
    displayMemoryUsage()

    val imageCache = Solution.ImageCache()
    val renderer = Solution.ImageRenderer(imageCache)

    val imageUrls = listOf(
        "https://example.com/profile1.jpg",
        "https://example.com/profile2.jpg",
        "https://example.com/profile1.jpg",  // 중복된 URL - 캐시에서 로드됨
        "https://example.com/banner.jpg",
        "https://example.com/profile2.jpg",  // 중복된 URL - 캐시에서 로드됨
        "https://example.com/profile1.jpg"   // 중복된 URL - 캐시에서 로드됨
    )

    // 이미지 렌더링
    for (url in imageUrls) {
        renderer.loadAndRenderImage(url, 100, 100)
    }

    log("캐시 크기: ${imageCache.getCacheSize()}")
    displayMemoryUsage()

    log("\n고급 사용 사례: LRU 캐시 정책 적용")
    val lruCache = Solution.LRUImageCache(maxSize = 2)  // 캐시 크기를 2로 제한

    log("\n첫 번째 이미지 로드")
    lruCache.getImage("https://example.com/img1.jpg", 100, 100).display()

    log("\n두 번째 이미지 로드")
    lruCache.getImage("https://example.com/img2.jpg", 100, 100).display()

    log("\n첫 번째 이미지 다시 로드 (캐시에서)")
    lruCache.getImage("https://example.com/img1.jpg", 100, 100).display()

    log("\n세 번째 이미지 로드 (캐시 크기 초과로 두 번째 이미지가 제거됨)")
    lruCache.getImage("https://example.com/img3.jpg", 100, 100).display()

    log("\n두 번째 이미지 다시 로드 (제거되었으므로 새로 로드됨)")
    lruCache.getImage("https://example.com/img2.jpg", 100, 100).display()

    log("\n플라이웨이트 패턴의 이점:")
    log("1. 중복 네트워크 요청 감소: 같은 이미지는 한 번만 로드됩니다.")
    log("2. 메모리 사용량 최적화: 동일한 이미지의 여러 복사본 대신 하나의 인스턴스를 공유합니다.")
    log("3. 로딩 시간 단축: 이미 로드된 이미지는 즉시 사용 가능합니다.")
    log("4. 확장 가능한 캐싱 정책: LRU와 같은 다양한 캐싱 전략을 적용할 수 있습니다.")
}