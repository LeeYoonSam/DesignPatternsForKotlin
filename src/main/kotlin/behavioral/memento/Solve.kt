package behavioral.memento

import behavioral.memento.Solve.History
import behavioral.memento.Solve.TextEditor

class Solve {
    // 메멘토 클래스 - 에디터의 상태를 저장
    data class EditorMemento(
        private val content: String
    ) {
        fun getSavedContent() = content
    }

    // Originator 클래스
    class TextEditor {
        private var content: String = ""

        fun addContent(text: String) {
            content += text
        }

        fun getContent(): String = content

        // 현재 상태를 메멘토 객체로 저장
        fun save(): EditorMemento {
            return EditorMemento(content)
        }

        // 메멘토 객체로부터 상태 복원
        fun restore(memento: EditorMemento) {
            content = memento.getSavedContent()
        }
    }

    // Caretaker 클래스 - 히스토리 관리
    class History {
        private val mementos = mutableListOf<EditorMemento>()

        fun push(memento: EditorMemento) {
            mementos.add(memento)
        }

        fun pop(): EditorMemento? {
            if (mementos.size <= 1) return null
            mementos.removeAt(mementos.lastIndex)
            return mementos.lastOrNull()
        }
    }
}

fun main() {
    val editor = TextEditor()
    val history = History()

    // 초기 상태 저장
    history.push(editor.save())

    // 텍스트 추가 및 상태 저장
    editor.addContent("안녕하세요. ")
    history.push(editor.save())

    editor.addContent("메멘토 패턴입니다.. ")
    history.push(editor.save())

    println("현재 택스트: ${editor.getContent()}")

    // 실행 취소
    history.pop()?.let { memento ->
        editor.restore(memento)
        println("실행 취소 후: ${editor.getContent()}")
    }

    // 한 번 더 실행 취소
    history.pop()?.let { memento ->
        editor.restore(memento)
        println("두 번째 실행 취소 후: ${editor.getContent()}")
    }
}