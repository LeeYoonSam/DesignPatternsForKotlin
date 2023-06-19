package book.structural.brdige

interface Trooper {
    fun move(x: Long, y: Long)
    fun attackRebel(x: Long, y: Long)
}

/**
 * 다른 유형의 병사를 여러개 구현
 */
open class StormTrooper : Trooper {
    override fun move(x: Long, y: Long) {
        // Move at normal speed
    }

    override fun attackRebel(x: Long, y: Long) {
        // Missed most of the time
    }
}

open class ShockTrooper : Trooper {
    override fun move(x: Long, y: Long) {
        // Moves slower than regular StormTrooper
    }

    override fun attackRebel(x: Long, y: Long) {
        // Sometimes hits
    }
}

/**
 * 더 강력한 병사 구현
 */
class RiotControlTrooper : StormTrooper() {
    override fun attackRebel(x: Long, y: Long) {
        // Has an electric baton, stay away!
    }
}

class FlameTrooper : ShockTrooper() {
    override fun attackRebel(x: Long, y: Long) {
        // Uses flametrower, dangerous!
    }
}

/**
 * 다른 부대보다 더 빠른 정찰병 구현
 */
class ScoutTrooper : ShockTrooper() {
    override fun move(x: Long, y: Long) {
        // Runs faster
    }
}
