package architecture.saga.order

import java.util.concurrent.CompletableFuture

class Solution {
    // 이벤트 정의
    sealed class OrderEvent {
        abstract val orderId: String
    }

    data class OrderCreatedEvent(
        override val orderId: String,
        val customerId: String,
        val productId: String,
        val amount: Double
    ) : OrderEvent()

    data class InventoryCheckedEvent(override val orderId: String) : OrderEvent()
    data class InventoryReservedEvent(override val orderId: String) : OrderEvent()
    data class PaymentCompletedEvent(override val orderId: String) : OrderEvent()
    data class OrderCompletedEvent(override val orderId: String) : OrderEvent()

    // 보상 이벤트 정의
    data class OrderCancelledEvent(override val orderId: String) : OrderEvent()
    data class InventoryReleasedEvent(override val orderId: String) : OrderEvent()
    data class PaymentRefundedEvent(override val orderId: String) : OrderEvent()

    // 서비스 정의
    interface OrderSagaService {
        fun createOrder(event: OrderCreatedEvent): CompletableFuture<Boolean>
        fun cancelOrder(event: OrderCancelledEvent): CompletableFuture<Boolean>
    }

    interface InventoryService {
        fun checkAndReserveInventory(event: OrderCreatedEvent): CompletableFuture<Boolean>
        fun releaseInventory(event: InventoryReleasedEvent): CompletableFuture<Boolean>
    }

    interface PaymentService {
        fun processPayment(event: OrderCreatedEvent): CompletableFuture<Boolean>
        fun refundPayment(event: PaymentRefundedEvent): CompletableFuture<Boolean>
    }

    // Saga 오케스트레이터
    class OrderSagaOrchestrator(
        private val orderService: OrderSagaService,
        private val inventoryService: InventoryService,
        private val paymentService: PaymentService
    ) {
        private val sagaLog = mutableListOf<OrderEvent>()

        // process 중간에 예외를 throw 하면 보상 트랜잭션을 통한 롤백 테스트 가능
        fun processSaga(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            return createOrder(event)
                .thenCompose { success ->
                    if (success) checkAndReserveInventory(event)
                    else compensateOrder(event)
                }
                .thenCompose { success ->
                    if (success) processPayment(event)
                    else compensateInventory(event)
                }
                .thenCompose { success ->
                    if (success) completeOrder(event)
                    else compensatePayment(event)
                }
                .exceptionally { throwable ->
                    println("Saga failed: ${throwable.message}")
                    compensateAll(event)
                    false
                }
        }

        private fun createOrder(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            return orderService.createOrder(event)
                .thenApply { success ->
                    if (success) sagaLog.add(event)
                    success
                }
        }

        private fun checkAndReserveInventory(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            return inventoryService.checkAndReserveInventory(event)
                .thenApply { success ->
                    if (success) sagaLog.add(InventoryReservedEvent(event.orderId))
                    success
                }
        }

        private fun processPayment(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            return paymentService.processPayment(event)
                .thenApply { success ->
                    if (success) sagaLog.add(PaymentCompletedEvent(event.orderId))
                    success
                }
        }

        private fun completeOrder(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            sagaLog.add(OrderCompletedEvent(event.orderId))
            return CompletableFuture.completedFuture(true)
        }

        // 보상 트랜잭션들
        private fun compensateOrder(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            val compensationEvent  = OrderCancelledEvent(event.orderId)
            sagaLog.add(compensationEvent)
            return orderService.cancelOrder(compensationEvent)
        }

        private fun compensateInventory(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            val compensationEvent = InventoryReleasedEvent(event.orderId)
            sagaLog.add(compensationEvent)
            return inventoryService.releaseInventory(compensationEvent)
        }

        private fun compensatePayment(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            val compensationEvent = PaymentRefundedEvent(event.orderId)
            sagaLog.add(compensationEvent)
            return paymentService.refundPayment(compensationEvent)
        }

        private fun compensateAll(event: OrderCreatedEvent): Boolean {
            compensatePayment(event)
            compensateInventory(event)
            compensateOrder(event)
            return false
        }

        fun getSagaLog(): List<OrderEvent> = sagaLog.toList()
    }

    // 서비스 구현
    class OrderSagaServiceImpl : OrderSagaService {
        override fun createOrder(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            println("Creating order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }

        override fun cancelOrder(event: OrderCancelledEvent): CompletableFuture<Boolean> {
            println("Cancelling order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }
    }

    class InventoryServiceImpl : InventoryService {
        override fun checkAndReserveInventory(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            println("Reserving inventory for order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }

        override fun releaseInventory(event: InventoryReleasedEvent): CompletableFuture<Boolean> {
            println("Releasing inventory for order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }
    }

    class PaymentServiceImpl : PaymentService {
        override fun processPayment(event: OrderCreatedEvent): CompletableFuture<Boolean> {
            println("Processing payment for order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }

        override fun refundPayment(event: PaymentRefundedEvent): CompletableFuture<Boolean> {
            println("Refunding payment for order: ${event.orderId}")
            return CompletableFuture.completedFuture(true)
        }
    }
}

fun main() {
    val orchestrator = Solution.OrderSagaOrchestrator(
        Solution.OrderSagaServiceImpl(),
        Solution.InventoryServiceImpl(),
        Solution.PaymentServiceImpl()
    )

    val orderEvent = Solution.OrderCreatedEvent(
        orderId = "ORDER-001",
        customerId = "CUST-001",
        productId = "PROD-001",
        amount = 100.0
    )

    // Saga 실행
    orchestrator.processSaga(orderEvent).thenAccept { success ->
        println("\nSaga completed with status: $success")
        println("\nSaga Log:")
        orchestrator.getSagaLog().forEach { event ->
            println("- $event")
        }
    }.join()
}