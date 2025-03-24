package structural.flyweight.gameobjects

import java.awt.Color
import kotlin.random.Random

/**
 * 1. 데이터 분리:
 * 내부 상태(공유 가능): EnemyType 클래스에 메쉬, 텍스처, 기본 능력치 등 공유 가능한 데이터 저장
 * 외부 상태(인스턴스별): EnemyInstance 클래스에 위치, 회전, 크기, 현재 체력 등 인스턴스별 데이터 저장
 *
 *
 * 2. 플라이웨이트 팩토리 도입:
 * EnemyTypeFactory가 공유 객체를 생성하고 캐싱
 * 동일한 타입 요청 시 기존 객체를 재사용
 *
 *
 * 3. 메모리 최적화:
 * - 3가지 타입의 적이라면 3×(10KB + 100KB) + 100×0.1KB = 340KB 메모리 사용
 * - 약 97% 메모리 절약 (11,000KB → 340KB)
 *
 *
 * < 이해 포인트 >
 *
 * 1. 내부 상태와 외부 상태 구분:
 * - 내부 상태(Intrinsic State): 여러 객체에서 공유 가능한 불변 데이터 (예: 메쉬, 텍스처)
 * - 외부 상태(Extrinsic State): 객체마다 다른 컨텍스트 의존 데이터 (예: 위치, 회전)
 * - 어떤 데이터가 공유 가능한지 올바르게 식별하는 것이 중요합니다.
 *
 * 2. 캐싱 메커니즘:
 * ```
 * fun getEnemyType(typeName: String): EnemyType {
 *     return enemyTypes.getOrPut(typeName) {
 *         when (typeName) {
 *             "Orc" -> EnemyType("Orc", 120, 15, Color.GREEN)
 *             // ...
 *         }
 *     }
 * }
 * ```
 * - getOrPut 메서드를 사용해 캐시에 있으면 기존 객체 반환, 없으면 새로 생성
 * - 이는 코틀린의 간결한 방식으로 캐싱 로직을 구현한 예입니다.
 *
 * 3. 플라이웨이트 패턴 적용 기준:
 * - 많은 수의 유사 객체가 존재하는 경우
 * - 객체의 상태를 내부/외부로 분리할 수 있는 경우
 * - 객체 생성 비용이 높거나 메모리 사용량이 큰 경우
 *
 * 4. 객체 지향 설계 원칙:
 * - 단일 책임 원칙: 각 클래스는 하나의 책임만 가짐
 * - 인터페이스 분리: GameObject 인터페이스로 일관된 렌더링 방식 제공
 * - 의존성 주입: EnemyInstance가 EnemyType에 의존
 */
class Solution {
    // 적 캐릭터의 공유 가능한 내부 상태(intrinsic state) - 플라이웨이트 객체
    class EnemyType(
        val typeName: String,
        val baseHealth: Int,
        val baseDamage: Int,
        val color: Color
    ) {
        // 공유 가능한 무거운 리소스
        private val mesh: Mesh
        private val texture: Texture

        init {
            // 메쉬 생성 (공유됨)
            val enemyVertices = listOf(
                Point2D(0f, 0f),
                Point2D(1f, 0f),
                Point2D(1f, 1f),
                Point2D(0f, 1f)
            )
            mesh = Mesh(enemyVertices)

            // 텍스처 로딩 (공유됨)
            texture = Texture("enemy_$typeName")

            println("적 타입 '$typeName' 생성 완료 - 공유 리소스 로딩")
        }

        fun render(position: Point2D, scale: Float, rotation: Float, instanceName: String, instanceHealth: Int) {
            // 렌더링 로직 (실제로는 더 복잡)
            println("적 캐릭터 렌더링: $instanceName (타입: $typeName, 위치: $position, 크기: $scale, 회전: $rotation)")
            println("  - 체력: $instanceHealth, 기본 공격력: $baseDamage, 색상: $color")
        }
    }

    // 플라이웨이트 팩토리 - 적 타입 객체를 생성하고 캐싱
    class EnemyTypeFactory {
        private val enemyTypes = mutableMapOf<String, EnemyType>()

        fun getEnemyType(typeName: String): EnemyType {
            // 기존에 생성된 타입이 있으면 재사용, 없으면 새로 생성
            return enemyTypes.getOrPut(typeName) {
                when (typeName) {
                    "Orc" -> EnemyType("Orc", 120, 15, Color.GREEN)
                    "Goblin" -> EnemyType("Goblin", 80, 8, Color.BLUE)
                    "Troll" -> EnemyType("Troll", 200, 25, Color.RED)
                    else -> throw IllegalArgumentException("알 수 없는 적 타입: $typeName")
                }
            }
        }

        fun getTypeCount(): Int = enemyTypes.size
    }

    // 실제 적 인스턴스 - 외부 상태(extrinsic state)만 저장
    class EnemyInstance(
        val instanceId: Int,
        val type: EnemyType,
        var health: Int,  // 인스턴스별 변수(외부 상태)
        var position: Point2D = Point2D(0f, 0f), // 인스턴스별 위치(외부 상태)
        var scale: Float = 1.0f, // 인스턴스별 크기(외부 상태)
        var rotation: Float = 0f // 인스턴스별 회전(외부 상태)
    ) : GameObject {

        val name: String = "${type.typeName}#$instanceId"

        override fun render(position: Point2D, scale: Float, rotation: Float) {
            // 인스턴스 특정 위치와 상태로 적 타입 렌더링
            type.render(position, scale, rotation, name, health)
        }
    }
}

// 플라이웨이트 패턴을 적용한 코드를 실행하는 메인 함수
fun main() {
    println("===== 플라이웨이트 패턴 적용 후 =====")
    val random = Random
    val enemyTypeFactory = Solution.EnemyTypeFactory()
    val enemies = mutableListOf<Solution.EnemyInstance>()

    println("적 캐릭터 100개 생성 중...")
    val startTime = System.currentTimeMillis()

    // 100개의 적 캐릭터 생성 (공유 리소스를 사용)
    for (i in 1..100) {
        val enemyTypeName = if (i % 3 == 0) "Orc" else if (i % 3 == 1) "Goblin" else "Troll"
        val enemyType = enemyTypeFactory.getEnemyType(enemyTypeName)

        // 인스턴스별 상태 (외부 상태)
        val health = when (enemyTypeName) {
            "Orc" -> enemyType.baseHealth + random.nextInt(-10, 10)
            "Goblin" -> enemyType.baseHealth + random.nextInt(-5, 5)
            else -> enemyType.baseHealth + random.nextInt(-20, 20)
        }

        val position = Point2D(random.nextFloat() * 100, random.nextFloat() * 100)
        val scale = 0.8f + random.nextFloat() * 0.4f
        val rotation = random.nextFloat() * 360

        val enemy = Solution.EnemyInstance(i, enemyType, health, position, scale, rotation)
        enemies.add(enemy)
    }

    val endTime = System.currentTimeMillis()
    val typeCount = enemyTypeFactory.getTypeCount()

    println("생성 완료! 소요 시간: ${endTime - startTime}ms")
    println("공유 리소스 수: $typeCount (적 타입 종류)")
    println("메모리 사용량 (추정): ${(typeCount * (10 + 100) + 100 * 0.1)}KB ($typeCount 타입 * (10KB 메쉬 + 100KB 텍스처) + 100개 인스턴스 * 0.1KB)")

    println("\n게임 렌더링 시작...")
    // 몇 개의 적만 렌더링
    for (i in 0 until 5) {
        val enemy = enemies[i]
        enemy.render(enemy.position, enemy.scale, enemy.rotation)
    }

    println("\n이점: 동일한 타입의 적들이 메쉬와 텍스처를 공유하여 메모리 사용량 대폭 감소")
    println("  - 적용 전: ${(100 * (10 + 100))}KB")
    println("  - 적용 후: ${(typeCount * (10 + 100) + 100 * 0.1)}KB")
    println("  - 절약된 메모리: ${(100 * (10 + 100)) - (typeCount * (10 + 100) + 100 * 0.1)}KB")
}