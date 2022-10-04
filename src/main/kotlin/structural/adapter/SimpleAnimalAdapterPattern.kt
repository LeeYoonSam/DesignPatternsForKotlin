package structural.adapter

interface Animal {
    fun walk()
}

class Cat: Animal {
    override fun walk() {
        println("cat working")
    }
}

class Dog: Animal {
    override fun walk() {
        println("dog working")
    }
}

fun makeWalk(animal: Animal) {
    animal.walk()
}

// 새로운 타입인 물고기, 걸을수 없음
class Fish {
    fun swim() {
        println("fish swimming")
    }
}

class FishAdapter(private val fish: Fish): Animal {
    override fun walk() {
        fish.swim()
    }
}

fun main() {
    val kitty = Cat()
    val bingo = Dog()

    makeWalk(kitty)
    makeWalk(bingo)

    val nimo = Fish()
    val fishAdapter = FishAdapter(nimo)
    makeWalk(fishAdapter)
}