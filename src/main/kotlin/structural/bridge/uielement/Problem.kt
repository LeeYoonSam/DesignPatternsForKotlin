package structural.bridge.uielement

/**
 * 문제점
 * - 단일 상속으로 인한 클래스 폭발
 * - 기능 확장 시 클래스 수가 기하급수적 증가
 * - 추상화와 구현의 강한 결합
 * - 유지보수의 어려움
 */
class Problem {
    // 문제가 있는 코드: 클래스 폭발 문제
    // 각 플랫폼별로 모든 UI 요소를 구현해야 함

    class WindowsButton {
        fun render() = println("Rendering Windows button")
        fun click() = println("Windows button clicked")
    }

    class MacButton {
        fun render() = println("Rendering Mac button")
        fun click() = println("Mac button clicked")
    }

    class WindowsCheckbox {
        fun render() = println("Rendering Windows checkbox")
        fun click() = println("Windows checkbox clicked")
    }

    class MacCheckbox {
        fun render() = println("Rendering Mac checkbox")
        fun click() = println("Mac checkbox clicked")
    }
}

fun main() {
    val winButton = Problem.WindowsButton()
    val macButton = Problem.MacButton()
    val winCheckbox = Problem.WindowsCheckbox()
    val macCheckbox = Problem.MacCheckbox()

    winButton.render()
    macButton.render()
    winCheckbox.render()
    macCheckbox.render()

    winButton.click()
    macButton.click()
    winCheckbox.click()
    macCheckbox.click()
}