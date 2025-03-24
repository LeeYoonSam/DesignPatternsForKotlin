package structural.flyweight.gameobjects

import kotlin.random.Random

// 2D 포인트 클래스
data class Point2D(val x: Float, val y: Float)

// 모든 게임 객체의 기본 인터페이스
interface GameObject {
    fun render(position: Point2D, scale: Float, rotation: Float)
}

// 2D 메쉬 - 형태를 결정하는 정점 집합
class Mesh(val vertices: List<Point2D>) {
    // 메모리를 차지하는 메쉬 데이터
    val meshData = ByteArray(1024 * 10) // 10KB의 메쉬 데이터라고 가정

    init {
        // 메쉬 데이터 초기화 (실제로는 더 복잡)
        Random.nextBytes(meshData)
    }
}

// 텍스처 - 객체의 외형
class Texture(val name: String) {
    // 메모리를 많이 차지하는 텍스처 데이터
    val textureData = ByteArray(1024 * 100) // 100KB의 텍스처 데이터라고 가정

    init {
        // 텍스처 데이터 초기화 (실제로는 이미지 로딩)
        Random.nextBytes(textureData)
        println("$name 텍스처 로딩 완료 (100KB)")
    }
}