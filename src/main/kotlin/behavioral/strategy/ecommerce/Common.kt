package behavioral.strategy.ecommerce

import java.math.BigDecimal
import java.time.LocalDateTime

// 문제점을 보여주는 데이터 클래스들
data class Order(
    val items: List<OrderItem>,
    val customerType: CustomerType,
    val totalAmount: BigDecimal,
    val purchaseDate: LocalDateTime
)

data class OrderItem(
    val product: Product,
    val quantity: Int
)

data class Product(
    val name: String,
    val category: ProductCategory,
    val basePrice: BigDecimal
)

enum class CustomerType {
    REGULAR, PREMIUM, VIP
}

enum class ProductCategory {
    ELECTRONICS, CLOTHING, BOOKS, FURNITURE
}