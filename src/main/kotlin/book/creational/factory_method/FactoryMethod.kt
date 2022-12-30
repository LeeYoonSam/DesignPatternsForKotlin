package book.creational.factory_method

interface ChessPiece {
    val file: Char
    val rank: Char
}

data class Pawn(
    override val file: Char,
    override val rank: Char
): ChessPiece

data class Queen(
    override val file: Char,
    override val rank: Char
): ChessPiece

fun createPiece(notation: String): ChessPiece {
    val (type, file, rank) = notation.toCharArray()

    return when(type) {
        'q' -> Queen(file, rank)
        'p' -> Pawn(file, rank)
        // ...
        else -> throw RuntimeException("Unknown piece: $type")
    }
}

// More pieces here
fun morePieces() {
    val notations = listOf("pa8", "qc3")
    val pieces = mutableListOf<ChessPiece>()

    for (n in notations) {
        pieces.add(createPiece(n))
    }

    println(pieces)
}

fun destructuringDeclaration() {
    val notation: String = "qc8"
    val type = notation.toCharArray()[0]
    val file = notation.toCharArray()[1]
    val rank = notation.toCharArray()[2]

    println("type: $type, file: $file, rank: $rank")
}

fun main() {
    destructuringDeclaration()
}