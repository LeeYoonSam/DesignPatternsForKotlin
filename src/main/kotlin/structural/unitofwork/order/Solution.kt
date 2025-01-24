package structural.unitofwork.order

/**
 * 해결책: Unit of Work 패턴을 사용한 주문 처리 시스템
 */
class Solution {

    // 변경사항을 추적하는 Unit of Work
    class UnitOfWork {
        private val newOrders = mutableListOf<Order>()
        private val dirtyOrders = mutableListOf<Order>()
        private val dirtyProducts = mutableListOf<Product>()

        fun registerNew(order: Order) {
            newOrders.add(order)
        }

        fun registerDirty(order: Order) {
            dirtyOrders.add(order)
        }

        fun registerDirty(product: Product) {
            dirtyProducts.add(product)
        }

        fun commit() {
            try {
                // 트랜잭션 시작
                println("Transaction started")

                // 새로운 주문 저장
                newOrders.forEach { order ->
                    println("Saving new order: ${order.id}")
                    // 실제로는 데이터베이스에 저장
                }

                // 수정된 주문 업데이트
                dirtyOrders.forEach { order ->
                    println("Updating order: ${order.id}")
                    // 실제로는 데이터베이스에 업데이트
                }

                // 수정된 상품 업데이트
                dirtyProducts.forEach { product ->
                    println("Updating product: ${product.id}")
                    // 실제로는 데이터베이스에 업데이트
                }

                // 트랜잭션 커밋
                println("Transaction committed")

                // 변경사항 초기화
                newOrders.clear()
                dirtyOrders.clear()
                dirtyProducts.clear()
            } catch (e: Exception) {
                // 트랜잭션 롤백
                println("Transaction rolled back: ${e.message}")
                throw e
            }
        }
    }

    // 주문 처리 서비스
    class OrderProcessor(private val unitOfWork: UnitOfWork) {
        private val orders = mutableMapOf(
            "ORD-001" to Order("ORD-001", "PREPARE", mutableListOf(OrderItem("PROD-001", 1))),
            "ORD-002" to Order("ORD-002", "PREPARE", mutableListOf(OrderItem("PROD-001", 4), OrderItem("2", 3))),
            "ORD-003" to Order("ORD-003", "PAYMENT", mutableListOf(OrderItem("PROD-002", 3), OrderItem("6", 2))),
        )

        private val products = mutableMapOf(
            "PROD-001" to Product(
                id = "PROD-001",
                name = "Product1",
                stockQuantity = 10
            ),
            "PROD-002" to Product(
                id = "PROD-002",
                name = "Product2",
                stockQuantity = 20
            ),
            "PROD-003" to Product(
                id = "PROD-003",
                name = "Product3",
                stockQuantity = 30
            ),
            "PROD-004" to Product(
                id = "PROD-004",
                name = "Product4",
                stockQuantity = 40
            ),
            "PROD-005" to Product(
                id = "PROD-005",
                name = "Product5",
                stockQuantity = 4
            ),
            "PROD-006" to Product(
                id = "PROD-006",
                name = "Product6",
                stockQuantity = 3
            ),
            "PROD-007" to Product(
                id = "PROD-007",
                name = "Product7",
                stockQuantity = 1
            ),
        )

        fun processOrder(orderId: String, productId: String, quantity: Int) {
            try {
                val order = orders[orderId] ?: throw IllegalArgumentException("Order not found")
                val product = products[productId] ?: throw IllegalArgumentException("Product not found")

                // 재고 확인
                if (product.stockQuantity < quantity) {
                    throw IllegalStateException("Insufficient stock")
                }

                // 재고 감소
                product.stockQuantity -= quantity
                unitOfWork.registerDirty(product)

                // 주문 상태 업데이트
                order.status = "PROCESSING"
                order.items.add(OrderItem(productId, quantity))
                unitOfWork.registerDirty(order)

                // 모든 변경사항 커밋
                unitOfWork.commit()
            } catch (e: Exception) {
                // 예외 발생 시 자동으로 롤백됨
                throw e
            }
        }
    }

    data class Order(
        val id: String,
        var status: String,
        val items: MutableList<OrderItem> = mutableListOf()
    )

    data class Product(
        val id: String,
        val name: String,
        var stockQuantity: Int
    )

    data class OrderItem(
        val productId: String,
        val quantity: Int
    )
}

fun main() {
    println("Unit of Work Implementation Demo:")
    val unitOfWork = Solution.UnitOfWork()
    val processor = Solution.OrderProcessor(unitOfWork)

    try {
        // 주문 처리 시도
        processor.processOrder("ORD-001", "PROD-001", 15)
        println("Order processed successfully with transaction")
    } catch (e: Exception) {
        println("Error processing order: ${e.message}")
        // Unit of Work가 자동으로 롤백 처리
    }
}