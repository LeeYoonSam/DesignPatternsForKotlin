package structural.unitofwork.transaction

import java.util.*

// 도메인 모델 정의
data class User(
    val id: UUID = UUID.randomUUID(),
    var username: String,
    var email: String
)

data class Product(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    var price: Double,
    var stockQuantity: Int
)

data class Order(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val orderDate: Date = Date(),
    val items: MutableList<OrderItem> = mutableListOf(),
    var status: OrderStatus = OrderStatus.CREATED
)

data class OrderItem(
    val id: UUID = UUID.randomUUID(),
    val productId: UUID,
    var quantity: Int,
    var priceAtOrder: Double
)

enum class OrderStatus {
    CREATED, PAID, SHIPPED, DELIVERED, CANCELLED
}

// 데이터베이스 예외 클래스
sealed class DatabaseException(message: String) : Exception(message)
class DatabaseConnectionException(message: String) : DatabaseException(message)
class DatabaseQueryException(message: String) : DatabaseException(message)
class OptimisticLockException(message: String) : DatabaseException(message)