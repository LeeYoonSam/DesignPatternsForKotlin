package architecture.saga.order

import java.util.*

class Problem {
    // 전통적인 모놀리식 주문 처리 시스템
    data class Order(
        val orderId: String,
        var status: String,
        val customerId: String,
        val amount: Double
    )

    data class Payment(
        val paymentId: String,
        val orderId: String,
        val amount: Double,
        var status: String
    )

    data class Inventory(
        val productId: String,
        var quantity: Int
    )

    // 모놀리식 서비스
    class OrderService {
        private val orders = mutableMapOf<String, Order>()
        private val payments = mutableMapOf<String, Payment>()
        private val inventory = mutableMapOf<String, Inventory>()

        // 단일 트랜잭션으로 처리되는 주문 프로세스
        fun processOrder(orderId: String, customerId: String, productId: String, amount: Double) {
            try {
                // 1. 주문 생성
                val order = Order(orderId, "PENDING", customerId, amount)
                orders[orderId] = order

                // 2. 재고 확인 및 차감
                val product = inventory[productId] ?: throw IllegalStateException("Product not found")
                if (product.quantity < 1) {
                    throw IllegalStateException("Out of stock")
                }
                product.quantity--

                // 3. 결제 처리
                val payment = Payment(UUID.randomUUID().toString(), orderId, amount, "COMPLETED")
                payments[payment.paymentId] = payment

                // 4. 주문 완료
                order.status = "COMPLETED"
            } catch (e: Exception) {
                // 실패 시 모든 변경사항 롤백
                // (실제로는 데이터베이스 트랜잭션으로 처리)
                println("Order processing failed: ${e.message}")
                throw e
            }
        }
    }
}

fun main() {
    val orderService = Problem.OrderService()

    try {
        orderService.processOrder(
            orderId = "ORDER-001",
            customerId = "CUST-001",
            productId = "PROD-001",
            amount = 100.0
        )
    } catch (e: Exception) {
        println("Failed to process order: ${e.message}")
    }

    // 문제점:
    // 1. 분산 환경에서 단일 트랜잭션 처리 불가능
    // 2. 서비스 간 결합도가 높음
    // 3. 부분 실패 시 복구가 어려움
    // 4. 확장성이 제한됨
}