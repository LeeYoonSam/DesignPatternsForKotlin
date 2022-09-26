package creational.factory

import util.divider
import util.dividerWithMessage
import util.linebreak

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

enum class AnimalType {
    CAT,
    DOG
}

// 팩토리 함수로 구현
fun animalFactory(animalType: AnimalType): Animal {
    return when(animalType) {
        AnimalType.CAT -> Cat()
        AnimalType.DOG -> Dog()
    }
}

// 팩토리 클래스로 구현
class AnimalFactory {
    fun createAnimal(animalType: AnimalType): Animal {
        return when(animalType) {
            AnimalType.CAT -> Cat()
            AnimalType.DOG -> Dog()
        }
    }
}

fun main() {
    // 팩토리 함수 방식
    dividerWithMessage("Factory Function")
    val cat = animalFactory(AnimalType.CAT)
    cat.speak()
    val dog = animalFactory(AnimalType.DOG)
    dog.speak()

    linebreak(2)

    // 팩토리 클래스 방식
    dividerWithMessage("Factory Class")
    val factory = AnimalFactory()
    val cat2 = factory.createAnimal(AnimalType.CAT)
    val dog2 = factory.createAnimal(AnimalType.DOG)
    cat2.speak()
    dog2.speak()
}