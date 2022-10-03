package creational.singleton

/**
 * Singleton Pattern
 */
object Cat {
    var name: String = ""
    fun speak() = println("$name meow")
}

fun main() {
    val cat1 = Cat.apply { name = "고양이1" }
    println(cat1.speak())

    val cat2 = Cat.apply { name = "고양이2" }
    println(cat2.speak())

    println("cat1 == cat2: ${cat1 == cat2}")
    println("cat1 === cat2: ${cat1 === cat2}")
}