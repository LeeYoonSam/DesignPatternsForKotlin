package structural.flyweight.particlesystem

// 파티클이 가질 수 있는 타입 정의
enum class ParticleType {
    FIRE, SMOKE, EXPLOSION, SPARKLE, WATER
}

// 각 파티클이 가질 수 있는 색상 정의
enum class ParticleColor {
    RED, ORANGE, YELLOW, WHITE, GRAY, BLUE
}

// 파티클의 렌더링을 시뮬레이션하는 함수
fun renderParticle(
    x: Float,
    y: Float,
    type: ParticleType,
    color: ParticleColor,
    size: Float,
    texture: String
) {
    println("파티클 렌더링: 위치(${x}, ${y}), 타입: $type, 색상: $color, 크기: $size, 텍스처: $texture")
}

// 시스템 메모리 사용량을 시뮬레이션하는 유틸리티 함수
fun simulateMemoryUsage(objectCount: Int, bytesPerObject: Int): String {
    val totalBytes = objectCount * bytesPerObject
    return when {
        totalBytes < 1024 -> "$totalBytes bytes"
        totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
        else -> "${totalBytes / (1024 * 1024)} MB"
    }
}