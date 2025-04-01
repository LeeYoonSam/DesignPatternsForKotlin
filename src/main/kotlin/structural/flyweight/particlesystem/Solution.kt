package structural.flyweight.particlesystem

import kotlin.random.Random

// 플라이웨이트 객체: 공유 가능한 내부 상태를 포함
class ParticleTypeInfo(
    val color: ParticleColor,
    val texture: String,
    val behaviorPattern: String,
    val renderingDetails: Map<String, Any>
) {
    // 추가적인 공유 상태를 여기에 포함할 수 있음

    // 디버깅을 위한 메모리 사용량 추정
    fun estimatedSize(): Int {
        // 실제로는 더 복잡한 계산이 필요하지만 예시로 단순화
        return texture.length * 2 + // 텍스처 경로
                behaviorPattern.length * 2 + // 행동 패턴
                renderingDetails.size * 100 // 렌더링 세부 사항
    }
}

// 플라이웨이트 팩토리: 플라이웨이트 객체를 생성하고 캐시
class ParticleTypeFactory {
    private val particleTypes = mutableMapOf<ParticleType, ParticleTypeInfo>()

    // 플라이웨이트 객체 가져오기 (없으면 생성)
    fun getParticleType(type: ParticleType): ParticleTypeInfo {
        return particleTypes.getOrPut(type) {
            when (type) {
                ParticleType.FIRE -> ParticleTypeInfo(
                    ParticleColor.RED,
                    "textures/fire_particle.png",
                    "rising_fading",
                    mapOf(
                        "glow" to true,
                        "blendMode" to "additive",
                        "shader" to "fire_shader_v2",
                        "intensity" to 0.8f
                    )
                )
                ParticleType.SMOKE -> ParticleTypeInfo(
                    ParticleColor.GRAY,
                    "textures/smoke_particle.png",
                    "rising_spreading",
                    mapOf(
                        "opacity" to 0.6f,
                        "blendMode" to "normal",
                        "shader" to "smoke_shader_v1",
                        "intensity" to 0.5f
                    )
                )
                ParticleType.EXPLOSION -> ParticleTypeInfo(
                    ParticleColor.ORANGE,
                    "textures/explosion_particle.png",
                    "expanding_fading",
                    mapOf(
                        "glow" to true,
                        "blendMode" to "additive",
                        "shader" to "explosion_shader_v3",
                        "intensity" to 1.0f
                    )
                )
                ParticleType.SPARKLE -> ParticleTypeInfo(
                    ParticleColor.YELLOW,
                    "textures/sparkle_particle.png",
                    "twinkling_falling",
                    mapOf(
                        "glow" to true,
                        "blendMode" to "screen",
                        "shader" to "sparkle_shader_v1",
                        "intensity" to 0.9f
                    )
                )
                ParticleType.WATER -> ParticleTypeInfo(
                    ParticleColor.BLUE,
                    "textures/water_particle.png",
                    "falling_splashing",
                    mapOf(
                        "refraction" to 1.33f,
                        "blendMode" to "normal",
                        "shader" to "water_shader_v2",
                        "intensity" to 0.7f
                    )
                )
            }
        }
    }

    // 캐시된 플라이웨이트 객체 수
    fun getCachedTypesCount(): Int = particleTypes.size

    // 공유 상태가 차지하는 메모리 추정
    fun estimateSharedMemoryUsage(): String {
        val totalBytes = particleTypes.values.sumOf { it.estimatedSize() }
        return when {
            totalBytes < 1024 -> "$totalBytes bytes"
            totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
            else -> "${totalBytes / (1024 * 1024)} MB"
        }
    }
}

// 최적화된 파티클 클래스: 외부 상태만 저장
class OptimizedParticle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var lifespan: Int,
    var size: Float,
    private val particleTypeInfo: ParticleTypeInfo, // 참조만 저장
    private val type: ParticleType // enum 값은 작기 때문에 저장
) {
    var currentLife: Int = lifespan

    fun update() {
        x += velocityX
        y += velocityY
        currentLife--
    }

    fun render() {
        renderParticle(
            x, y,
            type,
            particleTypeInfo.color,
            size,
            particleTypeInfo.texture
        )
    }

    fun isAlive(): Boolean = currentLife > 0
}

// 최적화된 파티클 시스템
class OptimizedParticleSystem(private val maxParticles: Int) {
    private val particles = mutableListOf<OptimizedParticle>()
    private val particleTypeFactory = ParticleTypeFactory()
    private val random = Random(System.currentTimeMillis())

    // 새로운 파티클 생성
    fun createParticle(type: ParticleType) {
        if (particles.size >= maxParticles) return

        val particleTypeInfo = particleTypeFactory.getParticleType(type)

        val x = random.nextFloat() * 800
        val y = random.nextFloat() * 600
        val velocityX = (random.nextFloat() - 0.5f) * 10
        val velocityY = (random.nextFloat() - 0.5f) * 10
        val lifespan = random.nextInt(50, 200)
        val size = random.nextFloat() * 5 + 1

        val particle = OptimizedParticle(
            x, y, velocityX, velocityY, lifespan, size,
            particleTypeInfo, type
        )
        particles.add(particle)
    }

    // 모든 파티클 업데이트
    fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.update()
            if (!particle.isAlive()) {
                iterator.remove()
            }
        }
    }

    // 모든 파티클 렌더링
    fun renderParticles() {
        particles.forEach { it.render() }
    }

    // 현재 파티클 수 반환
    fun getParticleCount(): Int = particles.size

    // 메모리 사용량 추정 (단순화된 계산)
    fun estimateMemoryUsage(): String {
        // 각 파티클 객체가 약 60바이트를 사용한다고 가정 (공유 상태 제외)
        return simulateMemoryUsage(particles.size, 60)
    }

    // 공유 상태가 차지하는 메모리
    fun getSharedMemoryUsage(): String {
        return particleTypeFactory.estimateSharedMemoryUsage()
    }

    // 총 메모리 사용량 추정
    fun getTotalMemoryUsage(): String {
        // 각 파티클 객체가 약 60바이트를 사용한다고 가정
        val particleBytes = particles.size * 60

        // 공유 상태의 바이트 수
        val sharedStateStr = particleTypeFactory.estimateSharedMemoryUsage()
        val sharedBytes = when {
            sharedStateStr.endsWith("bytes") -> sharedStateStr.replace(" bytes", "").toInt()
            sharedStateStr.endsWith("KB") -> sharedStateStr.replace(" KB", "").toInt() * 1024
            sharedStateStr.endsWith("MB") -> sharedStateStr.replace(" MB", "").toInt() * 1024 * 1024
            else -> 0
        }

        val totalBytes = particleBytes + sharedBytes
        return when {
            totalBytes < 1024 -> "$totalBytes bytes"
            totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
            else -> "${totalBytes / (1024 * 1024)} MB"
        }
    }

    // 캐시된 파티클 타입 수
    fun getCachedTypesCount(): Int = particleTypeFactory.getCachedTypesCount()
}

fun main() {
    val system = OptimizedParticleSystem(10000)

    println("최적화된 파티클 시스템 시작: 파티클 생성 중...")

    // 여러 타입의 파티클 생성
    repeat(2000) { system.createParticle(ParticleType.FIRE) }
    repeat(1500) { system.createParticle(ParticleType.SMOKE) }
    repeat(1000) { system.createParticle(ParticleType.EXPLOSION) }
    repeat(800) { system.createParticle(ParticleType.SPARKLE) }
    repeat(700) { system.createParticle(ParticleType.WATER) }

    println("총 생성된 파티클 수: ${system.getParticleCount()}")
    println("캐시된 파티클 타입 수: ${system.getCachedTypesCount()}")
    println("파티클 객체 메모리 사용량: ${system.estimateMemoryUsage()} (공유 상태 제외)")
    println("공유 상태 메모리 사용량: ${system.getSharedMemoryUsage()}")
    println("총 메모리 사용량: ${system.getTotalMemoryUsage()}")

    println("\n파티클 시스템 업데이트 및 렌더링 (5프레임)...")

    // 몇 프레임 시뮬레이션
    repeat(5) {
        println("\n===== 프레임 ${it + 1} =====")
        system.updateParticles()
        // 파티클이 너무 많아서 실제 렌더링은 몇 개만 보여줌
        println("총 파티클 수: ${system.getParticleCount()} (렌더링 샘플 3개)")
        repeat(3) { system.createParticle(ParticleType.values().random()) }
        system.renderParticles()
        println("현재 메모리 사용량: ${system.getTotalMemoryUsage()}")
    }

    println("\n해결된 문제:")
    println("1. 공통 상태(텍스처, 행동 패턴, 렌더링 세부 정보)가 모든 파티클 간에 공유됩니다.")
    println("2. 메모리 사용량이 크게 줄었습니다. (파티클당 500바이트 → 60바이트 + 공유 상태)")
    println("3. 객체 생성 오버헤드가 감소했습니다.")
    println("4. 더 많은 파티클을 효율적으로 관리할 수 있게 되었습니다.")

    println("\n플라이웨이트 패턴의 추가 이점:")
    println("1. 새로운 파티클 타입 추가가 용이합니다.")
    println("2. 공유 상태의 변경이 모든 관련 파티클에 자동으로 적용됩니다.")
    println("3. 캐시 메커니즘으로 성능이 향상됩니다.")
}