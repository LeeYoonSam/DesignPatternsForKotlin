package structural.unitofwork.order

import structural.unitofwork.order.Solution.*

/**
 * 문제점
 * - 트랜잭션 관리의 복잡성
 * - 데이터 일관성 유지의 어려움
 * - 불필요한 데이터베이스 연산
 * - 변경사항 추적의 어려움
 */
class Problem {
    // 트랜잭션 관리가 없는 주문 처리 시스템
    class OrderProcessor {
        private val orders = mutableMapOf(
            "ORD-001" to Order("ORD-001", "PREPARE", mutableListOf(OrderItem("PROD-001", 1))),
            "ORD-002" to Order("ORD-002", "PREPARE", mutableListOf(OrderItem("PROD-001", 4), OrderItem("2", 3))),
            "ORD-003" to Order("ORD-003", "PAYMENT", mutableListOf(OrderItem("PROD-002", 3), OrderItem("6", 2))),
        )

        private val products = mutableMapOf(
            "PROD-001" to Product(id = "PROD-001", name = "Product1", stockQuantity = 10),
            "PROD-002" to Product(id = "PROD-002", name = "Product2", stockQuantity = 20),
            "PROD-003" to Product(id = "PROD-003", name = "Product3", stockQuantity = 30),
            "PROD-004" to Product(id = "PROD-004", name = "Product4", stockQuantity = 40),
            "PROD-005" to Product(id = "PROD-005", name = "Product5", stockQuantity = 4),
            "PROD-006" to Product(id = "PROD-006", name = "Product6", stockQuantity = 3),
            "PROD-007" to Product(id = "PROD-007", name = "Product7", stockQuantity = 1),
        )

        fun processOrder(orderId: String, productId: String, quantity: Int) {
            // 개별적인 데이터베이스 연산들
            val order = orders[orderId] ?: throw IllegalArgumentException("Order not found")
            val product = products[productId] ?: throw IllegalArgumentException("Product not found")

            // 재고 확인
            if (product.stockQuantity < quantity) {
                throw IllegalStateException("Insufficient stock")
            }

            // 재고 감소
            product.stockQuantity -= quantity
            products[productId] = product

            // 주문 상태 업데이트
            order.status = "PROCESSING"
            orders[orderId] = order

            // 주문 항목 추가
            order.items.add(OrderItem(productId, quantity))
            orders[orderId] = order
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
    println("Problem Implementation Demo:")
    val processor = Problem.OrderProcessor()

    try {
        // 주문 처리 시도
        // 만약 중간에 실패하면 이전 변경사항들이 롤백되지 않음
        processor.processOrder("ORD-001", "PROD-001", 5)
        println("Order processed successfully")
    } catch (e: Exception) {
        println("Error processing order: ${e.message}")
        // 실패 시 수동으로 모든 변경사항을 되돌려야 함
    }
}