package book.creational.factory.abstract

/**
 * server:
 *  port: 8080
 * environment: production
```
 */
interface Property {
    val name: String
    val value: Any
}

interface ServerConfiguration {
    val properties: List<Property>
}

data class PropertyImpl(
    override val name: String,
    override val value: Any
) : Property

data class ServerConfigurationImpl(
    override val properties: List<Property>
) : ServerConfiguration

fun property(prop: String): Property {
    val (name, value) = prop.split(":")
    return when (name) {
        "port" -> PropertyImpl(name, value.trim().toInt())
        "environment" -> PropertyImpl(name, value.trim())
        else -> throw RuntimeException("Unknown property: $name")
    }
}

fun main() {
    /**
     * there is a slight issue with this code
     */
    val portProperty = property("port: 8080")
    val environment = property("environment: production")

    /**
     * store the value of the port property into another variable
     *
     * result -> Type mismatch: inferred type is Any but Int was expected
     */
    // val port: Int = portProperty.value
}