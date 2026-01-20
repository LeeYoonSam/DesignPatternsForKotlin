package architecture.ddd.aggregate

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 시스템 - Aggregate 패턴 적용
 *
 * Aggregate 패턴 특징:
 * - Aggregate Root를 통해서만 내부 객체에 접근
 * - 일관성 경계(Consistency Boundary) 정의
 * - 불변식(Invariant) 보장
 * - 트랜잭션 단위로 동작
 */
class Solution {

    // ===== Value Objects =====

    @JvmInline
    value class OrderId(val value: String) {
        companion object {
            fun generate(): OrderId = OrderId(UUID.randomUUID().toString())
        }
    }

    @JvmInline
    value class ProductId(val value: String) {
        companion object {
            fun generate(): ProductId = ProductId(UUID.randomUUID().toString())
        }
    }

    @JvmInline
    value class CustomerId(val value: String)

    @JvmInline
    value class OrderItemId(val value: String) {
        companion object {
            fun generate(): OrderItemId = OrderItemId(UUID.randomUUID().toString())
        }
    }

    data class Money(val amount: BigDecimal) : Comparable<Money> {
        init {
            require(amount >= BigDecimal.ZERO) { "금액은 음수일 수 없습니다" }
        }

        operator fun plus(other: Money) = Money(amount + other.amount)
        operator fun minus(other: Money) = Money(amount - other.amount)
        operator fun times(multiplier: Int) = Money(amount * BigDecimal(multiplier))

        override fun compareTo(other: Money) = amount.compareTo(other.amount)
        override fun toString() = "₩${amount.setScale(0, RoundingMode.HALF_UP)}"

        companion object {
            val ZERO = Money(BigDecimal.ZERO)
            fun of(value: Long) = Money(BigDecimal.valueOf(value))
            fun of(value: Double) = Money(BigDecimal.valueOf(value))
        }
    }

    @JvmInline
    value class Quantity(val value: Int) {
        init {
            require(value >= 0) { "수량은 음수일 수 없습니다" }
        }

        operator fun plus(other: Quantity) = Quantity(value + other.value)
        operator fun minus(other: Quantity) = Quantity(value - other.value)
        operator fun compareTo(other: Quantity) = value.compareTo(other.value)

        companion object {
            val ZERO = Quantity(0)
            fun of(value: Int) = Quantity(value)
        }
    }

    data class Address(
        val street: String,
        val city: String,
        val zipCode: String,
        val country: String
    ) {
        init {
            require(street.isNotBlank()) { "도로명은 필수입니다" }
            require(city.isNotBlank()) { "도시는 필수입니다" }
            require(zipCode.isNotBlank()) { "우편번호는 필수입니다" }
        }
    }

    // ===== Domain Events =====

    sealed class OrderEvent {
        abstract val orderId: OrderId
        abstract val occurredAt: LocalDateTime
    }

    data class OrderCreated(
        override val orderId: OrderId,
        val customerId: CustomerId,
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderItemAdded(
        override val orderId: OrderId,
        val productId: ProductId,
        val quantity: Quantity,
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderItemRemoved(
        override val orderId: OrderId,
        val itemId: OrderItemId,
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderConfirmed(
        override val orderId: OrderId,
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderCancelled(
        override val orderId: OrderId,
        val reason: String,
        override val occurredAt: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    // ===== Order Status =====

    enum class OrderStatus {
        PENDING {
            override fun canTransitionTo(next: OrderStatus) = next in listOf(CONFIRMED, CANCELLED)
        },
        CONFIRMED {
            override fun canTransitionTo(next: OrderStatus) = next in listOf(SHIPPED, CANCELLED)
        },
        SHIPPED {
            override fun canTransitionTo(next: OrderStatus) = next == DELIVERED
        },
        DELIVERED {
            override fun canTransitionTo(next: OrderStatus) = false
        },
        CANCELLED {
            override fun canTransitionTo(next: OrderStatus) = false
        };

        abstract fun canTransitionTo(next: OrderStatus): Boolean
    }

    // ===== Order Item (Entity within Aggregate) =====

    class OrderItem private constructor(
        val id: OrderItemId,
        val productId: ProductId,
        val productName: String,
        val unitPrice: Money,
        private var _quantity: Quantity
    ) {
        val quantity: Quantity get() = _quantity
        val subtotal: Money get() = unitPrice * quantity.value

        // 내부에서만 수량 변경 가능 (패키지 프라이빗 시뮬레이션)
        internal fun increaseQuantity(amount: Quantity) {
            _quantity = _quantity + amount
        }

        internal fun decreaseQuantity(amount: Quantity) {
            require(_quantity >= amount) { "감소할 수량이 현재 수량보다 큽니다" }
            _quantity = _quantity - amount
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OrderItem) return false
            return id == other.id
        }

        override fun hashCode() = id.hashCode()

        override fun toString() = "OrderItem(id=$id, product=$productName, qty=$quantity, subtotal=$subtotal)"

        companion object {
            internal fun create(
                productId: ProductId,
                productName: String,
                unitPrice: Money,
                quantity: Quantity
            ): OrderItem {
                require(quantity.value > 0) { "수량은 1 이상이어야 합니다" }
                return OrderItem(
                    id = OrderItemId.generate(),
                    productId = productId,
                    productName = productName,
                    unitPrice = unitPrice,
                    _quantity = quantity
                )
            }
        }
    }

    // ===== Order Aggregate Root =====

    class Order private constructor(
        val id: OrderId,
        val customerId: CustomerId,
        private var _status: OrderStatus,
        private val _items: MutableList<OrderItem>,
        private var _shippingAddress: Address?,
        val createdAt: LocalDateTime,
        private val _domainEvents: MutableList<OrderEvent>
    ) {
        // 읽기 전용 프로퍼티
        val status: OrderStatus get() = _status
        val items: List<OrderItem> get() = _items.toList()  // 방어적 복사
        val shippingAddress: Address? get() = _shippingAddress
        val domainEvents: List<OrderEvent> get() = _domainEvents.toList()

        // 계산 프로퍼티
        val totalAmount: Money
            get() = _items.fold(Money.ZERO) { acc, item -> acc + item.subtotal }

        val itemCount: Int
            get() = _items.sumOf { it.quantity.value }

        val isEmpty: Boolean
            get() = _items.isEmpty()

        // ===== Aggregate 행위 메서드 =====

        /**
         * 상품 추가 - Aggregate Root를 통해서만 가능
         */
        fun addItem(
            productId: ProductId,
            productName: String,
            unitPrice: Money,
            quantity: Quantity
        ) {
            // 불변식 검증
            requirePendingStatus("상품 추가")
            requireMaxItems()

            // 기존 항목이 있으면 수량 증가
            val existingItem = _items.find { it.productId == productId }
            if (existingItem != null) {
                existingItem.increaseQuantity(quantity)
            } else {
                val newItem = OrderItem.create(productId, productName, unitPrice, quantity)
                _items.add(newItem)
            }

            // 도메인 이벤트 발행
            _domainEvents.add(OrderItemAdded(id, productId, quantity))
        }

        /**
         * 상품 제거 - Aggregate Root를 통해서만 가능
         */
        fun removeItem(itemId: OrderItemId) {
            requirePendingStatus("상품 제거")

            val item = _items.find { it.id == itemId }
                ?: throw IllegalArgumentException("주문 항목을 찾을 수 없습니다: $itemId")

            _items.remove(item)
            _domainEvents.add(OrderItemRemoved(id, itemId))
        }

        /**
         * 상품 수량 변경 - Aggregate Root를 통해서만 가능
         */
        fun updateItemQuantity(itemId: OrderItemId, newQuantity: Quantity) {
            requirePendingStatus("수량 변경")
            require(newQuantity.value > 0) { "수량은 1 이상이어야 합니다" }

            val item = _items.find { it.id == itemId }
                ?: throw IllegalArgumentException("주문 항목을 찾을 수 없습니다: $itemId")

            val currentQty = item.quantity
            when {
                newQuantity > currentQty -> item.increaseQuantity(Quantity(newQuantity.value - currentQty.value))
                newQuantity < currentQty -> item.decreaseQuantity(Quantity(currentQty.value - newQuantity.value))
            }
        }

        /**
         * 배송 주소 설정
         */
        fun setShippingAddress(address: Address) {
            requirePendingStatus("배송 주소 설정")
            _shippingAddress = address
        }

        /**
         * 주문 확정 - 모든 불변식 검증
         */
        fun confirm() {
            requirePendingStatus("주문 확정")

            // 불변식 검증
            require(_items.isNotEmpty()) { "주문에 상품이 없습니다" }
            require(_shippingAddress != null) { "배송 주소가 필요합니다" }
            require(totalAmount >= MIN_ORDER_AMOUNT) {
                "최소 주문 금액은 $MIN_ORDER_AMOUNT 입니다. 현재: $totalAmount"
            }

            _status = OrderStatus.CONFIRMED
            _domainEvents.add(OrderConfirmed(id))
        }

        /**
         * 주문 취소
         */
        fun cancel(reason: String) {
            require(_status.canTransitionTo(OrderStatus.CANCELLED)) {
                "현재 상태에서는 취소할 수 없습니다: $_status"
            }

            _status = OrderStatus.CANCELLED
            _domainEvents.add(OrderCancelled(id, reason))
        }

        /**
         * 배송 시작
         */
        fun ship() {
            require(_status == OrderStatus.CONFIRMED) { "확정된 주문만 배송할 수 있습니다" }
            _status = OrderStatus.SHIPPED
        }

        /**
         * 배송 완료
         */
        fun deliver() {
            require(_status == OrderStatus.SHIPPED) { "배송 중인 주문만 완료 처리할 수 있습니다" }
            _status = OrderStatus.DELIVERED
        }

        /**
         * 도메인 이벤트 클리어
         */
        fun clearDomainEvents() {
            _domainEvents.clear()
        }

        // ===== 불변식 검증 헬퍼 =====

        private fun requirePendingStatus(action: String) {
            require(_status == OrderStatus.PENDING) {
                "대기 중인 주문에서만 '$action'이 가능합니다. 현재 상태: $_status"
            }
        }

        private fun requireMaxItems() {
            require(_items.size < MAX_ITEMS_PER_ORDER) {
                "주문당 최대 ${MAX_ITEMS_PER_ORDER}개 항목만 추가할 수 있습니다"
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Order) return false
            return id == other.id
        }

        override fun hashCode() = id.hashCode()

        override fun toString() = "Order(id=$id, status=$status, items=${_items.size}, total=$totalAmount)"

        companion object {
            private const val MAX_ITEMS_PER_ORDER = 10
            private val MIN_ORDER_AMOUNT = Money.of(10000)

            /**
             * 팩토리 메서드 - 유일한 생성 진입점
             */
            fun create(customerId: CustomerId): Order {
                val orderId = OrderId.generate()
                val order = Order(
                    id = orderId,
                    customerId = customerId,
                    _status = OrderStatus.PENDING,
                    _items = mutableListOf(),
                    _shippingAddress = null,
                    createdAt = LocalDateTime.now(),
                    _domainEvents = mutableListOf()
                )
                order._domainEvents.add(OrderCreated(orderId, customerId))
                return order
            }
        }
    }

    // ===== Product Aggregate =====

    class Product private constructor(
        val id: ProductId,
        private var _name: String,
        private var _price: Money,
        private var _stockQuantity: Quantity
    ) {
        val name: String get() = _name
        val price: Money get() = _price
        val stockQuantity: Quantity get() = _stockQuantity
        val isInStock: Boolean get() = _stockQuantity.value > 0

        fun updatePrice(newPrice: Money) {
            _price = newPrice
        }

        fun addStock(quantity: Quantity) {
            _stockQuantity = _stockQuantity + quantity
        }

        fun reserveStock(quantity: Quantity) {
            require(_stockQuantity >= quantity) {
                "재고가 부족합니다. 현재: $_stockQuantity, 요청: $quantity"
            }
            _stockQuantity = _stockQuantity - quantity
        }

        fun releaseStock(quantity: Quantity) {
            _stockQuantity = _stockQuantity + quantity
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Product) return false
            return id == other.id
        }

        override fun hashCode() = id.hashCode()

        override fun toString() = "Product(id=$id, name=$name, price=$price, stock=$stockQuantity)"

        companion object {
            fun create(name: String, price: Money, initialStock: Quantity): Product {
                require(name.isNotBlank()) { "상품명은 필수입니다" }
                return Product(
                    id = ProductId.generate(),
                    _name = name,
                    _price = price,
                    _stockQuantity = initialStock
                )
            }
        }
    }

    // ===== Application Service =====

    class OrderApplicationService(
        private val orders: MutableMap<OrderId, Order> = mutableMapOf(),
        private val products: MutableMap<ProductId, Product> = mutableMapOf()
    ) {
        fun registerProduct(name: String, price: Money, stock: Quantity): Product {
            val product = Product.create(name, price, stock)
            products[product.id] = product
            return product
        }

        fun createOrder(customerId: CustomerId): Order {
            val order = Order.create(customerId)
            orders[order.id] = order
            return order
        }

        fun addItemToOrder(orderId: OrderId, productId: ProductId, quantity: Quantity) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val product = products[productId] ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")

            // 재고 예약 (Product Aggregate의 책임)
            product.reserveStock(quantity)

            try {
                // 주문에 항목 추가 (Order Aggregate의 책임)
                order.addItem(
                    productId = product.id,
                    productName = product.name,
                    unitPrice = product.price,
                    quantity = quantity
                )
            } catch (e: Exception) {
                // 실패 시 재고 롤백
                product.releaseStock(quantity)
                throw e
            }
        }

        fun removeItemFromOrder(orderId: OrderId, itemId: OrderItemId) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            val item = order.items.find { it.id == itemId }
                ?: throw IllegalArgumentException("주문 항목을 찾을 수 없습니다")

            // 재고 복구
            val product = products[item.productId]
            product?.releaseStock(item.quantity)

            // 주문에서 항목 제거
            order.removeItem(itemId)
        }

        fun setShippingAddress(orderId: OrderId, address: Address) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            order.setShippingAddress(address)
        }

        fun confirmOrder(orderId: OrderId) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            order.confirm()

            // 도메인 이벤트 처리 (실제로는 이벤트 버스로 발행)
            order.domainEvents.forEach { event ->
                println("  [Event] $event")
            }
            order.clearDomainEvents()
        }

        fun cancelOrder(orderId: OrderId, reason: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            // 재고 복구
            order.items.forEach { item ->
                products[item.productId]?.releaseStock(item.quantity)
            }

            order.cancel(reason)
        }

        fun getOrder(orderId: OrderId): Order? = orders[orderId]
        fun getProduct(productId: ProductId): Product? = products[productId]
    }
}

fun main() {
    println("=== Aggregate 패턴 적용 데모 ===")
    println()

    val service = Solution.OrderApplicationService()

    // 상품 등록
    val laptop = service.registerProduct(
        name = "노트북",
        price = Solution.Money.of(1500000),
        stock = Solution.Quantity.of(10)
    )
    val mouse = service.registerProduct(
        name = "마우스",
        price = Solution.Money.of(50000),
        stock = Solution.Quantity.of(100)
    )
    println("상품 등록 완료:")
    println("  - $laptop")
    println("  - $mouse")
    println()

    // 주문 생성
    val order = service.createOrder(Solution.CustomerId("CUST001"))
    println("주문 생성: $order")
    println()

    // 상품 추가 (Aggregate Root를 통해서만 가능)
    println("--- 상품 추가 ---")
    service.addItemToOrder(order.id, laptop.id, Solution.Quantity.of(1))
    service.addItemToOrder(order.id, mouse.id, Solution.Quantity.of(2))
    println("주문 상태: $order")
    println("주문 항목:")
    order.items.forEach { println("  - $it") }
    println("재고 현황:")
    println("  - 노트북: ${laptop.stockQuantity}")
    println("  - 마우스: ${mouse.stockQuantity}")
    println()

    // 배송 주소 설정
    println("--- 배송 주소 설정 ---")
    service.setShippingAddress(
        order.id,
        Solution.Address("서울시 강남구 테헤란로 123", "서울", "06234", "대한민국")
    )
    println("배송 주소: ${order.shippingAddress}")
    println()

    // 주문 확정
    println("--- 주문 확정 ---")
    service.confirmOrder(order.id)
    println("주문 상태: ${order.status}")
    println()

    // ===== Aggregate 패턴의 보호 기능 시연 =====
    println("=== Aggregate 패턴의 보호 기능 ===")
    println()

    // 보호 1: items 리스트 직접 수정 불가
    println("보호 1: items 리스트는 읽기 전용")
    println("  - order.items는 방어적 복사본 반환")
    println("  - 외부에서 직접 수정 불가능")

    // 보호 2: OrderItem 수량 직접 변경 불가
    println()
    println("보호 2: OrderItem 수량은 Aggregate Root를 통해서만 변경")
    // order.items[0].quantity = 100  // 컴파일 에러!
    println("  - item.quantity는 읽기 전용")

    // 보호 3: 상태 직접 변경 불가
    println()
    println("보호 3: 주문 상태는 비즈니스 메서드를 통해서만 변경")
    // order.status = OrderStatus.SHIPPED  // 컴파일 에러!
    println("  - order.status는 읽기 전용")

    // 보호 4: 확정된 주문은 수정 불가
    println()
    println("보호 4: 확정된 주문에 상품 추가 시도")
    try {
        service.addItemToOrder(order.id, laptop.id, Solution.Quantity.of(1))
    } catch (e: IllegalArgumentException) {
        println("  - 예외 발생: ${e.message}")
    }

    // 새 주문으로 추가 테스트
    println()
    println("=== 불변식 검증 테스트 ===")

    val order2 = service.createOrder(Solution.CustomerId("CUST002"))

    // 최소 주문 금액 미달
    println()
    println("테스트 1: 최소 주문 금액 미달")
    service.addItemToOrder(order2.id, mouse.id, Solution.Quantity.of(1))  // 50,000원
    service.setShippingAddress(
        order2.id,
        Solution.Address("부산시 해운대구", "부산", "48094", "대한민국")
    )
    try {
        service.confirmOrder(order2.id)
    } catch (e: IllegalArgumentException) {
        println("  - 예외 발생: ${e.message}")
    }

    // 재고 부족
    println()
    println("테스트 2: 재고 부족")
    try {
        service.addItemToOrder(order2.id, laptop.id, Solution.Quantity.of(100))
    } catch (e: IllegalArgumentException) {
        println("  - 예외 발생: ${e.message}")
    }

    println()
    println("=== Aggregate 패턴 장점 요약 ===")
    println("1. Aggregate Root(Order)를 통해서만 내부 객체 접근")
    println("2. 불변식이 항상 보장됨 (최소 금액, 최대 항목 수 등)")
    println("3. 트랜잭션 일관성 경계가 명확함")
    println("4. 도메인 이벤트를 통한 느슨한 결합")
    println("5. 외부에서 내부 상태 직접 변경 불가능")
    println("6. 비즈니스 규칙이 Aggregate 내부에 캡슐화")
}
