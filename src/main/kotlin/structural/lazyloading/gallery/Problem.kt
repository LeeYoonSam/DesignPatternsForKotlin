package structural.lazyloading.gallery

import java.awt.image.BufferedImage

/**
 * 이미지 갤러리 애플리케이션에서 발생하는 성능 문제
 *
 * 문제점:
 * 1. 애플리케이션 시작 시 모든 이미지를 로드
 * 2. 큰 이미지 파일을 모두 메모리에 로드하면 시작 지연 및 메모리 사용량 증가
 * 3. 사용자가 실제로 보지 않는 이미지도 모두 로드
 */
class Problem {
    class Image(val id: Int, val name: String, val path: String) {
        // 실제 이미지 데이터
        val imageData: BufferedImage

        init {
            println("로딩 중: $name ($path)")
            // 이미지 로딩에 시간이 걸린다고 가정
            Thread.sleep(500)

            // 실제 이미지 파일이 없으므로 더미 이미지 생성
            imageData = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            println("로딩 완료: $name")
        }

        fun display() {
            println("이미지 표시: $name (크기: ${imageData.width}x${imageData.height})")
        }
    }

    // 이미지 갤러리 관리 클래스
    class ImageGallery {
        private val images = mutableListOf<Image>()

        fun loadImages() {
            // 모든 이미지를 한 번에 로드
            val startTime = System.currentTimeMillis()
            println("갤러리 초기화 시작...")

            for (i in 1..10) {
                val image = Image(i, "이미지 $i", "path/to/image$i.jpg")
                images.add(image)
            }

            val endTime = System.currentTimeMillis()
            println("갤러리 초기화 완료: ${(endTime - startTime) / 1000.0}초 소요")
        }

        fun showImage(id: Int) {
            val image = images.find { it.id == id }
            image?.display() ?: println("이미지를 찾을 수 없습니다: ID $id")
        }

        fun showGalleryInfo() {
            println("갤러리에 ${images.size}개의 이미지가 있습니다.")
            println("사용 가능한 이미지:")
            images.forEach { println(" - ${it.id}: ${it.name}") }
        }
    }
}

fun main() {
    println("애플리케이션 시작")

    val gallery = Problem.ImageGallery()

    // 사용자가 아직 어떤 이미지를 볼지 결정하지 않았지만
    // 모든 이미지가 미리 로드됨
    println("이미지 갤러리 초기화 중...")
    gallery.loadImages()

    println("\n갤러리 정보 표시:")
    gallery.showGalleryInfo()

    // 사용자가 실제로는 2개의 이미지만 봄
    println("\n사용자 상호작용 시뮬레이션:")
    gallery.showImage(3)
    Thread.sleep(1000) // 사용자가 다른 작업 중
    gallery.showImage(7)

    println("\n애플리케이션 종료")
}