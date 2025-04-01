package structural.flyweight.particlesystem

import kotlin.random.Random

// 플라이웨이트 패턴을 사용하지 않은 파티클 클래스
class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var lifespan: Int,
    var size: Float,
    var type: ParticleType,
    var color: ParticleColor,
    var texture: String, // 텍스처 경로 (메모리를 많이 차지한다고 가정)
    var behaviorPattern: String, // 행동 패턴 (메모리를 많이 차지한다고 가정)
    var renderingDetails: Map<String, Any> // 렌더링 세부 사항 (메모리를 많이 차지한다고 가정)
) {
    var currentLife: Int = lifespan

    fun update() {
        x += velocityX
        y += velocityY
        currentLife--
    }

    fun render() {
        renderParticle(x, y, type, color, size, texture)
    }

    fun isAlive(): Boolean = currentLife > 0
}

// 파티클 정보를 담는 데이터 클래스
data class ParticleData(
    val color: ParticleColor,
    val texture: String,
    val behaviorPattern: String,
    val renderingDetails: Map<String, Any>
)

// 파티클 시스템 클래스
class ParticleSystem(private val maxParticles: Int) {
    private val particles = mutableListOf<Particle>()
    private val random = Random(System.currentTimeMillis())

    // 새로운 파티클 생성
    fun createParticle(type: ParticleType) {
        if (particles.size >= maxParticles) return

        val x = random.nextFloat() * 800
        val y = random.nextFloat() * 600
        val velocityX = (random.nextFloat() - 0.5f) * 10
        val velocityY = (random.nextFloat() - 0.5f) * 10
        val lifespan = random.nextInt(50, 200)
        val size = random.nextFloat() * 5 + 1

        // 타입에 따른 속성 설정
        val particleData = when (type) {
            ParticleType.FIRE -> ParticleData(
                ParticleColor.RED,
                "textures/fire_particle.png", // 큰 텍스처 데이터라고 가정
                "rising_fading", // 복잡한 행동 패턴 알고리즘이라고 가정
                mapOf(
                    "glow" to true,
                    "blendMode" to "additive",
                    "shader" to "fire_shader_v2",
                    "intensity" to 0.8f
                )
            )
            ParticleType.SMOKE -> ParticleData(
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
            ParticleType.EXPLOSION -> ParticleData(
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
            ParticleType.SPARKLE -> ParticleData(
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
            ParticleType.WATER -> ParticleData(
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

        val particle = Particle(
            x = x,
            y = y,
            velocityX = velocityX,
            velocityY = velocityY,
            lifespan = lifespan,
            size = size,
            type = type,
            color = particleData.color,
            texture = particleData.texture,
            behaviorPattern = particleData.behaviorPattern,
            renderingDetails = particleData.renderingDetails
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
        // 각 파티클 객체가 약 500바이트를 사용한다고 가정
        return simulateMemoryUsage(particles.size, 500)
    }
}

fun main() {
    val system = ParticleSystem(10000)

    println("파티클 시스템 시작: 파티클 생성 중...")

    // 여러 타입의 파티클 생성
    repeat(2000) { system.createParticle(ParticleType.FIRE) }
    repeat(1500) { system.createParticle(ParticleType.SMOKE) }
    repeat(1000) { system.createParticle(ParticleType.EXPLOSION) }
    repeat(800) { system.createParticle(ParticleType.SPARKLE) }
    repeat(700) { system.createParticle(ParticleType.WATER) }

    println("총 생성된 파티클 수: ${system.getParticleCount()}")
    println("추정 메모리 사용량: ${system.estimateMemoryUsage()}")

    println("\n파티클 시스템 업데이트 및 렌더링 (5프레임)...")

    // 몇 프레임 시뮬레이션
    repeat(5) {
        println("\n===== 프레임 ${it + 1} =====")
        system.updateParticles()
        // 파티클이 너무 많아서 실제 렌더링은 몇 개만 보여줌
        println("총 파티클 수: ${system.getParticleCount()} (렌더링 샘플 3개)")
        repeat(3) { system.createParticle(ParticleType.values().random()) }
        system.renderParticles()
        println("현재 메모리 사용량: ${system.estimateMemoryUsage()}")
    }

    println("\n문제점:")
    println("1. 각 파티클마다 동일한 텍스처, 행동 패턴, 렌더링 세부 정보가 중복 저장됩니다.")
    println("2. 수천 개의 파티클을 위해 많은 메모리가 낭비됩니다.")
    println("3. 객체 생성 및 가비지 컬렉션 오버헤드가 높습니다.")
}