package structural.flyweight.texteditor

/**
 * 문제점
 * - 대량의 유사 객체 생성으로 인한 메모리 부족
 * - 객체 생성/관리 비용 증가
 * - 중복 데이터 저장
 * - 리소스 낭비
 * - 성능 저하
 */
class Problem {
    data class TextCharacter(
        val char: Char,
        val font: String,
        val size: Int,
        val color: String,
        val bold: Boolean,
        val italic: Boolean,
        val x: Int,
        val y: Int
    )

    class TextEditor {
        private val characters = mutableListOf<TextCharacter>()

        fun addCharacter(
            char: Char,
            font: String,
            size: Int,
            color: String,
            bold: Boolean,
            italic: Boolean,
            x: Int,
            y: Int
        ) {
            val textChar = TextCharacter(
                char = char,
                font = font,
                size = size,
                color = color,
                bold = bold,
                italic = italic,
                x = x,
                y = y
            )
            characters.add(textChar)
        }

        fun render() {
            characters.forEach { char ->
                println("Rendering '${char.char}' at (${char.x}, ${char.y}) with " +
                        "font: ${char.font}, size: ${char.size}, color: ${char.color}, " +
                        "bold: ${char.bold}, italic: ${char.italic}")
            }
        }

        fun getMemoryUsage(): Int {
            return characters.size * 8
        }
    }
}

fun main() {
    val editor = Problem.TextEditor()

    val text = "Hello World"
    var x = 0
    text.forEach { char ->
        editor.addCharacter(
            char = char,
            font = "Arial",
            size = 12,
            color = "black",
            bold = false,
            italic = false,
            x = x,
            y = 0
        )
        x += 10
    }

    editor.render()
    println("Memory usage: ${editor.getMemoryUsage()} units")
}