package structural.composite

import util.dividerWithMessage

interface Component {
    fun fn()
}

class Leaf : Component {
    override fun fn() {
        println("Leaf")
    }
}

class Composite : Component {

    private val components = mutableListOf<Component>()

    fun addComponent(component: Component) {
        components.add(component)
    }

    override fun fn() {
        println("Composite")
        components.forEach {
            it.fn()
        }
    }
}

fun main() {
    dividerWithMessage("composite 1")
    val composite1 = Composite()
    composite1.addComponent(Leaf())
    composite1.addComponent(Leaf())
    composite1.fn()

    dividerWithMessage("composite 0")
    val composite0 = Composite()
    composite0.addComponent(Leaf())
    composite0.addComponent(composite1)

    composite0.fn()
}