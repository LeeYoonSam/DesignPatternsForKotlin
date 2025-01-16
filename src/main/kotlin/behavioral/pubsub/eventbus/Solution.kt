package behavioral.pubsub.eventbus

/**
 * 해결책: 발행-구독 패턴을 사용한 이벤트 기반 주문 처리 시스템
 */
class Solution {
    // 이벤트 정의
    sealed class Event {
        data class OrderCreated(val order: Order) : Event()
        data class InventoryUpdate(val orderId: String) : Event()
        data class OrderShipped(val orderId: String) : Event()
    }

    // 이벤트 브로커
    class EventBroker {
        private val subscribers = mutableMapOf<Class<out Event>, MutableSet<(Event) -> Unit>>()

        fun publish(event: Event) {
            subscribers[event::class.java]?.forEach { subscriber ->
                subscriber(event)
            }
        }

        fun subscribe(eventType: Class<out Event>, subscriber: (Event) -> Unit) {
            subscribers.getOrPut(eventType) { mutableSetOf() }.add(subscriber)
        }

        fun unsubscribe(eventType: Class<out Event>, subscriber: (Event) -> Unit) {
            subscribers[eventType]?.remove(subscriber)
        }
    }

    // 주문 처리 컴포넌트
    class OrderProcessor(private val eventBroker: EventBroker) {
        fun processOrder(order: Order) {
            println("Processing order: ${order.id}")
            eventBroker.publish(Event.OrderCreated(order))
        }
    }

    // 이메일 서비스
    class EmailService(private val eventBroker: EventBroker) {
        init {
            eventBroker.subscribe(Event.OrderCreated::class.java) { event ->
                when (event) {
                    is Event.OrderCreated -> sendOrderConfirmation(event.order)
                    else -> {}
                }
            }
        }

        private fun sendOrderConfirmation(order: Order) {
            println("Sending order confirmation email for order: ${order.id}")
        }
    }

    // 재고 서비스
    class InventoryService(private val eventBroker: EventBroker) {
        init {
            eventBroker.subscribe(Event.OrderCreated::class.java) { event ->
                when (event) {
                    is Event.OrderCreated -> updateInventory(event.order)
                    else -> {}
                }
            }
        }

        private fun updateInventory(order: Order) {
            println("Updating inventory for order: ${order.id}")
            eventBroker.publish(Event.InventoryUpdate(order.id))
        }
    }

    // 배송 서비스
    class ShippingService(private val eventBroker: EventBroker) {
        init {
            eventBroker.subscribe(Event.InventoryUpdate::class.java) { event ->
                when (event) {
                    is Event.InventoryUpdate -> scheduleDelivery(event.orderId)
                    else -> {}
                }
            }
        }

        private fun scheduleDelivery(orderId: String) {
            println("Scheduling delivery for order: $orderId")
            eventBroker.publish(Event.OrderShipped(orderId))
        }
    }

    data class Order(val id: String, val items: List<String>)
}

fun main() {
    println("\nSolution Implementation:")
    // 개선된 구현
    val eventBroker = Solution.EventBroker()
    val emailService = Solution.EmailService(eventBroker)
    val inventoryService = Solution.InventoryService(eventBroker)
    val shippingService = Solution.ShippingService(eventBroker)
    val improvedOrderProcessor = Solution.OrderProcessor(eventBroker)

    val order = Solution.Order("456", listOf("item1", "item2"))
    improvedOrderProcessor.processOrder(order)
}