package structural.decorator.dynamicproxy

interface UserService {
    fun getUser(id: Long): User
    fun createUser(name: String, email: String): User
    fun updateUser(id: Long, name: String, email: String): User
    fun deleteUser(id: Long): Boolean
}

data class User(val id: Long, val name: String, val email: String)

class UserServiceImpl : UserService {
    private val users = mutableMapOf<Long, User>()
    private var nextId = 1L

    override fun getUser(id: Long): User {
        Thread.sleep(100)
        return users[id] ?: throw NoSuchElementException("User not found with id: $id")
    }

    override fun createUser(name: String, email: String): User {
        Thread.sleep(300)
        val user = User(nextId++, name, email)
        users[user.id] = user
        return user
    }

    override fun updateUser(id: Long, name: String, email: String): User {
        Thread.sleep(200)
        if (!users.containsKey(id)) {
            throw NoSuchElementException("User not found with id: $id")
        }
        val updatedUser = User(id, name, email)
        users[id] = updatedUser
        return updatedUser
    }

    override fun deleteUser(id: Long): Boolean {
        Thread.sleep(100)
        if (!users.containsKey(id)) {
            throw NoSuchElementException("User not found with id: $id")
        }
        users.remove(id)
        return true
    }
}

interface OrderService {
    fun placeOrder(userId: Long, productId: Long, quantity: Int): Order
    fun getOrder(id: Long): Order
    fun cancelOrder(id: Long): Boolean
}

data class Order(val id: Long, val userId: Long, val productId: Long, val quantity: Int, val status: String)
class OrderServiceImpl : OrderService {
    private val orders = mutableMapOf<Long, Order>()
    private var nextId = 1L

    override fun placeOrder(userId: Long, productId: Long, quantity: Int): Order {
        Thread.sleep(300)
        val order = Order(nextId++, userId, productId, quantity, "PLACED")
        orders[order.id] = order
        return order
    }

    override fun getOrder(id: Long): Order {
        Thread.sleep(200)
        return orders[id] ?: throw NoSuchElementException("Order not found with id: $id")
    }

    override fun cancelOrder(id: Long): Boolean {
        Thread.sleep(100)
        if (!orders.containsKey(id)) {
            throw NoSuchElementException("Order not found with id: $id")
        }
        val order = orders[id]!!
        orders[id] = order.copy(status = "CANCELED")
        return true
    }
}