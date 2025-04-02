package structural.flyweight.imagecache

import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * 플라이웨이트 패턴을 적용하지 않은 이미지 구현
 */
class ImageWithoutCaching(
    override val url: String,
    override val width: Int,
    override val height: Int
) : Image {
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
 * 플라이웨이트 패턴 없이 이미지를 로드하고 렌더링하는 클래스
 */
class ImageRendererWithoutCaching {
    fun loadAndRenderImage(url: String, width: Int, height: Int) {
        val image = ImageWithoutCaching(url, width, height)
        image.display()
    }
}

/**
 * 문제점을 보여주는 메인 함수
 */
fun main() {
    log("이미지 로딩 시작 - 플라이웨이트 패턴 미적용")
    displayMemoryUsage()

    val renderer = ImageRendererWithoutCaching()
    val imageUrls = listOf(
        "https://example.com/profile1.jpg",
        "https://example.com/profile2.jpg",
        "https://example.com/profile1.jpg",  // 중복된 URL
        "https://example.com/banner.jpg",
        "https://example.com/profile2.jpg",  // 중복된 URL
        "https://example.com/profile1.jpg"   // 중복된 URL
    )

    // 이미지 렌더링
    for (url in imageUrls) {
        renderer.loadAndRenderImage(url, 100, 100)
    }

    displayMemoryUsage()
    log("총 6개의 이미지를 로드했으며, 그 중 3개는 중복된 이미지")
    log("플라이웨이트 패턴 미적용 시 모든 이미지를 매번 새로 로드하므로 네트워크 요청과 메모리 사용량이 증가합니다.")
}