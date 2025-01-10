package creational.abstractfactory.gui

/**
 * 문제점
 * - 플랫폼별 컴포넌트 생성 코드 중복
 * - 플랫폼 의존적인 코드
 * - 일관성 없는 객체 생성
 * - 확장성 부족
 * - 결합도 증가
 */
class Problem {
    interface Button {
        fun render()
        fun handleClick()
    }

    interface Checkbox {
        fun render()
        fun toggle()
    }

    class WindowsButton : Button {
        override fun render() = println("Rendering Windows button")
        override fun handleClick() = println("Handling Windows button click")
    }

    class WindowsCheckbox : Checkbox {
        override fun render() = println("Rendering Windows checkbox")
        override fun toggle() = println("Toggling Windows checkbox")
    }

    class MacButton : Button {
        override fun render() = println("Rendering Mac button")
        override fun handleClick() = println("Handling Mac button click")
    }

    class MacCheckbox : Checkbox {
        override fun render() = println("Rendering Mac checkbox")
        override fun toggle() = println("Toggling Mac checkbox")
    }

    // GUI 생성 및 관리
    class GUIApplication {
        private val isWindows = System.getProperty("os.name").contains("Windows")

        fun createButton() = if (isWindows) {
            WindowsButton()
        } else {
            MacButton()
        }

        fun createCheckbox() = if (isWindows) {
            WindowsCheckbox()
        } else {
            MacCheckbox()
        }
    }
}

fun main() {
    val app = Problem.GUIApplication()

    // 버튼 생성 및 사용
    val button = app.createButton()
    button.render()
    button.handleClick()

    // 체크박스 생성 및 사용
    val checkbox = app.createCheckbox()
    checkbox.render()
    checkbox.toggle()
}