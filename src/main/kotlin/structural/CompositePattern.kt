package structural

/**
 * 참고: https://gmlwjd9405.github.io/2018/08/10/composite-pattern.html
 *
 * Composite Pattern(구조 패턴)
 *
 * < 컴퍼지트 패턴 >
 *
 * 여러 개의 객체들로 구성된 복합 객체와 단일 객체를 클라이언트에서 구별 없이 다루게 해주는 패턴
 *      즉, 전체-부분의 관계(Ex. Directory-File)를 갖는 객체들 사이의 관계를 정의할 때 유용하다.
 *      또한 클라이언트는 전체와 부분을 구분하지 않고 동일한 인터페이스 를 사용할 수 있다.
 *
 * 역할이 수행하는 작업
 *      Component
 *          구체적인 부분
 *          즉 Leaf 클래스와 전체에 해당하는 Composite 클래스에 공통 인터페이스를 정의
 *      Leaf
 *          구체적인 부분 클래스
 *          Composite 객체의 부품으로 설정
 *      Composite
 *          전체 클래스
 *          복수 개의 Component를 갖도록 정의
 *          그러므로 복수 개의 Leaf, 심지어 복수 개의 Composite 객체를 부분으로 가질 수 있음
 *
 *
 * 구조 패턴(Structural Pattern)
 *      클래스나 객체를 조합해서 더 큰 구조를 만드는 패턴
 *      예를 들어 서로 다른 인터페이스를 지닌 2개의 객체를 묶어 단일 인터페이스를 제공하거나 객체들을 서로 묶어 새로운 기능을 제공하는 패턴
 */
class CompositePattern {
    /**
     * 컴퓨터에 추가 장치 지원하기
     *
     * 컴퓨터(Computer 클래스) 모델링
     *      키보드(Keyboard 클래스): 데이터를 입력받는다.
     *      본체(Body 클래스): 데이터를 처리한다.
     *      모니터(Monitor 클래스): 처리 결과를 출력한다.
     * Computer 클래스 –‘합성 관계’– 구성 장치
     *
     * 참고
     * 합성 관계
     *      생성자에서 필드에 대한 객체를 생성하는 경우
     *      전체 객체의 라이프타임과 부분 객체의 라이프 타임은 의존적이다.
     *      즉, 전체 객체(마름모가 표시된 클래스)가 없어지면 부분 객체도 없어진다.
     *
     *
     * 문제점
     * 다른 부품이 추가되는 경우
     *      Computer 클래스의 부품으로 Speaker 클래스 또는 Mouse 클래스를 추가한다면?
     *
     *      위와 같은 방식의 설계는 확장성이 좋지 않다. 즉, OCP를 만족하지 않는다.
     *      새로운 부품을 추가할 때마다 Computer 클래스를 아래와 같이 수정해야 한다.
     *          새로운 부품에 대한 참조를 필드로 추가한다.
     *          새로운 부품 객체를 설정하는 setter 메서드로 addDevice와 같은 메서드를 추가한다.
     *          getPrice, getPower 등과 같이 컴퓨터의 부품을 이용하는 모든 메서드에서는 새롭게 추가된 부품 객체를 이용할 수 있도록 수정한다.
     *      문제점의 핵심은 Computer 클래스에 속한 부품의 구체적인 객체를 가리키면 OCP를 위반하게 된다는 것이다.
     */
    data class KeyBoardBefore(val power: Int, val price: Int)
    data class BodyBefore(val power: Int, val price: Int)
    data class MonitorBefore(val power: Int, val price: Int)

    class ComputerBefore {
        private lateinit var keyboard: KeyBoardBefore
        private lateinit var body: BodyBefore
        private lateinit var monitor: MonitorBefore

        fun addKeyboard(keyBoard: KeyBoardBefore) { this.keyboard = keyBoard }
        fun addBody(body: BodyBefore) { this.body = body }
        fun addMonitor(monitor: MonitorBefore) { this.monitor = monitor }

        fun getPrice(): Int {
            val keyboardPrice = keyboard.price
            val bodyPrice = body.price
            val monitorPrice = monitor.price

            return keyboardPrice.plus(bodyPrice).plus(monitorPrice)
        }

        fun getPower(): Int {
            val keyboardPower = keyboard.power
            val bodyPower = body.power
            val monitorPower = monitor.power

            return keyboardPower.plus(bodyPower).plus(monitorPower)
        }
    }

    /**
     * 해결책
     * 구체적인 부품들을 일반화한 클래스를 정의하고 이를 Computer 클래스가 가리키도록 설계한다.
     *
     * 구체적인 부품들을 일반화한 ComputerDevice 클래스를 정의
     *      ComputerDevice 클래스는 구체적인 부품 클래스의 공통 기능만 가지며 실제로 존재하는 구체적인 부품이 될 수는 없다. (즉, ComputerDevice 객체를 실제로 생성할 수 없다.)
     *      그러므로 ComputerDevice 클래스는 추상 클래스가 된다.
     * 구체적인 부품 클래스들(Keyboard, Body 등)은 ComputerDevice의 하위 클래스로 정의
     * Computer 클래스는 복수 개( 0..* )의 ComputerDevice 객체를 갖음
     * Computer 클래스도 ComputerDevice 클래스의 하위 클래스로 정의
     *      즉, Computer 클래스도 ComputerDevice 클래스의 일종
     *      ComputerDevice 클래스를 이용하면 Client 프로그램은 Keyboard, Body 등과 마찬가지로 Computer를 상용할 수 있다.
     */
    interface ComputerDevice {
        val power: Int
        val price: Int
    }

    class KeyBoard(override val power: Int, override val price: Int): ComputerDevice
    class Body(override val power: Int, override val price: Int): ComputerDevice
    class Monitor(override val power: Int, override val price: Int): ComputerDevice
    class Speaker(override val power: Int, override val price: Int): ComputerDevice
    class Mouse(override val power: Int, override val price: Int): ComputerDevice

    class Computer: ComputerDevice {
        // 복수 개의 ComputerDevice 객체를 가리킴
        private val components = mutableListOf<ComputerDevice>()

        fun addComponent(component: ComputerDevice) = components.add(component)
        fun removeComponent(component: ComputerDevice) = components.remove(component)

        override val power: Int
            get() = components.sumOf { it.power }

        override val price: Int
            get() = components.sumOf { it.price }
    }
}

fun main() {
    /**
     * Before
     */
    println("--------------------------------- Before ---------------------------------")
    val keyboardBefore = CompositePattern.KeyBoardBefore(5, 2)
    val bodyBefore = CompositePattern.BodyBefore(100, 70)
    val monitorBefore = CompositePattern.MonitorBefore(20, 30)

    val computerBefore = CompositePattern.ComputerBefore()
    computerBefore.addKeyboard(keyboardBefore)
    computerBefore.addBody(bodyBefore)
    computerBefore.addMonitor(monitorBefore)

    val computerPrice = computerBefore.getPrice()
    val computerPower = computerBefore.getPower()
    println("Computer Price: ${computerPrice}만원\nComputer Power: ${computerPower}W")

    /**
     * After
     */
    println("--------------------------------- After ---------------------------------")
    val keyBoard = CompositePattern.KeyBoard(5, 2)
    val body = CompositePattern.Body(100, 70)
    val monitor = CompositePattern.Monitor(20, 30)
    val speaker = CompositePattern.Speaker(10, 5)
    val mouse = CompositePattern.Mouse(5, 2)

    val computer = CompositePattern.Computer()
    computer.addComponent(keyBoard)
    computer.addComponent(body)
    computer.addComponent(monitor)
    computer.addComponent(speaker)
    computer.addComponent(mouse)
    println("Computer Price: ${computer.price}만원\nComputer Power: ${computer.power}W")

    computer.removeComponent(mouse)
    println("Computer Price: ${computer.price}만원\nComputer Power: ${computer.power}W")
}