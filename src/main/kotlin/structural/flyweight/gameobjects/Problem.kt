package structural.flyweight.gameobjects

import java.awt.Color
import kotlin.random.Random

class Problem {
    // 적 캐릭터 클래스 - 문제가 있는 구현
    class Enemy(val name: String, val health: Int, val damage: Int, val color: Color) : GameObject {
        // 각 적 캐릭터마다 개별적으로 메쉬와 텍스처를 저장 (메모리 낭비)
        private val mesh: Mesh
        private val texture: Texture

        init {
            // 메쉬 생성
            val enemyVertices = listOf(
                Point2D(0f, 0f),
                Point2D(1f, 0f),
                Point2D(1f, 1f),
                Point2D(0f, 1f)
            )
            mesh = Mesh(enemyVertices)

            // 텍스처 로딩
            texture = Texture("enemy_$name")

            println("적 캐릭터 $name 생성 완료")
        }

        override fun render(position: Point2D, scale: Float, rotation: Float) {
            // 렌더링 로직 (실제로는 더 복잡)
            println("적 캐릭터 렌더링: $name (위치: $position, 크기: $scale, 회전: $rotation)")
            println("  - 체력: $health, 공격력: $damage, 색상: $color")
        }
    }
}

// 문제가 있는 코드를 실행하는 메인 함수
fun main() {
    println("===== 플라이웨이트 패턴 적용 전 =====")
    val random = Random
    val enemies = mutableListOf<Problem.Enemy>()

    println("적 캐릭터 100개 생성 중...")
    val startTime = System.currentTimeMillis()

    // 100개의 적 캐릭터 생성 (각각 개별적인 메쉬와 텍스처를 가짐)
    for (i in 1..100) {
        val enemyType = if (i % 3 == 0) "Orc" else if (i % 3 == 1) "Goblin" else "Troll"
        val health = when (enemyType) {
            "Orc" -> 120
            "Goblin" -> 80
            else -> 200
        }
        val damage = when (enemyType) {
            "Orc" -> 15
            "Goblin" -> 8
            else -> 25
        }
        val color = when (enemyType) {
            "Orc" -> Color.GREEN
            "Goblin" -> Color.BLUE
            else -> Color.RED
        }

        enemies.add(Problem.Enemy("$enemyType#$i", health, damage, color))
    }

    val endTime = System.currentTimeMillis()
    println("생성 완료! 소요 시간: ${endTime - startTime}ms")
    println("메모리 사용량 (추정): ${(100 * (10 + 100))}KB (100개 객체 * (10KB 메쉬 + 100KB 텍스처))")

    println("\n게임 렌더링 시작...")
    // 몇 개의 적만 렌더링
    for (i in 0 until 5) {
        val enemy = enemies[i]
        val position = Point2D(random.nextFloat() * 100, random.nextFloat() * 100)
        val scale = 0.8f + random.nextFloat() * 0.4f
        val rotation = random.nextFloat() * 360

        enemy.render(position, scale, rotation)
    }

    println("\n문제점: 각 적 캐릭터마다 동일한 메쉬와 텍스처를 중복 저장하여 메모리 낭비 발생")
}