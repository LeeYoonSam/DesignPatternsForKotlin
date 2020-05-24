package behavioral

/**
 * 출처: https://gmlwjd9405.github.io/2018/07/07/command-pattern.html
 *
 * Command Pattern(행위 패턴)
 *
 * < 커맨드 패턴 >
 * 실행될 기능을 캡슐화함으로써 주어진 여러 기능을 실행할 수 있는 재사용성이 높은 클래스를 설계하는 패턴
 *      즉, 이벤트가 발생했을 때 실행될 기능이 다양하면서도 변경이 필요한 경우에 이벤트를 발생시키는 클래스를 변경하지 않고 재사용하고자 할때 유용
 *      실행될 기능을 캡슐화함으로써 기능의 실행을 요구하는 호출자 클래스와 실제 기능을 실행하는 수신자 클래스 사이의 의존성을 제거
 *      실행될 기능의 변경에도 호출자 클래스를 수정 없이 그대로 사용 할 수 있도록 해준다.
 *
 * 역할이 수행하는 작업
 *      Command
 *          실행될 기능에 대한 인터페이스
 *          실행될 기능을 execute 메서드로 선언
 *      ConcreateCommand
 *          실제로 실행되는 기능을 구현
 *          즉, Command 라는 인터페이스를 구현
 *      Invoker
 *          기능의 실행을 요청하는 호출자 클래스
 *      Receiver
 *          ConcreteCommand 에서 execute 메서드를 구현할 때 필요한 클래스
 *          즉, ConcreteCommand 의 기능을 실행하기 위해 사용하는 수신자 클래스
 *
 * 참고
 * > 행위 패턴이란?
 *      객체나 클래스 사이의 알고리즘이나 책임 분배에 관련된 패턴
 *      한 객체가 혼자 수행할 수 없는 작업을 여러 개의 객체로 어떻게 분배하는지, 또 그렇게 하면서도 객체 사이의 결합도를 최소화하는 것에 중점을 둔다.
 */
class CommandPattern {
    /**
     * 만능 버튼 만들기 - Before
     *
     * 버튼이 눌리면 램프의 불이 켜지는 프로그램
     *
     * 문제점
     * 1. 버튼을 눌렀을때 다른 기능을 실행하는 경우
     *      버튼을 눌렀을때 알람이 시작되게 하려면?
     *      새로운 기능으로 변경하려고 기존 코드(Button 클래스)의 내용을 수정해야 하므로 OCP에 위배된다.
     *      Button 클래스의 pressed() 전체를 변경해야 한다.
     *
     * 2. 버튼을 누르는 동작에 따라 다른 기능을 실행하는 경우
     *      버튼을 처음 눌렀을때는 램프를 켜고, 두 번째 눌렀을 때는 알람을 동작하게 하려면?
     *      필요한 기능을 새로 추가할 때마다 Button 클래스의 코드를 수정해야 하므로 재사용하기 어렵다.
     *
     */
    class LampBefore {
        fun turnOn() = println("Lamp On")
    }

    class ButtonBefore(private val theLamp: LampBefore) {
        fun pressed() = theLamp.turnOn()
    }


    /**
     * 만능 버튼 만들기 - After
     *
     * 해결책
     * 문제를 해결하기 위해서는 구체적인 기능을 직접 구현하는 대신 실행될 기능을 캡슐화 해야 한다.
     *
     * - 즉, Button 클래스의 pressed 메서드에서 구체적인 기능(램프 켜기, 알람 동작 등)을 직접 구현하는 대신 버튼을 눌렀을 때 실행될 기능을 Button 클래스 외부에서
     * 제공받아 캡슐화해 pressed 메서드에 호출한다.
     * - 이를 통해 Button 클래스 코드를 수정하지 않고 그대로 사용할 수 있다.
     * - Button 클래스는 미리 약속된 Command 인터페이스의 execute 메서드를 호출
     */
    interface Command {
        abstract fun execute()
    }

    class Button(theCommand: Command) {
        private var theCommand: Command = theCommand

        fun pressed() = theCommand.execute()

        fun setCommand(command: Command) {
            this.theCommand = command
        }
    }

    class Lamp {
        fun turnOn() = println("Lamp On")
    }

    class LampOnCommand(private val theLamp: Lamp): Command {
        override fun execute() = theLamp.turnOn()
    }

    class Alarm {
        fun start() = println("Alarming")
    }

    class AlarmCommand(private val theAlarm: Alarm): Command {
        override fun execute() = theAlarm.start()
    }
}

fun main() {
    /**
     * Before
     */
    println("--------------------------------- Before ---------------------------------")
    val lampBefore = CommandPattern.LampBefore()
    val lampButtonBefore = CommandPattern.ButtonBefore(lampBefore)
    lampButtonBefore.pressed()

    /**
     * After
     */
    println("--------------------------------- After ---------------------------------")
    val lamp = CommandPattern.Lamp()
    val lampOnCommand = CommandPattern.LampOnCommand(lamp)

    val alarm = CommandPattern.Alarm()
    val alarmCommand = CommandPattern.AlarmCommand(alarm)

    val button1 = CommandPattern.Button(lampOnCommand)
    button1.pressed()

    val button2 = CommandPattern.Button(alarmCommand)
    button2.pressed()
    button2.setCommand(lampOnCommand)
    button2.pressed()
}