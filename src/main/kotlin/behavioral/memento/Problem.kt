package behavioral.memento

/**
 * 문제점
 * - 객체 상태 저장/복원의 어려움
 * - 캡슐화 위반 위험
 * - 실행 취소 기능 구현의 복잡성
 * - 상태 히스토리 관리의 어려움
 * - 메모리 사용량 증가
 */
class Problem {
    class TextEditor {
        private var content: String = ""

        fun addContent(text: String) {
            content += text
        }

        fun getContent(): String = content

        // 실행 취소 기능을 구현하기 위해 직접 content를 수정
        fun setContent(text: String) {
            content = text
        }
    }
}

fun main() {
    val editor = Problem.TextEditor()
    val history = mutableListOf<String>()

    // 텍스트 추가 및 상태 저장
    editor.addContent("안녕하세요.")
    history.add(editor.getContent())

    editor.addContent("메멘토 패턴입니다.")
    history.add(editor.getContent())

    println("현재 텍스트: ${editor.getContent()}")

    // 실행 취소 - 직접 이전 상태 값을 설정
    if (history.size > 1) {
        history.removeLast()
        editor.setContent(history.last())
    }

    println("실행 취소 후: ${editor.getContent()}")
}