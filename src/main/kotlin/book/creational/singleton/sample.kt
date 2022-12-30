package book.creational.singleton

import util.dividerWithMessage

// create instance
val myFavoriteMovies = listOf("Black Hawk Down", "Blade Runner")
val yourFavoriteMovies = listOf<String>()

// we both want to list the best movies in the Quick and Angry series
val myFavoriteQuickAndAngryMovies = listOf<String>()
val myFavoriteQuickAndAngryMovies2 = NoMoviesList
val yourFavoriteQuickAndAngryMovies = listOf<String>()

// Kotlin Singleton object
//object NoMoviesList

// generic List interface
object NoMoviesList : List<String> {
    internal object EmptyIterator : ListIterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun hasPrevious(): Boolean = false
        override fun nextIndex(): Int = 0
        override fun previousIndex(): Int = -1
        override fun next(): Nothing = throw NoSuchElementException()
        override fun previous(): Nothing = throw NoSuchElementException()
    }

    override val size = 0
    override fun contains(element: String): Boolean = false
    override fun containsAll(elements: Collection<String>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): String {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: String): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<Nothing> = EmptyIterator

    override fun lastIndexOf(element: String): Int {
        TODO("Not yet implemented")
    }

    override fun listIterator(): ListIterator<String> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<String> {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<String> {
        TODO("Not yet implemented")
    }
}

// we can access NoMoviesList from anywhere
fun testNoMoviesList() {
    val myFavoriteQuickAndAngryMovies = NoMoviesList
    val yourFavoriteQuickAndAngryMovies = NoMoviesList

    // 참조 타입의 주소값을 비교(reference comparison)
    println(myFavoriteQuickAndAngryMovies === yourFavoriteQuickAndAngryMovies) // true
}

// prints the list of our movies
fun printMovies(movies: List<String>) {
    for (movie in movies) {
        println(movie)
    }
}

object Logger {
    init {
        println("I was accessed for the first time")
        // Initialization loginc goes here
    }
    // More code goes here
}

fun main() {
    dividerWithMessage("No Movies List")
    testNoMoviesList()

    dividerWithMessage("print movies")
    // initial list of movies
    printMovies(myFavoriteMovies)

    dividerWithMessage("print empty movie")
    printMovies(myFavoriteQuickAndAngryMovies2)

    dividerWithMessage("print kotlin empty")
    printMovies(emptyList())
}