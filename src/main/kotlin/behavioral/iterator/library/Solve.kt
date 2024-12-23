package behavioral.iterator.library

class Solve {
    // 반복자 인터페이스
    interface BookIterator {
        fun hasNext(): Boolean
        fun next(): String
        fun reset()
    }

    // 집합체 인터페이스
    interface BookCollection {
        fun createIterator(): BookIterator
        fun addBook(book: String)
        fun getBookCount(): Int
    }

    // 구체적인 반복자
    class BookStoreIterator(private val books: List<String>) : BookIterator {
        private var currentIndex = 0

        override fun hasNext(): Boolean = currentIndex < books.size

        override fun next(): String {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            return books[currentIndex++]
        }

        override fun reset() {
            currentIndex = 0
        }
    }

    // 구체적인 집합체
    class ModernBookStore : BookCollection {
        private val books  = mutableListOf<String>()

        override fun createIterator(): BookIterator = BookStoreIterator(books)

        override fun addBook(book: String) {
            books.add(book)
        }

        override fun getBookCount(): Int = books.size
    }

    class GenreIterator(private val booksByGenre: Map<String, List<String>>) : BookIterator {
        private val genres = booksByGenre.keys.toList()
        private var genreIndex = 0
        private var bookIndex = 0

        override fun hasNext(): Boolean {
            while (genreIndex < genres.size) {
                val currentGenreBooks = booksByGenre[genres[genreIndex]] ?: emptyList()
                if (bookIndex < currentGenreBooks.size) {
                    return true
                }
                genreIndex++
                bookIndex = 0
            }
            return false
        }

        override fun next(): String {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            val currentGenreBooks = booksByGenre[genres[genreIndex]] ?: emptyList()
            return "${genres[genreIndex]}: ${currentGenreBooks[bookIndex++]}"
        }

        override fun reset() {
            genreIndex = 0
            bookIndex = 0
        }
    }

    class GenreBookStore : BookCollection {
        private val booksByGenre = mutableMapOf<String, MutableList<String>>()

        fun addBook(genre: String, book: String) {
            booksByGenre.getOrPut(genre) { mutableListOf() }.add(book)
        }

        override fun addBook(book: String) {
            addBook("General", book)
        }

        override fun createIterator(): BookIterator = GenreIterator(booksByGenre)

        override fun getBookCount(): Int = booksByGenre.values.sumOf { it.size }
    }
}

fun main() {
    // 일반 책방 테스트
    val bookStore = Solve.ModernBookStore()
    bookStore.addBook("The Great Gatsby")
    bookStore.addBook("1984")
    bookStore.addBook("Animal Farm")

    println("일반 책방의 모든 책:")
    val iterator = bookStore.createIterator()
    while (iterator.hasNext()) {
        println(iterator.next())
    }

    // 장르별 책방 테스트
    val genreBookStore = Solve.GenreBookStore()
    genreBookStore.addBook("Fiction", "The Lord of the Rings")
    genreBookStore.addBook("Fiction", "Due")
    genreBookStore.addBook("Science", "A Brief History of Time")
    genreBookStore.addBook("Science", "The Selfish Gene")

    println("\n장르별 책방의 모든 책:")
    val genreIterator = genreBookStore.createIterator()
    while (genreIterator.hasNext()) {
        println(genreIterator.next())
    }
}