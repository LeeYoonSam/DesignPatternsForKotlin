package creational.factorymethod

/**
 * Factory Method Pattern
 */

open class Animal {
    open fun speak() {}
}

class Cat : Animal() {
    override fun speak() {
        println("meow")
    }
}

class Dog : Animal() {
    override fun speak() {
        println("bark")
    }
}

abstract class AnimalFactory {
    abstract fun createAnimal(): Animal
}

class CatFactory : AnimalFactory() {

    var _catCount = 0

    override fun createAnimal(): Cat {
        _catCount += 1
        return Cat()
    }

    fun getCatCount(): Int {
        return _catCount
    }
}

class DogManager : AnimalFactory() {
    var dog: Dog? = null

    fun haveDog(): Dog {
        return if (dog == null) {
            createAnimal()
        } else {
            dog as Dog
        }
    }

    override fun createAnimal(): Dog {
        return Dog()
    }

    fun makeWings(dog: Dog): Dog {
        print("dog wings added")
        return dog
    }
}

fun main() {
    val catFactory = CatFactory()
    val cat = catFactory.createAnimal()
    println("${catFactory.getCatCount()} cats are created")

    val dogManager = DogManager()
    val dog = dogManager.haveDog()
    dogManager.makeWings(dog)
}