package structural.decorator.coffee

class Solve {
    // 기본 커피 인터페이스
    interface Coffee {
        fun cost(): Double
        fun description(): String
    }

    // 기본 커피 구현
    class SimpleCoffee : Coffee {
        override fun cost(): Double = 3.0
        override fun description(): String = "Simple Coffee"
    }

    // 데코레이터 추상 클래스
    abstract class CoffeeDecorator(private val decoratedCoffee: Coffee) : Coffee {
        override fun cost(): Double = decoratedCoffee.cost()
        override fun description(): String = decoratedCoffee.description()
    }

    // 구체적인 데코레이터들
    class MilkDecorator(coffee: Coffee) : CoffeeDecorator(coffee) {
        override fun cost(): Double = super.cost() + 1.5
        override fun description(): String = "${super.description()}, milk"
    }

    class SugarDecorator(coffee: Coffee) : CoffeeDecorator(coffee) {
        override fun cost(): Double = super.cost() + 0.5
        override fun description(): String = "${super.description()}, sugar"
    }

    class WhippedCreamDecorator(coffee: Coffee) : CoffeeDecorator(coffee) {
        override fun cost(): Double = super.cost() + 2.0
        override fun description(): String = "${super.description()}, whipped cream"
    }
}

fun main() {
    // 기본 커피
    var myCoffee: Solve.Coffee = Solve.SimpleCoffee()
    println("${myCoffee.description()} - Cost: $${myCoffee.cost()}")

    // 우유 추가
    myCoffee = Solve.MilkDecorator(myCoffee)
    println("${myCoffee.description()} - Cost: $${myCoffee.cost()}")

    // 설탕 추가
    myCoffee = Solve.SugarDecorator(myCoffee)
    println("${myCoffee.description()} - Cost: $${myCoffee.cost()}")

    // 휘핑크림 추가
    myCoffee = Solve.WhippedCreamDecorator(myCoffee)
    println("${myCoffee.description()} - Cost: $${myCoffee.cost()}")
}