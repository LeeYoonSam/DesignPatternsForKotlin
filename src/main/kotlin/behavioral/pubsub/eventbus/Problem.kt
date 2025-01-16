package behavioral.pubsub.eventbus

/**
 * 문제점
 * - 컴포넌트 간 직접적인 결합도가 높음
 * - 동적인 이벤트 구독/해지 처리의 어려움
 * - 이벤트 전파 과정의 복잡성
 * - 비동기 이벤트 처리의 어려움
 * - 이벤트 유실 가능성
 */
class Problem {
    // 주문 처리 컴포넌트
    class OrderProcessor {
        private val emailService = EmailService()
        private val inventoryService = InventoryService()
        private val shippingService = ShippingService()

        fun processOrder(order: Order) {
            // 주문 처리
            println("Processing order: ${order.id}")

            // 직접적인 서비스 호출로 인한 강한 결합
            emailService.sendOrderConfirmation(order)
            inventoryService.updateInventory(order)
            shippingService.scheduleDelivery(order)
        }
    }

    class EmailService {
        fun sendOrderConfirmation(order: Order) {
            println("Sending order confirmation email for order: ${order.id}")
        }
    }

    class InventoryService {
        fun updateInventory(order: Order) {
            println("Updating inventory for order: ${order.id}")
        }
    }

    class ShippingService {
        fun scheduleDelivery(order: Order) {
            println("Scheduling delivery for order: ${order.id}")
        }
    }

    data class Order(val id: String, val items: List<String>)
}


fun main() {
    // 문제가 있는 구현
    println("Problem Implementation:")
    val problemOrder = Problem.Order("123", listOf("item1", "item2"))
    val orderProcessor = Problem.OrderProcessor()
    orderProcessor.processOrder(problemOrder)
}