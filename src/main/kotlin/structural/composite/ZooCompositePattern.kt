package structural.composite

interface Animal {
    fun speak()
}

class Cat : Animal {
    override fun speak() {
        println("meow")
    }
}

class Dog : Animal {
    override fun speak() {
        println("bark")
    }
}

class AnimalGroup : Animal {

    private val animals = mutableListOf<Animal>()

    fun add(animal: Animal) {
        animals.add(animal)
    }

    override fun speak() {
        println("group speaking..")
        animals.forEach {
            it.speak()
        }
    }
}

fun main() {
    val catGroup = AnimalGroup()
    repeat(3) {
        catGroup.add(Cat())
    }

    val dogGroup = AnimalGroup()
    repeat(2) {
        dogGroup.add(Dog())
    }

    val zoo = AnimalGroup()
    zoo.add(catGroup)
    zoo.add(dogGroup)

    zoo.speak()
}