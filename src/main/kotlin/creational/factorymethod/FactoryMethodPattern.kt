package creational.factorymethod

import java.util.*

/**
 * 출처: https://gmlwjd9405.github.io/2018/08/07/factory-method-pattern.html
 *
 * FactoryMethod Pattern(생성 패턴)
 *
 * < 팩토리 메서드 패턴 >
 *
 * 객체 생성 처리를 서브 클래스로 분리해 처리하도록 캡슐화하는 패턴
 *      즉, 객체의 생성 코드를 별도의 클래스/메서드로 분리함으로써 객체 생성의 변화에 대비하는데 유용하다.
 *      특정 기능의 구현은 개별 클래스를 통해 제공되는 것이 바람직한 설계다.
 *          기능의 변경이나 상황에 따른 기능의 선택은 해당 객체를 생성하는 코드의 변경을 초래한다.
 *          상황에 따라 적절한 객체를 생성하는 코드는 자주 중복될 수 있다.
 *          객체 생성 방식의 변화는 해당되는 모든 코드 부분을 변경해야 하는 문제가 발생한다.
 *      스트래티지 패턴, 싱글턴 패턴, 템플릿 메서드 패턴을 사용한다.
 *
 * 역할이 수행하는 작업
 * Product
 *      팩토리 메서드로 생성될 객체의 공통 인터페이스
 * ConcreteProduct
 *      구체적으로 객체가 생성되는 클래스
 * Creator
 *      팩토리 메서드를 갖는 클래스
 * ConcreteCreator
 *      팩토리 메서드를 구현하는 클래스로 ConcreteProduct 객체를 생성
 *
 *
 * 팩토리 메서드 패턴의 개념과 적용방법
 *      - 객체 생성을 전담하는 별도의 Factory 클래스 이용
 *          스트래티지 패턴과 싱글턴 패턴을 이용
 *      - 상속 이용: 하위 클래스에서 적합한 클래스의 객체를 생성
 *          스트래티지 패턴, 싱글턴 패턴과 템플릿 메서드 패턴을 이용
 *
 * 참고
 * 생성 패턴이란?
 *      객체의 생성에 관련된 패턴
 *      객체의 생성과 조합을 캡슐화 해서 특정 객체가 생성되거나 변경되어도 프로그램 구조에 영향을 크게 받지 않도록 유연성을 제공하는 패턴
 */
class FactoryMethodPattern {
    /**
     * 여러 가지 방식의 엘리베이터 스케줄링 방법 지원하기 - Before
     *
     * 작업 처리량(Throughput)을 기준으로 한 스케줄링에 따른 엘리베이터 관리
     * 스케줄링: 주어진 요청(목적지 충과 방향)을 받았을 때 여러 대의 엘리베이터 중 하나를 선택하는 것을 말한다.
     *      예를 들어 엘리베이터 내부에서 버튼을 눌렀을 때는 해당 상요자가 탄 엘리베이터를 이동시킨다.
     *      그러나 사용자가 엘리베이터 외부, 즉 건물 내부의 층에서 버튼을 누른경우에는 여러대의 엘리베이터 중 하나를 선택해서 이동시켜야 한다.
     *
     * ElevatorManager 클래스
     *      이동 요청을 처리하는 클래스
     *      엘리베이터를 스케줄링(엘리베이터 선택)하기 위한 ThroughputScheduler 객체를 갖는다.
     *      각 엘리베이터의 이동을 책임지는 ElevatorController 객체를 복수 개 갖는다.
     *
     * requestElevator() 메서드
     *      요청(목적지 층, 방향)을 받았을 때 우선 ThroughputScheduler 클래스의 selectElevator() 메서드를 호출해 적정한 엘리베이터를 선택한다.
     *      선택된 엘리베이터에 해당하는 ElevatorController 객체의 gotoFloor() 메서드를 호출해 엘리베이터를 이동시킨다.
     *
     *
     *  문제점
     *  1. 다른 스케줄링 전략을 사용하는 경우
     *      엘리베이터 작업 처리량을 최대화(ThroughputScheduler 클래스)시키는 전략이 아닌 사용자의 대기 시간을 최소화하는 엘리베이터 선택 전략을 사용해야 한다면?
     *  2. 프로그램 실행 중에 스케줄링 전략을 변경, 즉 동적 스케줄링을 지원 해야하는 경우
     *      오전에는 대기 시간 최소화 전략을 사용하고, 오후에는 처리량 최대화 전략을 사용해야 한다면?
     */
    class ElevatorManagerBefore(controllerCount: Int) {
        private val controllers = mutableListOf<ElevatorController>()
        private val scheduler = ThroughputSchedulerBefore()

        init {
            for( i in 0 until controllerCount) {
                val controller = ElevatorController(1)
                controllers.add(controller)
            }
        }

        // 요청에 따라 엘리베이터를 선택하고 이동시킴
        fun requestElevator(destination: Int, direction: Direction) {
            // ThroughputSchedulerBefore 를 이용해 엘리베이터 선택
            val selectedElevator = scheduler.selectElevator(this, destination, direction)

            // 선택된 엘리베이터를 이동시킴
            controllers[selectedElevator].getFloor(destination)
        }
    }

    class ElevatorController(private val id: Int, private var curFloor: Int = 1) {
        fun getFloor(destination: Int) {
            print("Elevator [$id] Floor: $curFloor")

            // 현재 층 갱신, 즉 주어진 목적지 층(destination)으로 엘리베이터가 이동함
            curFloor = destination
            println(" ==> $curFloor")
        }
    }

    /* 엘리베이터 작업 처리량을 최대화시키는 전략의 클래스 */
    class ThroughputSchedulerBefore {
        fun selectElevator(manager: ElevatorManagerBefore, destination: Int, direction: Direction): Int {
            return 0 // 임의
        }
    }

    /**
     * 여러 가지 방식의 엘리베이터 스케줄링 방법 지원하기 - After
     *
     * 문제 1 해결방법
     * 스트래티지 패턴을 활용한 엘리베이터 스케줄링 전략을 설계
     *      requestElevator() 메서드가 실행될 때마다 현재 시간에 따라 적절한 스케줄링 객체를 생성해야 한다.
     *      ElevatorManager 클래스의 입장에서는 여러 스케줄링 전략이 있기 때문에 ElevatorScheduler 라는 인터페이스 를 사용하여 여러 전략들을 캡슐화하여 동적으로 선택할 수 있게 한다.
     */
    class ElevatorManager(controllerCount: Int) {
        private val controllers = mutableListOf<ElevatorController>()

        init {
            for( i in 0 until controllerCount) {
                val controller = ElevatorController(i + 1)  // <== 수정
                controllers.add(controller)
            }
        }

        // 요청에 따라 엘리베이터를 선택하고 이동시킴
        fun requestElevator(destination: Int, direction: Direction) {
            // 0..23
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            // StrategyPattern 을 사용해서 동적으로 스케줄러 변경
            // 오전에는 ResponseTimeScheduler, 오후에는 ThroughputScheduler
            val scheduler = if(hour < 12) {
                ResponseTimeScheduler()
            } else {
                ThroughputScheduler()
            }

            // ThroughputSchedulerBefore 를 이용해 엘리베이터 선택
            val selectedElevator = scheduler.selectElevator(this, destination, direction)

            // 선택된 엘리베이터를 이동시킴
            controllers[selectedElevator].getFloor(destination)
        }
    }

    interface ElevatorScheduler {
        fun selectElevator(manager: ElevatorManager, destination: Int, direction: Direction): Int
    }

    class ResponseTimeScheduler: ElevatorScheduler {
        override fun selectElevator(manager: ElevatorManager, destination: Int, direction: Direction): Int {
            return 1 // 임의 선택
        }
    }

    /* 엘리베이터 작업 처리량을 최대화시키는 전략의 클래스 */
    class ThroughputScheduler: ElevatorScheduler {
        override fun selectElevator(manager: ElevatorManager, destination: Int, direction: Direction): Int {
            return 0 // 임의 선택
        }
    }

    /**
     * 여러 가지 방식의 엘리베이터 스케줄링 방법 지원하기 - After
     *
     * 문제 2 해결방법
     * 엘리베이터 스케줄링 전략이 추가되거나 동적 스케줄링 방식으로 전략을 선택하도록 변경되면
     *      해당 스케줄링 전략을 지원하는 구체적인 클래스를 생성 해야할 뿐만 아니라
     *      ElevatorManager 클래스의 requestElevator() 메서드도 수정 할 수밖에 없다.
     *          requestElevator() 메서드의 책임: 1. 엘리베이터 선택, 2. 엘리베이터 이동
     *          즉, 엘리베이터를 선택하는 전략의 변경에 따라 requestElevator()가 변경되는 것은 바람직하지 않다.
     *
     * 새로운 스케줄링 전략이 추가되는 경우
     *      엘리베이터 노후화 최소화 전략
     *
     * 동적 스케줄링 방식이 변경되는 경우
     *      오전: 대기 시간 최소화 전략, 오후: 처리량 최대화 전략 -> 두 전략의 사용 시간을 서로 바꾸는 경우
     *
     * 해결책
     * 과정1
     * 주어진 기능을 실제로 제공하는 적절한 클래스 생성 작업을 별도의 클래스/메서드로 분리 시켜야 한다.
     *
     * 엘리베이터 스케줄링 전략에 일치하는 클래스를 생성하는 코드를 requestElevator 메서드에서 분리해 별도의 클래스/메서드를 정의한다.
     *      변경 전: ElevatorManager 클래스가 직접 ThroughputScheduler 객체와 ResponseTimeScheduler 객체를 생성
     *      변경 후: SchedulerFactory 클래스의 getScheduler() 메서드가 스케줄링 전략에 맞는 객체를 생성
     */

    enum class SchedulingStrategyID {
        RESPONSE_TIME,
        THROUGHPUT,
        DYNAMIC
    }

    class SchedulerFactory {
        companion object {
            fun getScheduler(schedulingStrategyID: SchedulingStrategyID): ElevatorSchedulerNew {
                return when(schedulingStrategyID) {
                    SchedulingStrategyID.RESPONSE_TIME -> {
                        // 대기시간 최소화 전략
                        ResponseTimeSchedulerNew()
                    }

                    SchedulingStrategyID.THROUGHPUT -> {
                        // 처리량 극대화 전략
                        ThroughputSchedulerNew()
                    }

                    SchedulingStrategyID.DYNAMIC -> {
                        // 0..23
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                        // StrategyPattern 을 사용해서 동적으로 스케줄러 변경
                        // 오전에는 ResponseTimeScheduler, 오후에는 ThroughputScheduler
                        if(hour < 12) {
                            ResponseTimeSchedulerNew()
                        } else {
                            ThroughputSchedulerNew()
                        }
                    }
                }
            }
        }
    }

//    class ElevatorManagerNew(controllerCount: Int, strategyID: SchedulingStrategyID) {
//        private val controllers = mutableListOf<ElevatorController>()
//        private var strategyID:SchedulingStrategyID = strategyID
//
//        init {
//            for( i in 0 until controllerCount) {
//                val controller = ElevatorController(i + 1)
//                controllers.add(controller)
//            }
//        }
//
//        // 실행 중에 다른 스케줄링 전략으로 지정 가능
//        fun setStrategyID(strategyID: SchedulingStrategyID) {
//            this.strategyID = strategyID
//        }
//
//        // 요청에 따라 엘리베이터를 선택하고 이동시킴
//        fun requestElevator(destination: Int, direction: Direction) {
//            // 주어진 전략 ID에 해당되는 ElevatorScheduler 를 사용함 (수정)
//            val scheduler = SchedulerFactory.getScheduler(strategyID)
//
//            // ThroughputSchedulerBefore 를 이용해 엘리베이터 선택
//            val selectedElevator = scheduler.selectElevator(this, destination, direction)
//
//            // 선택된 엘리베이터를 이동시킴
//            controllers[selectedElevator].getFloor(destination)
//        }
//    }

    interface ElevatorSchedulerNew {
        fun selectElevator(manager: ElevatorManagerNew, destination: Int, direction: Direction): Int
    }

    class ResponseTimeSchedulerNew: ElevatorSchedulerNew {
        override fun selectElevator(manager: ElevatorManagerNew, destination: Int, direction: Direction): Int {
            return 1 // 임의 선택
        }
    }

    /* 엘리베이터 작업 처리량을 최대화시키는 전략의 클래스 */
    class ThroughputSchedulerNew: ElevatorSchedulerNew {
        override fun selectElevator(manager: ElevatorManagerNew, destination: Int, direction: Direction): Int {
            return 0 // 임의 선택
        }
    }


    /**
     * 여러 가지 방식의 엘리베이터 스케줄링 방법 지원하기 - After
     *
     * 문제 2 해결방법
     * 엘리베이터 스케줄링 전략이 추가되거나 동적 스케줄링 방식으로 전략을 선택하도록 변경되면
     *      해당 스케줄링 전략을 지원하는 구체적인 클래스를 생성 해야할 뿐만 아니라
     *      ElevatorManager 클래스의 requestElevator() 메서드도 수정 할 수밖에 없다.
     *          requestElevator() 메서드의 책임: 1. 엘리베이터 선택, 2. 엘리베이터 이동
     *          즉, 엘리베이터를 선택하는 전략의 변경에 따라 requestElevator()가 변경되는 것은 바람직하지 않다.
     *
     * 새로운 스케줄링 전략이 추가되는 경우
     *      엘리베이터 노후화 최소화 전략
     *
     * 동적 스케줄링 방식이 변경되는 경우
     *      오전: 대기 시간 최소화 전략, 오후: 처리량 최대화 전략 -> 두 전략의 사용 시간을 서로 바꾸는 경우
     *
     * 해결책
     * 과정2
     * 동적 스케줄링 방식(DynamicScheduler)이라고 하면 여러 번 스케줄링 객체를 생성하지 않고 한 번 생성한 것을 계속해서 사용하는 것이 바람직할 수 있다.
     *
     * 싱글턴 패턴 을 활용한 엘리베이터 스케줄링 전략을 설계
     *      스케줄링 기능을 제공하는 ResponseTimeScheduler 클래스와 ThroughputScheduler 클래스는 오직 하나의 객체만 생성해서 사용하도록 한다.
     *      즉, 생성자를 통해 직접 객체를 생성하는 것이 허용되지 않아야 한다.
     *          이를 위해 각 생성자를 private으로 정의한다.
     *          대신 getInstance() 라는 정적 메서드로 객체 생성을 구현한다.
     */
    class ElevatorManagerNew(controllerCount: Int, strategyID: SchedulingStrategyID) {
        private val controllers = mutableListOf<ElevatorController>()
        private var strategyID: SchedulingStrategyID = strategyID

        init {
            for( i in 0 until controllerCount) {
                val controller = ElevatorController(i + 1)
                controllers.add(controller)
            }
        }

        // 실행 중에 다른 스케줄링 전략으로 지정 가능
        fun setStrategyID(strategyID: SchedulingStrategyID) {
            this.strategyID = strategyID
        }

        // 요청에 따라 엘리베이터를 선택하고 이동시킴
        fun requestElevator(destination: Int, direction: Direction) {
            // 주어진 전략 ID에 해당되는 ElevatorScheduler 를 사용함 (수정)
            val scheduler = SchedulerFactory2.getScheduler(strategyID)

            // ThroughputSchedulerBefore 를 이용해 엘리베이터 선택
            val selectedElevator = scheduler?.selectElevator(this, destination, direction)
            selectedElevator?.run {
                // 선택된 엘리베이터를 이동시킴
                controllers[selectedElevator].getFloor(destination)
            }
        }
    }

    class SchedulerFactory2 {
        companion object {
            fun getScheduler(schedulingStrategyID: SchedulingStrategyID): ElevatorSchedulerNew? {
                return when(schedulingStrategyID) {
                    SchedulingStrategyID.RESPONSE_TIME -> {
                        // 대기시간 최소화 전략
                        ResponseTimeScheduler2.getInstance()
                    }

                    SchedulingStrategyID.THROUGHPUT -> {
                        // 처리량 극대화 전략
                        ThroughputScheduler2.getInstance()
                    }

                    SchedulingStrategyID.DYNAMIC -> {
                        // 0..23
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                        // StrategyPattern 을 사용해서 동적으로 스케줄러 변경
                        // 오전에는 ResponseTimeScheduler, 오후에는 ThroughputScheduler
                        if(hour < 12) {
                            ResponseTimeScheduler2.getInstance()
                        } else {
                            ThroughputScheduler2.getInstance()
                        }
                    }
                }
            }
        }
    }

    // 싱글턴 패턴으로 구현한 ThroughtputScheduler 클래스
    class ThroughputScheduler2 {
        companion object {
            private var scheduler: ElevatorSchedulerNew? = null

            fun getInstance(): ElevatorSchedulerNew? {
                if(scheduler == null) scheduler = ThroughputSchedulerNew()
                return scheduler
            }
        }
    }

    // 싱글턴 패턴으로 구현한 ResponseTimeScheduler 클래스
    class ResponseTimeScheduler2 {
        companion object {
            private var scheduler: ElevatorSchedulerNew? = null

            fun getInstance(): ElevatorSchedulerNew? {
                if(scheduler == null) scheduler = ResponseTimeSchedulerNew()
                return scheduler
            }
        }
    }
}

enum class Direction {
    UP,
    DOWN
}

fun main() {
    /**
     * Before
     */
    println("--------------------------------- Before ---------------------------------")
    FactoryMethodPattern.ElevatorManagerBefore(2).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManagerBefore(2).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManagerBefore(2).also {
        it.requestElevator(10, Direction.UP)
    }

    /**
     * After
     */
    println("--------------------------------- After ---------------------------------")
    FactoryMethodPattern.ElevatorManager(2).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManager(2).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManager(2).also {
        it.requestElevator(10, Direction.UP)
    }

    println("--------------------------------- After 2 ---------------------------------")
    FactoryMethodPattern.ElevatorManagerNew(2, FactoryMethodPattern.SchedulingStrategyID.RESPONSE_TIME).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManagerNew(2, FactoryMethodPattern.SchedulingStrategyID.THROUGHPUT).also {
        it.requestElevator(10, Direction.UP)
    }

    FactoryMethodPattern.ElevatorManagerNew(2, FactoryMethodPattern.SchedulingStrategyID.DYNAMIC).also {
        it.requestElevator(10, Direction.UP)
    }
}