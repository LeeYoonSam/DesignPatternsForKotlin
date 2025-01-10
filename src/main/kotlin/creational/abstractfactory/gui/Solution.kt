package creational.abstractfactory.gui

class Solution {
    interface Button {
        fun render()
        fun handleClick()
    }

    interface Checkbox {
        fun render()
        fun toggle()
    }

    // 구체적인 Windows 제품들
    class WindowsButton : Button {
        override fun render() = println("Rendering Windows button")
        override fun handleClick() = println("Handling Windows button click")
    }

    class WindowsCheckbox : Checkbox {
        override fun render() = println("Rendering Windows checkbox")
        override fun toggle() = println("Toggling Windows checkbox")
    }

    // 구체적인 Mac 제품들
    class MacButton : Button {
        override fun render() = println("Rendering Mac button")
        override fun handleClick() = println("Handling Mac button click")
    }

    class MacCheckbox : Checkbox {
        override fun render() = println("Rendering Mac checkbox")
        override fun toggle() = println("Toggling Mac checkbox")
    }

    // 추상 팩토리 인터페이스

    interface GUIFactory {
        fun createButton(): Button
        fun createCheckbox(): Checkbox
    }

    // 구체적인 팩토리들
    class WindowsFactory : GUIFactory {
        override fun createButton(): Button = WindowsButton()
        override fun createCheckbox(): Checkbox = WindowsCheckbox()
    }

    class MacFactory : GUIFactory {
        override fun createButton(): Button = MacButton()
        override fun createCheckbox(): Checkbox = MacCheckbox()
    }

    // 팩토리 생성 클래스
    object GUIFactoryProvider {
        fun getFactory(): GUIFactory {
            return if (System.getProperty("os.name").contains("Windows")) {
                WindowsFactory()
            } else {
                MacFactory()
            }
        }
    }

    // GUI 애플리케이션
    class GUIApplication(private val factory: GUIFactory) {
        fun createUI() {
            // 버튼 생성 및 사용
            val button = factory.createButton()
            button.render()
            button.handleClick()

            // 체크박스 생성 및 사용
            val checkbox = factory.createCheckbox()
            checkbox.render()
            checkbox.toggle()
        }
    }
}

fun main() {
    // 현재 OS에 맞는 팩토리 생성
    val factory = Solution.GUIFactoryProvider.getFactory()

    // 애플리케이션 생성 및 UI 구성
    val app = Solution.GUIApplication(factory)
    app.createUI()

    // 다른 테마의 UI 생성도 가능
    println("\nCreating Window-style UI explicitly:")
    val windowApp = Solution.GUIApplication(Solution.WindowsFactory())
    windowApp.createUI()
}