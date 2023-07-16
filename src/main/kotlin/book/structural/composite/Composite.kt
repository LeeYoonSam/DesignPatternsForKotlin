package book.structural.composite

fun main() {
    val bobaFett = StormTrooperBridge(Rifle(), RegularLegs())
    val squad = Squad(
        listOf(
            bobaFett.copy(),
            bobaFett.copy(),
            bobaFett.copy()
        )
    )

    squad.move(1, 0)

    /**
     * Nesting Composite
     *
     * 이제 소대에 명령을 내리는 것은 분대에 명령을 내리는 것과 똑같은 방식으로 작동합니다.
     * 실제로 이 패턴을 사용하면 임의의 복잡성을 가진 트리와 같은 구조를 지원하고 모든 노드에서 작업을 수행할 수 있습니다.
     */
    val platoon = Squad(Squad(), Squad())
}

interface Trooper {
    fun move(x: Long, y: Long)
    fun attackRebel(x: Long, y: Long)
    fun treat()
}

/**
 * Composite design pattern
 *
 * Trooper 의 새로운 기능이 추가되면 알수 없으므로 Trooper 를 상속 받습니다.
 *
 * Secondary constructor
 * - 생성자에게 스톰트루퍼 목록을 전달하는 대신 스톰트루퍼를 리스트로 감싸지 않고 직접 전달할 수 있다면 좋을 것 같습니다.
 * - 이를 달성하는 한 가지 방법은 스쿼드 클래스에 secondary constructor 를 추가하는 것입니다.
 */
class Squad(private val units: List<Trooper>): Trooper {
    constructor(): this(listOf())
    constructor(t1: Trooper) : this(listOf(t1))
    constructor(t1: Trooper, t2: Trooper) : this(listOf(t1, t2))
    constructor(vararg units: Trooper) : this(units.toList())

    override fun move(x: Long, y: Long) {
        for (u in units) {
            u.move(x, y)
        }
    }

    override fun attackRebel(x: Long, y: Long) {
        for (u in units) {
            u.attackRebel(x, y)
        }
    }

    override fun treat() {
        TODO("Not yet implemented")
    }
}

data class StormTrooperBridge(
    private val weapon: Weapon,
    private val legs: Legs
) : Trooper {
    override fun move(x: Long, y: Long) {
        TODO("Not yet implemented")
    }

    override fun attackRebel(x: Long, y: Long) {
        TODO("Not yet implemented")
    }

    override fun treat() {
        TODO("Not yet implemented")
    }
}

typealias PointsOfDamage = Long
typealias Meters = Int

interface Weapon {
    fun attack(): PointsOfDamage
}

interface Legs {
    fun move(): Meters
}

const val RIFLE_DAMAGE: PointsOfDamage = 3L
const val REGULAR_SPEED: Meters = 1

class Rifle : Weapon {
    override fun attack(): PointsOfDamage = RIFLE_DAMAGE
}

class RegularLegs : Legs {
    override fun move(): Meters = REGULAR_SPEED
}