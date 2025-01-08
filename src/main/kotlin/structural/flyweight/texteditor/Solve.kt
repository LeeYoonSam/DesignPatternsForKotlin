package structural.flyweight.texteditor

class Solve {
    // 공유할 내부 상태 (Flyweight)
    data class FontStyle(
        val font: String,
        val size: Int,
        val color: String,
        val bold: Boolean,
        val italic: Boolean
    )

    // Flyweight Factory
    object FontStyleFactory {
        private val styles = mutableMapOf<String, FontStyle>()

        fun getFontStyle(
            font: String,
            size: Int,
            color: String,
            bold: Boolean,
            italic: Boolean
        ): FontStyle {
            val key = "$font-$size-$color-$bold-$italic"
            return styles.getOrPut(key) {
                FontStyle(font, size, color, bold, italic)
            }
        }

        fun getStylesCount() = styles.size
    }

    // 외부 상태를 포함한 문자 클래스
    data class Character(
        val char: Char,
        val style: FontStyle,   // 공유되는 내부 상태
        val x: Int,             // 외부 상태
        val y: Int              // 외부 상태
    )

    class TextEditor {
        private val characters = mutableListOf<Character>()

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
            val style = FontStyleFactory.getFontStyle(
                font = font,
                size = size,
                color = color,
                bold = bold,
                italic = italic
            )
            characters.add(Character(char = char, style = style, x = x, y = y))
        }

        fun render() {
            characters.forEach { char ->
                println("Rendering '${char.char}' at (${char.x}, ${char.y}) with " +
                        "font: ${char.style.font}, size: ${char.style.size}, " +
                        "color: ${char.style.color}, bold: ${char.style.bold}, " +
                        "italic: ${char.style.italic}")
            }
        }

        fun getMemoryUsage(): Int {
            return characters.size * 3 + // 문자와 위치 정보
                FontStyleFactory.getStylesCount() * 5 // 공유되는 스타일 정보
        }
    }
}

fun main() {
    val editor = Solve.TextEditor()

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

    // 다른 스타일의 텍스트 추가
    x = 0
    text.forEach { char ->
        editor.addCharacter(
            char = char,
            font = "Arial",
            size = 14,
            color = "red",
            bold = true,
            italic = false,
            x = x,
            y = 20
        )
        x += 12
    }

    editor.render()
    println("\nTotal unique font styles: ${Solve.FontStyleFactory.getStylesCount()}")
    println("Memory usage: ${editor.getMemoryUsage()} units")
}