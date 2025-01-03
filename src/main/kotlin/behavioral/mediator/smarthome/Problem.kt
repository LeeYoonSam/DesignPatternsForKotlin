package behavioral.mediator.smarthome

/**
 * 문제점
 * - 객체간 강한 결합
 * - 복잡한 다대다 통신
 * - 변경의 어려움
 * - 재사용성 저하
 */
class Problem {
    // 문제가 있는 코드: 직접적인 객체 간 통신
    class SmartDeviceWithoutMediator(val name: String) {
        private val otherDevices = mutableListOf<SmartDeviceWithoutMediator>()

        fun addDevice(device: SmartDeviceWithoutMediator) {
            otherDevices.add(device)
        }

        fun sendCommand(command: String) {
            println("$name: $command")

            // 모든 디바이스에 직접 통신
            otherDevices.forEach { device ->
                device.receiveCommand(this, command)
            }
        }

        private fun receiveCommand(sender: SmartDeviceWithoutMediator, command: String) {
            println("$name received command from ${sender.name}: $command")
        }
    }
}

fun main() {
    val smartDevice = Problem.SmartDeviceWithoutMediator("MyHome")

    val tv = Problem.SmartDeviceWithoutMediator("TV")
    val computer = Problem.SmartDeviceWithoutMediator("COMPUTER")

    smartDevice.addDevice(tv)
    smartDevice.addDevice(computer)

    smartDevice.sendCommand("Turn on")
}