package behavioral.visitor

/**
 * Visitor Pattern
 */

class Cat(
    val name: String,
    val age: Int
) {
    fun speak() {
        println("meow")
    }

    fun accept(visitor: Visitor) {
        println("use implementation of visitor")
        visitor.visit(this)
    }
}

interface Visitor {
    fun visit(cat: Cat)
}

class NameVisitor : Visitor {
    override fun visit(cat: Cat) {
        println(cat.name)
    }
}

class AgeVisitor : Visitor {
    override fun visit(cat: Cat) {
        println(cat.age)
    }
}

fun main() {
    val kitty = Cat("Kitty", 3)
    kitty.speak()

    val nameVisitor = NameVisitor()
    val ageVisitor = AgeVisitor()

    kitty.accept(nameVisitor)
    kitty.accept(ageVisitor)
}