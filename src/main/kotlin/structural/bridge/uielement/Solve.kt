package structural.bridge.uielement

class Solve {
    interface UIElementImplementation {
        fun render()
        fun handleInput()
    }

    // 구체적인 구현부
    class WindowsImplementation : UIElementImplementation {
        override fun render() = println("Rendering on Windows")
        override fun handleInput() = println("Handling Windows input")
    }

    class MacImplementation : UIElementImplementation {
        override fun render() = println("Rendering on Mac")
        override fun handleInput() = println("Handling Mac input")
    }

    // 추상화 계층
    abstract class UIElement(protected val implementation: UIElementImplementation) {
        abstract fun draw()
        abstract fun processInput()
    }

    // 정제된 추상화
    class Button(implementation: UIElementImplementation) : UIElement(implementation) {
        override fun draw() {
            println("Drawing Button:")
            implementation.render()
        }

        override fun processInput() {
            println("Processing Button Input:")
            implementation.handleInput()
        }
    }

    class Checkbox(implementation: UIElementImplementation) : UIElement(implementation) {
        override fun draw() {
            println("Drawing Checkbox:")
            implementation.render()
        }

        override fun processInput() {
            println("Processing Checkbox Input:")
            implementation.handleInput()
        }
    }
}

fun main() {
    val windowsImpl = Solve.WindowsImplementation()
    val macImpl = Solve.MacImplementation()

    val windowsButton = Solve.Button(windowsImpl)
    val macButton = Solve.Button(macImpl)
    val windowsCheckbox = Solve.Checkbox(windowsImpl)
    val macCheckbox = Solve.Checkbox(macImpl)

    windowsButton.draw()
    windowsButton.processInput()

    macButton.draw()
    macButton.processInput()

    windowsCheckbox.draw()
    windowsCheckbox.processInput()

    macCheckbox.draw()
    macCheckbox.processInput()
}