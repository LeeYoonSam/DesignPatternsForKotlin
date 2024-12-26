package behavioral.command.editor

class Solve {
    // Command 인터페이스
    interface Command {
        fun execute()
        fun undo()
    }

    // Receiver: 실제 텍스트 처리를 담당
    class TextEditor {
        private var content: StringBuilder = StringBuilder()

        fun insert(text: String, position: Int) {
            content.insert(position, text)
        }

        fun delete(position: Int, length: Int): String {
            val deletedText = content.substring(position, position + length)
            content.delete(position, position + length)
            return deletedText
        }

        fun getContent(): String = content.toString()
    }

    // Concrete Commands
    class InsertTextCommand(
        private val editor: TextEditor,
        private val text: String,
        private val position: Int
    ) : Command {
        override fun execute() {
            editor.insert(text, position)
        }

        override fun undo() {
            editor.delete(position, text.length)
        }
    }

    class DeleteTextCommand(
        private val editor: TextEditor,
        private val position: Int,
        private val length: Int
    ) : Command {
        private lateinit var deletedText: String

        override fun execute() {
            deletedText = editor.delete(position, length)
        }

        override fun undo() {
            editor.insert(deletedText, position)
        }
    }

    // Invoker: 커맨드 실행과 이력 관리
    class EditorInvoker {
        private val history = mutableListOf<Command>()
        private val undoneCommands = mutableListOf<Command>()

        fun executeCommand(command: Command) {
            command.execute()
            history.add(command)
            undoneCommands.clear()
        }

        fun undo() {
            if (history.isNotEmpty()) {
                val command = history.removeAt(history.lastIndex)
                command.undo()
                undoneCommands.add(command)
            }
        }

        fun redo() {
            if (undoneCommands.isNotEmpty()) {
                val command = undoneCommands.removeAt(undoneCommands.lastIndex)
                command.execute()
                history.add(command)
            }
        }
    }
}

fun main() {
    val editor = Solve.TextEditor()
    val invoker = Solve.EditorInvoker()

    // 텍스트 추가
    invoker.executeCommand(Solve.InsertTextCommand(editor, "Hello ", 0))
    println("첫 번째 텍스트 추가 후: ${editor.getContent()}")

    // 더 많은 텍스트 추가
    invoker.executeCommand(Solve.InsertTextCommand(editor, "World!", 6))
    println("두 번째 텍스트 추가 후: ${editor.getContent()}")

    // 텍스트 삭제
    invoker.executeCommand(Solve.DeleteTextCommand(editor, 5, 7))
    println("텍스트 삭제 후: ${editor.getContent()}")

    // 실행 취소
    invoker.undo()
    println("실행 취소 후: ${editor.getContent()}")

    // 다시 실행
    invoker.redo()
    println("다시 실행 후: ${editor.getContent()}")
}