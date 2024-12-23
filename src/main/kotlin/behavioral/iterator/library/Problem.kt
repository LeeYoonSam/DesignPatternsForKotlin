package behavioral.iterator.library

/**
 * 문제 코드: 다양한 컬렉션에 대한 비일관적인 접근
 *
 * 문제점
 * - 컬렉션마다 다른 순회 방식
 * - 내부 구현 노출
 * - 일관성 없는 접근 방식
 * - 확장성 부족
 */
class Problem {
    class BookStore {
        private val books = mutableListOf<String>()

        fun addBook(book: String) {
            books.add(book)
        }

        fun displayBooks() {
            // 직접적인 내부 리스트 접근
            for (book in books) {
                println(book)
            }
        }
    }

    class Library {
        private val books = arrayOfNulls<String>(10)
        private var index = 0

        fun addBook(book: String) {
            // 배열 크기 고정으로 인한 제한
            if (index < books.size) {
                books[index++] = book
            }
        }

        fun showAllBooks() {
            // 다른 방식의 순회
            for (i in 0 until index) {
                println(books[i])
            }
        }
    }
}

fun main() {
    val bookStore = Problem.BookStore()
    bookStore.addBook("The Great Gatsby")
    bookStore.addBook("1984")

    println("BookStore books:")
    bookStore.displayBooks()

    val library = Problem.Library()
    library.addBook("Pride and Prejudice")

    println("\nLibrary books:")
    library.showAllBooks()
}