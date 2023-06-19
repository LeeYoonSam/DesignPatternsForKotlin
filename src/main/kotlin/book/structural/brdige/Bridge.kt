package book.structural.brdige

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

class Flamethrower : Weapon {
    override fun attack(): PointsOfDamage = RIFLE_DAMAGE * 2
}

class Batton : Weapon {
    override fun attack(): PointsOfDamage = RIFLE_DAMAGE * 3
}

class RegularLegs : Legs {
    override fun move(): Meters = REGULAR_SPEED
}

class AthleticLegs : Legs {
    override fun move(): Meters = REGULAR_SPEED * 2
}

// 클래스를 최대한 평평하게 처리
val stormTrooper = StormTrooperBridge(Rifle(), RegularLegs())
val flameTrooper = StormTrooperBridge(Flamethrower(), RegularLegs())
val scoutTrooper = StormTrooperBridge(Rifle(), AthleticLegs())