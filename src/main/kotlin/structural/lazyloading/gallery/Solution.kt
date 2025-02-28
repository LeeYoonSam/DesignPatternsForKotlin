package structural.lazyloading.gallery

import java.awt.image.BufferedImage
import kotlin.properties.Delegates

/**
 * 지연 로딩 패턴을 사용한 이미지 갤러리 애플리케이션
 *
 * 해결책:
 * 1. 이미지 메타데이터만 미리 로드
 * 2. 실제 이미지 데이터는 사용자가 요청할 때만 로드
 * 3. Kotlin의 위임 속성(delegated properties)을 활용한 지연 초기화
 */
class Solution {
    // 이미지 메타데이터
    data class ImageInfo(val id: Int, val name: String, val path: String)

    // 지연 로딩을 위한 프록시 클래스
    class LazyImage(private val imageInfo: ImageInfo) {
        // 실제 이미지 데이터를 지연 로딩하기 위한 lazy 위임 속성
        private val imageData: BufferedImage by lazy {
            loadImageData()
        }

        // 이미지 로딩 여부를 확인하기 위한 플래그
        private var isLoaded = false

        private fun loadImageData(): BufferedImage {
            println("로딩 중: ${imageInfo.name} (${imageInfo.path})")
            // 이미지 로딩에 시간이 걸린다고 가정
            Thread.sleep(500)

            // 실제 이미지 파일이 없으므로 더미 이미지 생성
            val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            println("로딩 완료: ${imageInfo.name}")
            isLoaded = true
            return image
        }

        fun display() {
            // 이미지를 표시할 때 실제 데이터에 접근하여 지연 로딩 트리거
            println("이미지 표시: ${imageInfo.name} (크기: ${imageData.width}x${imageData.height})")
        }

        fun isImageLoaded(): Boolean = isLoaded

        fun getInfo(): ImageInfo = imageInfo
    }

    // 지연 로딩을 지원하는 이미지 갤러리
    class LazyImageGallery {
        // 이미지 메타데이터와 프록시 객체 맵
        private val imageProxies = mutableListOf<LazyImage>()

        fun loadImageCatalog() {
            // 이미지 메타데이터만 빠르게 로드
            val startTime = System.currentTimeMillis()
            println("이미지 카탈로그 초기화 시작...")

            for (i in 1..10) {
                val imageInfo = ImageInfo(i, "이미지 $i", "path/to/image$i.jpg")
                imageProxies.add(LazyImage(imageInfo))
            }

            val endTime = System.currentTimeMillis()
            println("이미지 카탈로그 초기화 완료: ${(endTime - startTime) / 1000.0}초 소요")
        }

        fun showImage(id: Int) {
            val image = imageProxies.find { it.getInfo().id == id }
            if (image != null) {
                // 이미지를 표시할 때만 실제 이미지 데이터 로드
                image.display()
            } else {
                println("이미지를 찾을 수 없습니다: ID $id")
            }
        }

        fun showGalleryInfo() {
            println("갤러리에 ${imageProxies.size}개의 이미지가 있습니다.")
            println("사용 가능한 이미지:")
            imageProxies.forEach {
                val info = it.getInfo()
                val loadStatus = if (it.isImageLoaded()) "로드됨" else "로드되지 않음"
                println(" - ${info.id}: ${info.name} ($loadStatus)")
            }
        }

        fun getLoadedImagesCount(): Int {
            return imageProxies.count { it.isImageLoaded() }
        }
    }

    // 코틀린의 by lazy를 활용한 또 다른 구현 예제
    class LazyLoadingDemo {
        // Kotlin의 by lazy 위임을 사용한 지연 로딩
        val expensiveResource: String by lazy {
            println("비용이 많이 드는 리소스 초기화 중...")
            Thread.sleep(1000)
            "초기화된 비용이 많이 드는 리소스"
        }

        // Kotlin의 위임 속성을 사용한 커스텀 지연 로딩
        var dynamicResource: String by Delegates.observable("초기값") { _, old, new ->
            println("값 변경: $old -> $new")
        }

        fun performTask() {
            // 이 메서드가 호출될 때만 expensiveResource 초기화
            println("작업 수행 중...")
            println("리소스 사용: $expensiveResource")
        }
    }
}

fun main() {
    println("최적화된 애플리케이션 시작")

    val gallery = Solution.LazyImageGallery()

    // 이미지 메타데이터만 빠르게 로드
    println("이미지 갤러리 카탈로그 초기화 중...")
    gallery.loadImageCatalog()

    println("\n갤러리 정보 표시:")
    gallery.showGalleryInfo()
    println("현재 로드된 이미지 수: ${gallery.getLoadedImagesCount()}")

    // 사용자가 실제로 요청한 이미지만 로드
    println("\n사용자 상호작용 시뮬레이션:")
    gallery.showImage(3)
    Thread.sleep(1000) // 사용자가 다른 작업 중
    gallery.showImage(7)

    println("\n갤러리 상태 확인:")
    gallery.showGalleryInfo()
    println("현재 로드된 이미지 수: ${gallery.getLoadedImagesCount()}")

    println("\n다른 지연 로딩 예제:")
    val demo = Solution.LazyLoadingDemo()
    println("LazyLoadingDemo 인스턴스 생성됨 (아직 리소스 초기화 안됨)")

    demo.dynamicResource = "동적으로 변경된 값"

    demo.performTask() // 이때 expensiveResource 초기화
    demo.performTask() // 이미 초기화되어 있으므로 초기화 과정 없음

    println("\n애플리케이션 종료")
}