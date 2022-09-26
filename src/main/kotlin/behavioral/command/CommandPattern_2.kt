package behavioral.command

/**
 * Command Pattern
 */

fun main() {
    val firstCommand = PrintCommand("first command")
    val secondCommand = PrintCommand("second command")

    firstCommand.execute()
    secondCommand.execute()

    val baduk = Dog()
    val dogCommand = DogCommand(baduk, listOf("sit", "stay", "stay"))
    dogCommand.execute()

    // Invoker 적용해서 한꺼번에 실행
    val invoker = Invoker()
    invoker.run {
        addCommand(firstCommand)
        addCommand(dogCommand)
        addCommand(secondCommand)
        runCommands()
    }
}

interface Command {
    fun execute()
}


class PrintCommand(
    private val message: String
) : Command {

    override fun execute() {
        println(message)
    }
}

class Dog() {
    fun sit() {
        println("The dog sat down")
    }

    fun stay() {
        println("The dog is staying")
    }
}

class DogCommand(
    private val dog: Dog,
    private val commands: List<String>
) : Command {

    override fun execute() {
        commands.forEach {
            if (it == "sit") {
                dog.sit()
            } else if (it == "stay") {
                dog.stay()
            }
        }
    }
}

// Command 들을 모아서 한번에 실행
class Invoker {

    private val commands: MutableList<Command> = mutableListOf()

    fun addCommand(command: Command) {
        commands.add(command)
    }

    fun runCommands() {
        commands.forEach {
            it.execute()
        }
    }
}