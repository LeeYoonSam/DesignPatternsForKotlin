package structural.decorator

/**
 * 출처: https://gmlwjd9405.github.io/2018/07/09/decorator-pattern.html
 *
 * DecoratorPattern(구조 패턴)
 *
 * < 데코레이터 패턴 >
 * 객체의 결합을 통해 기능을 동적으로 유연하게 확장 할 수 있게 해주는 패턴
 *      즉, 기본 기능에 추가할 수 있는 기능의 종류가 많은 경우에 각 추가 기능을 Decorator 클래스로 정의 한 후 필요한
 *      Decorator 객체를 조합함으로써 추가 기능의 조합을 설계 하는 방식이다.
 *          Ex) 기본 도로 표시 기능에 차선 표시, 교통량 표시, 교차로 표시, 단속 카메라 표시의 4가지 추가 기능이 있을 때 추가 기능의 모든 조합은 15가지가 된다.
 *          -> 데코레이터 패턴을 이용하여 필요 추가 기능의 조합을 동적으로 생성할 수 있다.
 * 기본 기능에 추가할 수 있는 많은 종류의 부가 기능에서 파생되는 다양한 조합을 동적으로 구현할 수 있는 패턴이다.
 *
 * 역할이 수행하는 작업
 * Component
 *      기본 기능을 뜻하는 ConcreteComponent와 추가 기능을 뜻하는 Decorator의 공통 기능을 정의
 *      즉, 클라이언트는 Component를 통해 실제 객체를 사용함
 * ConcreteComponent
 *      기본 기능을 구현하는 클래스
 * Decorator
 *      많은 수가 존재하는 구체적인 Decorator의 공통 기능을 제공
 * ConcreteDecoratorA, ConcreteDecoratorB
 *      Decorator의 하위 클래스로 기본 기능에 추가되는 개별적인 기능을 뜻함
 *      ConcreteDecorator 클래스는 ConcreteComponent 객체에 대한 참조가 필요한데, 이는 Decorator 클래스에서 Component 클래스로의 ‘합성(composition) 관계’를 통해 표현됨
 *
 * 참고
 *  구조패턴 이란?
 *      클래스나 객체를 조합해서 더 큰 구조를 만드는 패턴
 *      예를 들어 서로 다른 인터페이스를 지닌 2개의 객체를 묶어 단일 인터페이스를 제공하거나 객체들을 서로 묶어 새로운 기능을 제공하는 패턴
 */
class DecoratorPattern {
    /**
     * 도로 표시 방법 조합하기 - Before
     *
     * 내비게이션 SW에서 도로를 표시하는 기능
     * 도로를 간단한 선으로 표시하는 기능(기본 기능)
     * 내비게이션 SW에 따라 도로의 차선을 표시하는 기능(추가 기능)
     *
     * 문제점
     * 1. 또 다른 도로 표시 기능을 추가로 구현하는 경우
     *      기본 도로 표시에 교통량을 표시하고 싶다면?
     * 2. 여러가지 추가 기능을 조합해야 하는 경우
     *      기본 도로 표시에 차선 표시 기능과 교통량 표시 기능을 함께 제공하고 싶다면?
     *      상속을 통해 조합의 각 경우를 설계한다면 각 조합별로 하위 클래스를 일일이 다 구현해야 한다
     *      다양한 기능의 조합을 고려해야 하는 경우 상속을 통한 기능의 확장은 각 기능별로 클래스를 추가해야한다는 단점
     */
    open class RoadDisplayBefore {
        open fun draw() = println("기본 도로 표시")
    }

    class RoadDisplayWithLane: RoadDisplayBefore() {
        override fun draw() {
            super.draw() // 상위 클래스, RoadDisplay 클래스의 draw 메서드를 호출해서 기본 도로 표시
            drawLane() // 추가로 자체적으로 가진 차선 표시
        }

        private fun drawLane() = println("차선 표시")
    }

    /**
     * 해결책
     * 문제를 해결하기 위해서는 각 추가 기능별로 개별적인 클래스 설계를하고 기능을 조합할 때 각 클래스의 객체 조합을 이용하면 된다.
     *
     * 도로를 표시하는 기본 기능만 필요한 경우 RoadDisplay 객체를 이용한다.
     * 차선을 표시하는 추가 기능도 필요한 경우 RoadDisplay 객체와 LaneDecorator 객체를 이용한다.
     *      LaneDecorator에서는 차선 표시 기능만 직접 제공: drawLane()
     *      도로 표시 가능은 RoadDisplay 클래스의 draw 메서드를 호출: super.draw()
     *          (DisplayDecorator 클래스에서 Display 클래스로의 합성(composition) 관계를 통해 RoadDisplay 객체에 대한 참조)
     */
    interface Display {
        fun draw()
    }

    // 기본 도로 표시 클래스
    class RoadDisplay: Display {
        override fun draw() = println("기본 도로 표시")
    }

    // 다양한 추가 기능에 대한 공통 클래스
    abstract class DisplayDecorator(private val decorateDisplay: Display): Display {
        override fun draw() {
            decorateDisplay.draw()
        }
    }

    // 차선 표시를 추가하는 클래스
    class LaneDecorator(decorateDisplay: Display): DisplayDecorator(decorateDisplay) {
        override fun draw() {
            super.draw()
            drawLane()
        }

        // 차선 표시 기능만 직접 제공
        private fun drawLane() = println("\t차선 표시")
    }

    // 교통량 표시를 추가하는 클래스
    class TrafficDecorator(decorateDisplay: Display): DisplayDecorator(decorateDisplay) {
        override fun draw() {
            super.draw()
            drawTraffic()
        }

        // 교통량 표시 기능만 직접 제공
        private fun drawTraffic() = println("\t교통량 표시")
    }

    // 교차로 표시를 추가하는 클래스
    class CrossingDecorator(decorateDisplay: Display): DisplayDecorator(decorateDisplay) {
        override fun draw() {
            super.draw()
            drawCrossing()
        }

        private fun drawCrossing() = println("\t교차로 표시")
    }
}

fun main() {
    /**
     * Before
     */
    println("--------------------------------- Before ---------------------------------")
    val roadBefore = DecoratorPattern.RoadDisplayBefore()
    roadBefore.draw().also { println("") }

    val roadWithLaneBefore = DecoratorPattern.RoadDisplayWithLane()
    roadWithLaneBefore.draw()
    /**
     * After
     */
    println("--------------------------------- After ---------------------------------")
    val road = DecoratorPattern.RoadDisplay()
    road.draw().also { println("") }// 기본 도로 표시

    val roadWithLane = DecoratorPattern.LaneDecorator(road)
    roadWithLane.draw().also { println("") } // 기본 도로 표시 + 차선 표시

    val roadWithTraffic = DecoratorPattern.TrafficDecorator(road)
    roadWithTraffic.draw() // 기본 도로 표시 + 교통량 표시

    println("--------------------------------- After 추가 예시 ---------------------------------")
    // 기본 도로 + 차선 + 교통량 표시
    val roadWithLaneAndTraffic =
        DecoratorPattern.TrafficDecorator(
            roadWithLane
        )
    roadWithLaneAndTraffic.draw().also { println("") }

    // 기본 도로 + 차선 + 교통량 + 교차로 표시
    val roadWithCrossingLaneAndTraffic =
        DecoratorPattern.LaneDecorator(
            DecoratorPattern.TrafficDecorator(
                DecoratorPattern.CrossingDecorator(
                    road
                )
            )
        )

    roadWithCrossingLaneAndTraffic.draw()
}