package behavioral

/**
 * 출처: https://gmlwjd9405.github.io/2018/07/06/strategy-pattern.html
 *
 * Strategy Pattern (행위 패턴)
 *
 * < 전략 패턴 >
 * 행위를 클래스로 캡슐화해 동적으로 행위를 자유롭게 바꿀 수 있게 해주는 패턴
 *      같은 문제를 해결하는 여러 알고리즘이 클래스별로 캡슐화되어 있고 이들이 필요할 때 교체할 수 있도록 함으로써 동일한 문제를 다른 알고리즘으로 해결할 수 있게 하는 디자인 패턴
 *      ‘행위(Behavioral) 패턴’의 하나 (아래 참고)
 * 즉, 전략을 쉽게 바꿀 수 있도록 해주는 디자인 패턴이다.
 *      전략이란
 *          어떤 목적을 달성하기 위해 일을 수행하는 방식, 비즈니스 규칙, 문제를 해결하는 알고리즘 등
 *          특히 게임 프로그래밍에서 게임 캐릭터가 자신이 처한 상황에 따라 공격이나 행동하는 방식을 바꾸고 싶을 때 스트래티지 패턴은 매우 유용하다.
 *
 *
 * 역할이 수행하는 작업
 *  Strategy
 *      인터페이스나 추상 클래스로 외부에서 동일한 방식으로 알고리즘을 호출하는 방법을 명시
 *  ConcreteStrategy
 *      스트래티지 패턴에서 명시한 알고리즘을 실제로 구현한 클래스
 *  Context
 *      스트래티지 패턴을 이용하는 역할을 수행한다.
 *      필요에 따라 동적으로 구체적인 전략을 바꿀 수 있도록 setter 메서드(‘집약 관계’)를 제공한다.
 *
 *  참고
 *  > 행위 패턴이란?
 *      객체나 클래스 사이의 알고리즘이나 책임 분배에 관련된 패턴
 *      한 객체가 혼자 수행할 수 없는 작업을 여러 개의 객체로 어떻게 분배하는지, 또 그렇게 하면서도 객체 사이의 결합도를 최소화하는 것에 중점을 둔다.
 *
 */
class StrategyPattern {
    /**
     * 로봇 만들기 - Before
     *
     * 문제점
     * 1. 기존 로봇의 공격와 이동 방법을 수정하는 경우
     *      아톰이 날 수는 없고 걷게만 만들고 싶으면?
     *      태권브이를 날게 하려면?
     *      새로운 기능으로 변경하려고 기존 코드의 내용을 수정해야 하므로 OCP(SOLID 이론의 개방폐쇄의 원칙)에 위배된다.
     *      또한 태권브이와 아톰의 move() 메서드의 내용이 중복
     *      만약 걷는 방식에 문제가 있거나 새로운 방식으로 수정하려면 모든 중복 코드를 일관성있게 변경해야만 한다.
     * 2. 새로운 로봇을 만들어 기존의 공격또는 이동방법을 추가/수정하는 경우
     *      새로운 로봇으로 썬가드를 만들어 태권브이의 미사일 공격 기능을 추가하려면?
     *      태권브이와 썬가드 클래스의 attack() 메서드의 내용이 중복된다.
     *      현재 시스템의 캡슐화의 단위가 Robot 자체이므로 로봇을 추가하기는 매우 쉬우나 새로운 로봇인 썬가드에 기존의 공격 또는 이동 방법을 추가하거나 변경하려고 하면 문제가 발생
     */
    abstract class RobotBefore(val name: String) {
        abstract fun attack()
        abstract fun move()
    }

    class TaekwonVBefore(name: String): RobotBefore(name) {
        override fun attack() = println("I have Missile.")
        override fun move() = println("I can only walk.")
    }

    class AtomBefore(name: String): RobotBefore(name) {
        override fun attack() = println("I have strong punch.")
        override fun move() = println("I can fly.")
    }

    /**
     * 로봇 만들기 - After
     *
     * 해결책
     * 문제를 해결하기 위해서는 무엇이 변화되었는지 찾은 후에 이를 클래스로 캡슐화 해야 한다.
     *
     * 문제를 발생기키는 요인인 이동 방식과 공격 방식의 변화, 이를 캡슐화하려면 외부에서 구체적인 이동 방식과 공격 방식을 담은 구체적인 클래스들을 은닉해야 한다.
     *      공격과 이동을 위한 인터페이스를 각각 만들고 이들을 실제 실현한 클래스를 만들어야 한다.
     *
     * Robot 클래스가 이동 기능과 공격 기능을 이용하는 클라이언트 역할을 수행
     *      구체적인 이동, 공격 방식이 MovingStrategy 와 AttackStrategy 인터페이스에 의해 캡슐화 되어 있다.
     *      이 인터페이스들이 일종의 방화벽 역할을 수행해 Robot 클래스의 변경을 차단 해준다.
     */

    interface AttackStrategy {
        fun attack()
    }

    class MissileStrategy: AttackStrategy {
        override fun attack() = println("I have Missile.")
    }

    class PunchStrategy: AttackStrategy {
        override fun attack() = println("I have strong punch.")
    }

    interface MovingStrategy {
        fun move()
    }

    class WalkingStrategy: MovingStrategy {
        override fun move() = println("I can only walk.")
    }

    class FlyingStrategy: MovingStrategy {
        override fun move() = println("I can fly.")
    }

    abstract class Robot(val name: String) {
        private lateinit var attackStrategy: AttackStrategy
        private lateinit var movingStrategy: MovingStrategy

        fun attack() {
            attackStrategy.attack()
        }

        fun move() {
            movingStrategy.move()
        }

        fun setAttackStrategy(attackStrategy: AttackStrategy) {
            this.attackStrategy = attackStrategy
        }

        fun setMovingStrategy(movingStrategy: MovingStrategy) {
            this.movingStrategy = movingStrategy
        }
    }

    class TaekwonV(name: String): Robot(name)
    class Atom(name: String): Robot(name)
}

fun main() {
    /**
     * Before
     */
    println("--------------------------------- Before ---------------------------------")
    StrategyPattern.TaekwonVBefore("TaekwonV").also {
        println("My name is ${it.name}")
        it.attack()
        it.move()
    }

    StrategyPattern.AtomBefore("Atom").also {
        println("My name is ${it.name}")
        it.attack()
        it.move()
    }

    /**
     * After
     */
    println("--------------------------------- After ---------------------------------")
    StrategyPattern.TaekwonV("TaekwonV").apply {
        setMovingStrategy(StrategyPattern.WalkingStrategy())
        setAttackStrategy(StrategyPattern.MissileStrategy())
    }.also {
        println("My name is ${it.name}")
        it.attack()
        it.move()
    }

    StrategyPattern.Atom("Atom").apply {
        setMovingStrategy(StrategyPattern.FlyingStrategy())
        setAttackStrategy(StrategyPattern.PunchStrategy())
    }.also {
        println("My name is ${it.name}")
        it.attack()
        it.move()
    }
}