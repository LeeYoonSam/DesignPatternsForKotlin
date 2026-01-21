package architecture.ddd.domainevent

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 전자상거래 주문 시스템 - Domain Event 패턴 적용
 *
 * Domain Event 패턴의 장점:
 * - 도메인 객체 간 느슨한 결합
 * - 부수 효과와 핵심 로직 분리
 * - 새 기능 추가 시 기존 코드 수정 불필요 (OCP)
 * - 비동기 처리 가능
 * - 이벤트 히스토리 추적 가능
 * - 테스트 용이
 */
class Solution {

    // ===== Value Objects =====

    @JvmInline
    value class OrderId(val value: String) {
        companion object {
            fun generate() = OrderId(UUID.randomUUID().toString())
        }
    }

    @JvmInline
    value class CustomerId(val value: String)

    @JvmInline
    value class ProductId(val value: String)

    data class Money(val amount: BigDecimal) {
        operator fun plus(other: Money) = Money(amount + other.amount)
        operator fun times(multiplier: Int) = Money(amount * BigDecimal(multiplier))
        fun toInt() = amount.toInt()
        override fun toString() = "₩$amount"

        companion object {
            val ZERO = Money(BigDecimal.ZERO)
            fun of(value: Long) = Money(BigDecimal.valueOf(value))
        }
    }

    // ===== Domain Events =====

    /**
     * 도메인 이벤트 기본 인터페이스
     */
    interface DomainEvent {
        val eventId: String
        val occurredAt: LocalDateTime
        val aggregateId: String
        val aggregateType: String
    }

    /**
     * 기본 도메인 이벤트 구현
     */
    abstract class BaseDomainEvent(
        override val aggregateId: String,
        override val aggregateType: String
    ) : DomainEvent {
        override val eventId: String = UUID.randomUUID().toString()
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    }

    // 주문 관련 이벤트
    data class OrderCreated(
        val orderId: OrderId,
        val customerId: CustomerId
    ) : BaseDomainEvent(orderId.value, "Order")

    data class OrderItemAdded(
        val orderId: OrderId,
        val productId: ProductId,
        val productName: String,
        val quantity: Int,
        val unitPrice: Money
    ) : BaseDomainEvent(orderId.value, "Order")

    data class OrderConfirmed(
        val orderId: OrderId,
        val customerId: CustomerId,
        val totalAmount: Money,
        val items: List<OrderItemSnapshot>
    ) : BaseDomainEvent(orderId.value, "Order")

    data class OrderCancelled(
        val orderId: OrderId,
        val customerId: CustomerId,
        val reason: String,
        val wasConfirmed: Boolean,
        val totalAmount: Money,
        val items: List<OrderItemSnapshot>
    ) : BaseDomainEvent(orderId.value, "Order")

    data class OrderShipped(
        val orderId: OrderId,
        val customerId: CustomerId,
        val shippingAddress: String
    ) : BaseDomainEvent(orderId.value, "Order")

    data class OrderDelivered(
        val orderId: OrderId,
        val customerId: CustomerId
    ) : BaseDomainEvent(orderId.value, "Order")

    // 이벤트에 포함될 주문 항목 스냅샷
    data class OrderItemSnapshot(
        val productId: ProductId,
        val productName: String,
        val quantity: Int,
        val unitPrice: Money
    )

    // ===== Event Infrastructure =====

    /**
     * 이벤트 핸들러 인터페이스
     */
    fun interface EventHandler<T : DomainEvent> {
        fun handle(event: T)
    }

    /**
     * 이벤트 디스패처 (이벤트 버스)
     */
    class EventDispatcher {
        private val handlers = ConcurrentHashMap<KClass<*>, MutableList<EventHandler<*>>>()
        private val eventStore = mutableListOf<DomainEvent>()

        @Suppress("UNCHECKED_CAST")
        fun <T : DomainEvent> register(eventType: KClass<T>, handler: EventHandler<T>) {
            handlers.getOrPut(eventType) { mutableListOf() }
                .add(handler as EventHandler<*>)
        }

        inline fun <reified T : DomainEvent> register(handler: EventHandler<T>) {
            register(T::class, handler)
        }

        @Suppress("UNCHECKED_CAST")
        fun dispatch(event: DomainEvent) {
            // 이벤트 저장 (감사 로그 / Event Sourcing 용)
            eventStore.add(event)

            // 핸들러 실행
            val eventHandlers = handlers[event::class] ?: return
            eventHandlers.forEach { handler ->
                try {
                    (handler as EventHandler<DomainEvent>).handle(event)
                } catch (e: Exception) {
                    println("  [Error] 이벤트 처리 실패: ${e.message}")
                }
            }
        }

        fun dispatchAll(events: List<DomainEvent>) {
            events.forEach { dispatch(it) }
        }

        fun getEventHistory(): List<DomainEvent> = eventStore.toList()

        fun getEventHistory(aggregateId: String): List<DomainEvent> =
            eventStore.filter { it.aggregateId == aggregateId }
    }

    // ===== Domain Model =====

    enum class OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }

    data class OrderItem(
        val productId: ProductId,
        val productName: String,
        val unitPrice: Money,
        val quantity: Int
    ) {
        val subtotal: Money get() = unitPrice * quantity

        fun toSnapshot() = OrderItemSnapshot(productId, productName, quantity, unitPrice)
    }

    /**
     * Order Aggregate - 도메인 이벤트 발행
     */
    class Order private constructor(
        val id: OrderId,
        val customerId: CustomerId,
        private var _status: OrderStatus,
        private val _items: MutableList<OrderItem>,
        private val _domainEvents: MutableList<DomainEvent>
    ) {
        val status: OrderStatus get() = _status
        val items: List<OrderItem> get() = _items.toList()
        val domainEvents: List<DomainEvent> get() = _domainEvents.toList()

        val totalAmount: Money
            get() = _items.fold(Money.ZERO) { acc, item -> acc + item.subtotal }

        fun addItem(productId: ProductId, productName: String, unitPrice: Money, quantity: Int) {
            require(_status == OrderStatus.PENDING) { "대기 중인 주문에만 상품 추가 가능" }

            _items.add(OrderItem(productId, productName, unitPrice, quantity))

            // 도메인 이벤트 발행
            _domainEvents.add(
                OrderItemAdded(id, productId, productName, quantity, unitPrice)
            )
        }

        fun confirm() {
            require(_status == OrderStatus.PENDING) { "대기 중인 주문만 확정 가능" }
            require(_items.isNotEmpty()) { "상품이 없는 주문은 확정 불가" }

            _status = OrderStatus.CONFIRMED

            // 도메인 이벤트 발행
            _domainEvents.add(
                OrderConfirmed(
                    orderId = id,
                    customerId = customerId,
                    totalAmount = totalAmount,
                    items = _items.map { it.toSnapshot() }
                )
            )
        }

        fun cancel(reason: String) {
            require(_status in listOf(OrderStatus.PENDING, OrderStatus.CONFIRMED)) {
                "대기 중이거나 확정된 주문만 취소 가능"
            }

            val wasConfirmed = _status == OrderStatus.CONFIRMED
            _status = OrderStatus.CANCELLED

            // 도메인 이벤트 발행
            _domainEvents.add(
                OrderCancelled(
                    orderId = id,
                    customerId = customerId,
                    reason = reason,
                    wasConfirmed = wasConfirmed,
                    totalAmount = totalAmount,
                    items = _items.map { it.toSnapshot() }
                )
            )
        }

        fun ship(shippingAddress: String) {
            require(_status == OrderStatus.CONFIRMED) { "확정된 주문만 배송 가능" }

            _status = OrderStatus.SHIPPED

            _domainEvents.add(
                OrderShipped(id, customerId, shippingAddress)
            )
        }

        fun deliver() {
            require(_status == OrderStatus.SHIPPED) { "배송 중인 주문만 완료 처리 가능" }

            _status = OrderStatus.DELIVERED

            _domainEvents.add(
                OrderDelivered(id, customerId)
            )
        }

        fun clearDomainEvents() {
            _domainEvents.clear()
        }

        companion object {
            fun create(customerId: CustomerId): Order {
                val orderId = OrderId.generate()
                val order = Order(
                    id = orderId,
                    customerId = customerId,
                    _status = OrderStatus.PENDING,
                    _items = mutableListOf(),
                    _domainEvents = mutableListOf()
                )

                order._domainEvents.add(OrderCreated(orderId, customerId))
                return order
            }
        }
    }

    // ===== Supporting Domain Objects =====

    class Customer(
        val id: CustomerId,
        var name: String,
        var email: String,
        var points: Int = 0
    )

    class Product(
        val id: ProductId,
        var name: String,
        var price: Money,
        var stock: Int
    )

    // ===== Event Handlers =====

    /**
     * 재고 관리 이벤트 핸들러
     */
    class InventoryEventHandler(
        private val products: MutableMap<ProductId, Product>
    ) {
        fun onOrderConfirmed(event: OrderConfirmed) {
            println("  [InventoryHandler] 주문 확정 - 재고 차감")
            event.items.forEach { item ->
                val product = products[item.productId]
                if (product != null) {
                    product.stock -= item.quantity
                    println("    - ${product.name}: -${item.quantity} (남은 재고: ${product.stock})")
                }
            }
        }

        fun onOrderCancelled(event: OrderCancelled) {
            if (event.wasConfirmed) {
                println("  [InventoryHandler] 주문 취소 - 재고 복구")
                event.items.forEach { item ->
                    val product = products[item.productId]
                    if (product != null) {
                        product.stock += item.quantity
                        println("    - ${product.name}: +${item.quantity} (남은 재고: ${product.stock})")
                    }
                }
            }
        }
    }

    /**
     * 포인트 적립 이벤트 핸들러
     */
    class PointEventHandler(
        private val customers: MutableMap<CustomerId, Customer>
    ) {
        fun onOrderConfirmed(event: OrderConfirmed) {
            val customer = customers[event.customerId] ?: return
            val pointsToAdd = (event.totalAmount.toInt() * 0.01).toInt()
            customer.points += pointsToAdd
            println("  [PointHandler] 포인트 적립: +$pointsToAdd (총: ${customer.points})")
        }

        fun onOrderCancelled(event: OrderCancelled) {
            if (event.wasConfirmed) {
                val customer = customers[event.customerId] ?: return
                val pointsToDeduct = (event.totalAmount.toInt() * 0.01).toInt()
                customer.points = maxOf(0, customer.points - pointsToDeduct)
                println("  [PointHandler] 포인트 차감: -$pointsToDeduct (총: ${customer.points})")
            }
        }

        fun onOrderDelivered(event: OrderDelivered) {
            val customer = customers[event.customerId] ?: return
            val bonusPoints = 100
            customer.points += bonusPoints
            println("  [PointHandler] 배송 완료 보너스: +$bonusPoints (총: ${customer.points})")
        }
    }

    /**
     * 알림 이벤트 핸들러
     */
    class NotificationEventHandler(
        private val customers: MutableMap<CustomerId, Customer>
    ) {
        fun onOrderConfirmed(event: OrderConfirmed) {
            val customer = customers[event.customerId] ?: return
            println("  [NotificationHandler] 이메일 발송: ${customer.email}")
            println("    - 제목: 주문이 확정되었습니다")
            println("    - 내용: 주문번호 ${event.orderId.value}, 총액 ${event.totalAmount}")
        }

        fun onOrderCancelled(event: OrderCancelled) {
            val customer = customers[event.customerId] ?: return
            println("  [NotificationHandler] 이메일 발송: ${customer.email}")
            println("    - 제목: 주문이 취소되었습니다")
            println("    - 내용: 사유 - ${event.reason}")
        }

        fun onOrderShipped(event: OrderShipped) {
            val customer = customers[event.customerId] ?: return
            println("  [NotificationHandler] 푸시 알림: ${customer.id.value}")
            println("    - 내용: 주문이 발송되었습니다! 배송지: ${event.shippingAddress}")
        }

        fun onOrderDelivered(event: OrderDelivered) {
            val customer = customers[event.customerId] ?: return
            println("  [NotificationHandler] 푸시 알림: ${customer.id.value}")
            println("    - 내용: 배송이 완료되었습니다! 리뷰를 남겨주세요.")
        }
    }

    /**
     * 통계 이벤트 핸들러
     */
    class StatisticsEventHandler {
        private var totalOrders = 0
        private var totalRevenue = Money.ZERO
        private var cancelledOrders = 0

        fun onOrderConfirmed(event: OrderConfirmed) {
            totalOrders++
            totalRevenue = totalRevenue + event.totalAmount
            println("  [StatisticsHandler] 통계 업데이트 - 총 주문: $totalOrders, 총 매출: $totalRevenue")
        }

        fun onOrderCancelled(event: OrderCancelled) {
            if (event.wasConfirmed) {
                totalOrders--
                totalRevenue = Money(totalRevenue.amount - event.totalAmount.amount)
                cancelledOrders++
            }
            println("  [StatisticsHandler] 취소 처리 - 취소 건수: $cancelledOrders")
        }

        fun getStatistics() = mapOf(
            "totalOrders" to totalOrders,
            "totalRevenue" to totalRevenue,
            "cancelledOrders" to cancelledOrders
        )
    }

    /**
     * 감사 로그 이벤트 핸들러
     */
    class AuditLogEventHandler {
        private val logs = mutableListOf<String>()

        fun onAnyEvent(event: DomainEvent) {
            val log = "[${event.occurredAt}] ${event::class.simpleName} - " +
                    "${event.aggregateType}(${event.aggregateId})"
            logs.add(log)
            println("  [AuditHandler] $log")
        }

        fun getLogs(): List<String> = logs.toList()
    }

    // ===== Application Service =====

    class OrderApplicationService(
        private val orders: MutableMap<OrderId, Order> = mutableMapOf(),
        private val products: MutableMap<ProductId, Product>,
        private val eventDispatcher: EventDispatcher
    ) {
        fun createOrder(customerId: CustomerId): Order {
            val order = Order.create(customerId)
            orders[order.id] = order

            // 이벤트 발행
            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()

            return order
        }

        fun addItem(orderId: OrderId, productId: ProductId, quantity: Int) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val product = products[productId] ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")

            order.addItem(productId, product.name, product.price, quantity)

            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()
        }

        fun confirmOrder(orderId: OrderId) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            println("주문 확정 처리: ${orderId.value}")
            order.confirm()

            // 이벤트 발행 → 핸들러들이 자동으로 처리
            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()
            println("주문 확정 완료")
        }

        fun cancelOrder(orderId: OrderId, reason: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            println("주문 취소 처리: ${orderId.value}")
            order.cancel(reason)

            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()
            println("주문 취소 완료")
        }

        fun shipOrder(orderId: OrderId, address: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            println("주문 배송 처리: ${orderId.value}")
            order.ship(address)

            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()
            println("배송 시작")
        }

        fun deliverOrder(orderId: OrderId) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            println("배송 완료 처리: ${orderId.value}")
            order.deliver()

            eventDispatcher.dispatchAll(order.domainEvents)
            order.clearDomainEvents()
            println("배송 완료")
        }

        fun getOrder(orderId: OrderId): Order? = orders[orderId]
    }
}

fun main() {
    println("=== Domain Event 패턴 적용 데모 ===")
    println()

    // 초기 데이터 설정
    val customers = mutableMapOf<Solution.CustomerId, Solution.Customer>()
    val products = mutableMapOf<Solution.ProductId, Solution.Product>()

    val customerId = Solution.CustomerId("CUST001")
    customers[customerId] = Solution.Customer(customerId, "홍길동", "hong@example.com", 1000)

    val productId1 = Solution.ProductId("PROD001")
    products[productId1] = Solution.Product(productId1, "노트북", Solution.Money.of(1500000), 10)

    val productId2 = Solution.ProductId("PROD002")
    products[productId2] = Solution.Product(productId2, "마우스", Solution.Money.of(50000), 100)

    // 이벤트 디스패처 및 핸들러 설정
    val eventDispatcher = Solution.EventDispatcher()

    val inventoryHandler = Solution.InventoryEventHandler(products)
    val pointHandler = Solution.PointEventHandler(customers)
    val notificationHandler = Solution.NotificationEventHandler(customers)
    val statisticsHandler = Solution.StatisticsEventHandler()
    val auditHandler = Solution.AuditLogEventHandler()

    // 이벤트 핸들러 등록 (느슨한 결합!)
    eventDispatcher.register<Solution.OrderConfirmed> { inventoryHandler.onOrderConfirmed(it) }
    eventDispatcher.register<Solution.OrderConfirmed> { pointHandler.onOrderConfirmed(it) }
    eventDispatcher.register<Solution.OrderConfirmed> { notificationHandler.onOrderConfirmed(it) }
    eventDispatcher.register<Solution.OrderConfirmed> { statisticsHandler.onOrderConfirmed(it) }
    eventDispatcher.register<Solution.OrderConfirmed> { auditHandler.onAnyEvent(it) }

    eventDispatcher.register<Solution.OrderCancelled> { inventoryHandler.onOrderCancelled(it) }
    eventDispatcher.register<Solution.OrderCancelled> { pointHandler.onOrderCancelled(it) }
    eventDispatcher.register<Solution.OrderCancelled> { notificationHandler.onOrderCancelled(it) }
    eventDispatcher.register<Solution.OrderCancelled> { statisticsHandler.onOrderCancelled(it) }
    eventDispatcher.register<Solution.OrderCancelled> { auditHandler.onAnyEvent(it) }

    eventDispatcher.register<Solution.OrderShipped> { notificationHandler.onOrderShipped(it) }
    eventDispatcher.register<Solution.OrderShipped> { auditHandler.onAnyEvent(it) }

    eventDispatcher.register<Solution.OrderDelivered> { pointHandler.onOrderDelivered(it) }
    eventDispatcher.register<Solution.OrderDelivered> { notificationHandler.onOrderDelivered(it) }
    eventDispatcher.register<Solution.OrderDelivered> { auditHandler.onAnyEvent(it) }

    // 애플리케이션 서비스 생성
    val orderService = Solution.OrderApplicationService(
        products = products,
        eventDispatcher = eventDispatcher
    )

    // 시나리오 1: 주문 생성 및 확정
    println("=== 시나리오 1: 주문 확정 ===")
    val order1 = orderService.createOrder(customerId)
    orderService.addItem(order1.id, productId1, 1)
    orderService.addItem(order1.id, productId2, 2)
    println("주문 총액: ${order1.totalAmount}")
    println()
    orderService.confirmOrder(order1.id)
    println()

    // 시나리오 2: 배송
    println("=== 시나리오 2: 배송 ===")
    orderService.shipOrder(order1.id, "서울시 강남구 테헤란로 123")
    println()
    orderService.deliverOrder(order1.id)
    println()

    // 시나리오 3: 새 주문 생성 후 취소
    println("=== 시나리오 3: 주문 취소 ===")
    val order2 = orderService.createOrder(customerId)
    orderService.addItem(order2.id, productId1, 2)
    orderService.confirmOrder(order2.id)
    println()
    orderService.cancelOrder(order2.id, "고객 변심")
    println()

    // 이벤트 히스토리 조회
    println("=== 이벤트 히스토리 ===")
    eventDispatcher.getEventHistory().forEach { event ->
        println("- ${event::class.simpleName} at ${event.occurredAt}")
    }
    println()

    // 통계 조회
    println("=== 최종 통계 ===")
    println(statisticsHandler.getStatistics())
    println()

    // 현재 상태
    println("=== 현재 상태 ===")
    println("고객 포인트: ${customers[customerId]?.points}")
    println("노트북 재고: ${products[productId1]?.stock}")
    println("마우스 재고: ${products[productId2]?.stock}")
    println()

    println("=== Domain Event 패턴 장점 ===")
    println("1. 핵심 로직과 부수 효과 분리")
    println("2. 새 핸들러 추가 시 기존 코드 수정 불필요 (OCP)")
    println("3. 핸들러별 독립적 실패 처리 가능")
    println("4. 이벤트 히스토리로 감사 로그 자동화")
    println("5. 비동기 처리로 확장 가능")
    println("6. 테스트 용이 (이벤트 발행만 검증)")
}
