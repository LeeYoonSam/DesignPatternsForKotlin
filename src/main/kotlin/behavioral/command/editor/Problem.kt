package behavioral.command.editor

/**
 * 문제점
 * - 작업 요청과 실행의 강한 결합
 * - 실행 취소/재실행 구현의 어려움
 * - 작업 이력 관리의 복잡성
 * - 새로운 기능 추가 시 코드 수정 필요
 */
class Problem {
    // 문제가 있는 코드: 직접적인 텍스트 조작으로 인한 실행 취소/재실행 불가능
    class SimpleTextEditor {
        private var content: StringBuilder = StringBuilder()

        fun addText(text: String) {
            content.append(text)
        }

        fun deleteText(length: Int) {
            if (length <= content.length) {
                content.setLength(content.length - length)
            }
        }

        fun getContent(): String = content.toString()
    }
}

fun main() {
    val editor = Problem.SimpleTextEditor()

    editor.addText("Hello ")
    println("텍스트 추가 후: ${editor.getContent()}")

    editor.addText("World!")
    println("텍스트 추가 후: ${editor.getContent()}")

    editor.deleteText(6)
    println("텍스트 삭제 후: ${editor.getContent()}")

    println("실행 취소 기능 없음!")
}