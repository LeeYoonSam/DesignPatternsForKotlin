package behavioral.mediator.smarthome

class Solve {
    interface SmartHomeMediator {
        fun sendCommand(sender: SmartDevice, command: String)
        fun registerDevice(device: SmartDevice)
    }

    abstract class SmartDevice(
        protected val mediator: SmartHomeMediator,
        val name: String
    ) {
        abstract fun sendCommand(command: String)
        abstract fun receivedCommand(command: String)
    }

    class SmartHomeMediatorImpl: SmartHomeMediator {
        private val devices = mutableListOf<SmartDevice>()

        override fun registerDevice(device: SmartDevice) {
            devices.add(device)
        }

        override fun sendCommand(sender: SmartDevice, command: String) {
            println("\n${sender.name}이(가) 명령을 전송: $command")

            when {
                command.contains("온도") -> {
                    // 온도 관련 명령은 에어컨과 온도계에만 전달
                    devices.filter {
                        it is AirConditioner || it is Thermometer
                    }.forEach {
                        if (it != sender) it.receivedCommand(command)
                    }
                }
                command.contains("조명") -> {
                    // 조명 관련 명령은 조명과 모션센서에만 전달
                    devices.filter {
                        it is Light || it is MotionSensor
                    }.forEach {
                        if (it != sender) it.receivedCommand(command)
                    }
                }
                command.contains("보안") -> {
                    // 보안 관련 명령은 모든 디바이스에 전달
                    devices.forEach {
                        if (it != sender) it.receivedCommand(command)
                    }
                }
            }
        }
    }

    class AirConditioner(mediator: SmartHomeMediator): SmartDevice(mediator, "에어컨") {
        override fun sendCommand(command: String) {
            mediator.sendCommand(this, command)
        }

        override fun receivedCommand(command: String) {
            println("$name 처리: $command")
            when {
                command.contains("온도 올림") -> println("$name: 온도를 올립니다.")
                command.contains("온도 내림") -> println("$name: 온도를 내립니다.")
                command.contains("보안") -> println("$name: 전원을 종료합니다.")
            }
        }
    }

    class Thermometer(mediator: SmartHomeMediator): SmartDevice(mediator, "온도계") {
        override fun sendCommand(command: String) {
            mediator.sendCommand(this, command)
        }

        override fun receivedCommand(command: String) {
            println("$name 처리: $command")
            when {
                command.contains("온도") -> println("$name: 현재 온도를 측정합니다.")
                command.contains("보안") -> println("$name: 온도 모니터링을 시작합니다.")
            }
        }
    }

    class Light(mediator: SmartHomeMediator): SmartDevice(mediator, "조명") {
        override fun sendCommand(command: String) {
            mediator.sendCommand(this, command)
        }

        override fun receivedCommand(command: String) {
            println("$name 처리: $command")
            when {
                command.contains("조명 켜기") -> println("$name: 조명을 켭니다.")
                command.contains("조명 끄기") -> println("$name: 조명을 끕니다.")
                command.contains("보안") -> println("$name: 보안 모드로 전환합니다.")
            }
        }
    }

    class MotionSensor(mediator: SmartHomeMediator): SmartDevice(mediator, "모션센서") {
        override fun sendCommand(command: String) {
            mediator.sendCommand(this, command)
        }

        override fun receivedCommand(command: String) {
            println("$name 처리: $command")
            when {
                command.contains("조명") -> println("$name: 동작 감지를 시작합니다.")
                command.contains("보안") -> println("$name: 보안 감시를 강화합니다.")
            }
        }
    }
}

fun main() {
    // Mediator 생성
    val mediator = Solve.SmartHomeMediatorImpl()

    // 디바이스 생성 및 등록
    val airConditioner = Solve.AirConditioner(mediator)
    val thermometer = Solve.Thermometer(mediator)
    val light = Solve.Light(mediator)
    val motionSensor = Solve.MotionSensor(mediator)

    mediator.registerDevice(airConditioner)
    mediator.registerDevice(thermometer)
    mediator.registerDevice(light)
    mediator.registerDevice(motionSensor)

    println("1. 온도 관련 명령 테스트")
    thermometer.sendCommand("온도 체크 필요")
    airConditioner.sendCommand("온도 올림")

    println("\n2. 조명 관련 명령 테스트")
    motionSensor.sendCommand("조명 켜기")
    light.sendCommand("조명 끄기")

    println("\n3. 보안 관련 명령 테스트")
    motionSensor.sendCommand("보안 경계 강화")
}