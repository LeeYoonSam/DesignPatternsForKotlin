package architecture.ddd.ecommerce

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.*

/**
 * 전자상거래 주문 시스템 - Value Object & Entity 패턴 적용
 *
 * Value Object 특징:
 * - 불변성 (Immutability)
 * - 속성 값으로 동등성 비교 (Equality by Value)
 * - 자기 유효성 검증 (Self-Validation)
 * - 부작용 없는 행위 (Side-Effect-Free Behavior)
 *
 * Entity 특징:
 * - 고유 식별자로 동등성 비교 (Equality by Identity)
 * - 연속성 (Continuity) - 상태가 변해도 동일한 엔티티
 * - 생명주기 관리 (Lifecycle Management)
 */
class Solution {

    // ===== Value Objects =====

    /**
     * Money - 금액을 표현하는 Value Object
     * - 불변
     * - BigDecimal로 정밀한 계산
     * - 통화 정보 포함
     */
    data class Money private constructor(
        val amount: BigDecimal,
        val currency: Currency
    ) : Comparable<Money> {

        init {
            require(amount >= BigDecimal.ZERO) { "금액은 음수일 수 없습니다: $amount" }
        }

        // 금액 연산 - 새로운 Money 객체 반환 (불변성)
        operator fun plus(other: Money): Money {
            requireSameCurrency(other)
            return Money(amount.add(other.amount), currency)
        }

        operator fun minus(other: Money): Money {
            requireSameCurrency(other)
            val result = amount.subtract(other.amount)
            require(result >= BigDecimal.ZERO) { "결과 금액이 음수입니다" }
            return Money(result, currency)
        }

        operator fun times(multiplier: Int): Money {
            require(multiplier >= 0) { "승수는 음수일 수 없습니다" }
            return Money(amount.multiply(BigDecimal(multiplier)), currency)
        }

        operator fun times(multiplier: BigDecimal): Money {
            require(multiplier >= BigDecimal.ZERO) { "승수는 음수일 수 없습니다" }
            return Money(amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP), currency)
        }

        private fun requireSameCurrency(other: Money) {
            require(currency == other.currency) {
                "통화가 일치하지 않습니다: $currency vs ${other.currency}"
            }
        }

        override fun compareTo(other: Money): Int {
            requireSameCurrency(other)
            return amount.compareTo(other.amount)
        }

        override fun toString(): String = "$currency $amount"

        companion object {
            fun of(amount: Double, currency: Currency = Currency.KRW): Money {
                return Money(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), currency)
            }

            fun of(amount: Long, currency: Currency = Currency.KRW): Money {
                return Money(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), currency)
            }

            fun zero(currency: Currency = Currency.KRW): Money {
                return Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), currency)
            }
        }

        enum class Currency(val symbol: String) {
            KRW("₩"), USD("$"), EUR("€"), JPY("¥")
        }
    }

    /**
     * Email - 이메일 주소를 표현하는 Value Object
     * - 자기 유효성 검증
     * - 불변
     */
    @JvmInline
    value class Email private constructor(val value: String) {
        init {
            require(isValid(value)) { "유효하지 않은 이메일 형식입니다: $value" }
        }

        val domain: String get() = value.substringAfter("@")
        val localPart: String get() = value.substringBefore("@")

        override fun toString(): String = value

        companion object {
            private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

            fun of(value: String): Email = Email(value.lowercase().trim())

            fun isValid(value: String): Boolean = EMAIL_REGEX.matches(value.trim())
        }
    }

    /**
     * PhoneNumber - 전화번호를 표현하는 Value Object
     */
    @JvmInline
    value class PhoneNumber private constructor(val value: String) {
        init {
            require(isValid(value)) { "유효하지 않은 전화번호 형식입니다: $value" }
        }

        val formatted: String
            get() = when {
                value.length == 11 -> "${value.substring(0, 3)}-${value.substring(3, 7)}-${value.substring(7)}"
                value.length == 10 -> "${value.substring(0, 3)}-${value.substring(3, 6)}-${value.substring(6)}"
                else -> value
            }

        override fun toString(): String = formatted

        companion object {
            private val PHONE_REGEX = Regex("^\\d{10,11}$")

            fun of(value: String): PhoneNumber {
                val cleaned = value.replace(Regex("[^0-9]"), "")
                return PhoneNumber(cleaned)
            }

            fun isValid(value: String): Boolean {
                val cleaned = value.replace(Regex("[^0-9]"), "")
                return PHONE_REGEX.matches(cleaned)
            }
        }
    }

    /**
     * Address - 주소를 표현하는 Value Object
     * - 여러 필드를 하나의 개념으로 묶음
     * - 불변
     */
    data class Address(
        val street: String,
        val city: String,
        val zipCode: ZipCode,
        val country: String
    ) {
        init {
            require(street.isNotBlank()) { "도로명 주소는 필수입니다" }
            require(city.isNotBlank()) { "도시는 필수입니다" }
            require(country.isNotBlank()) { "국가는 필수입니다" }
        }

        val fullAddress: String get() = "$street, $city $zipCode, $country"

        override fun toString(): String = fullAddress
    }

    /**
     * ZipCode - 우편번호를 표현하는 Value Object
     */
    @JvmInline
    value class ZipCode private constructor(val value: String) {
        init {
            require(isValid(value)) { "유효하지 않은 우편번호입니다: $value" }
        }

        override fun toString(): String = value

        companion object {
            private val ZIPCODE_REGEX = Regex("^\\d{5}$")

            fun of(value: String): ZipCode = ZipCode(value.trim())

            fun isValid(value: String): Boolean = ZIPCODE_REGEX.matches(value.trim())
        }
    }

    /**
     * Quantity - 수량을 표현하는 Value Object
     */
    @JvmInline
    value class Quantity private constructor(val value: Int) : Comparable<Quantity> {
        init {
            require(value >= 0) { "수량은 음수일 수 없습니다: $value" }
        }

        operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)
        operator fun minus(other: Quantity): Quantity {
            require(value >= other.value) { "결과 수량이 음수입니다" }
            return Quantity(value - other.value)
        }

        override fun compareTo(other: Quantity): Int = value.compareTo(other.value)
        override fun toString(): String = value.toString()

        companion object {
            fun of(value: Int): Quantity = Quantity(value)
            fun zero(): Quantity = Quantity(0)
        }
    }

    // ===== Entity Identifiers (Value Objects) =====

    @JvmInline
    value class OrderId private constructor(val value: String) {
        override fun toString(): String = value

        companion object {
            fun generate(): OrderId = OrderId(UUID.randomUUID().toString())
            fun of(value: String): OrderId {
                require(value.isNotBlank()) { "OrderId는 비어있을 수 없습니다" }
                return OrderId(value)
            }
        }
    }

    @JvmInline
    value class CustomerId private constructor(val value: String) {
        override fun toString(): String = value

        companion object {
            fun generate(): CustomerId = CustomerId(UUID.randomUUID().toString())
            fun of(value: String): CustomerId {
                require(value.isNotBlank()) { "CustomerId는 비어있을 수 없습니다" }
                return CustomerId(value)
            }
        }
    }

    @JvmInline
    value class ProductId private constructor(val value: String) {
        override fun toString(): String = value

        companion object {
            fun generate(): ProductId = ProductId(UUID.randomUUID().toString())
            fun of(value: String): ProductId {
                require(value.isNotBlank()) { "ProductId는 비어있을 수 없습니다" }
                return ProductId(value)
            }
        }
    }

    // ===== Entities =====

    /**
     * OrderStatus - 주문 상태 (Value Object + 상태 전이 로직)
     */
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

    /**
     * OrderItem - 주문 항목 (Value Object)
     * - 불변
     * - 주문 내에서만 의미가 있음
     */
    data class OrderItem(
        val productId: ProductId,
        val productName: String,
        val unitPrice: Money,
        val quantity: Quantity
    ) {
        init {
            require(productName.isNotBlank()) { "상품명은 필수입니다" }
            require(quantity.value > 0) { "수량은 1 이상이어야 합니다" }
        }

        val subtotal: Money get() = unitPrice * quantity.value

        override fun toString(): String = "$productName x${quantity} = $subtotal"
    }

    /**
     * Order - 주문 Entity
     * - 고유 식별자(OrderId)로 동등성 비교
     * - 상태 변경 메서드를 통한 캡슐화
     * - 불변식(Invariant) 보장
     */
    class Order private constructor(
        val id: OrderId,
        val customerId: CustomerId,
        val customerName: String,
        val customerEmail: Email,
        val shippingAddress: Address,
        private val _items: MutableList<OrderItem>,
        private var _status: OrderStatus,
        val createdAt: LocalDateTime
    ) {
        val items: List<OrderItem> get() = _items.toList()  // 방어적 복사
        val status: OrderStatus get() = _status

        val totalAmount: Money
            get() = _items.fold(Money.zero()) { acc, item -> acc + item.subtotal }

        val itemCount: Int get() = _items.sumOf { it.quantity.value }

        // 상품 추가
        fun addItem(item: OrderItem) {
            require(_status == OrderStatus.PENDING) { "대기 중인 주문에만 상품을 추가할 수 있습니다" }

            val existingIndex = _items.indexOfFirst { it.productId == item.productId }
            if (existingIndex >= 0) {
                val existing = _items[existingIndex]
                _items[existingIndex] = existing.copy(
                    quantity = existing.quantity + item.quantity
                )
            } else {
                _items.add(item)
            }
        }

        // 상품 제거
        fun removeItem(productId: ProductId) {
            require(_status == OrderStatus.PENDING) { "대기 중인 주문에서만 상품을 제거할 수 있습니다" }
            _items.removeAll { it.productId == productId }
        }

        // 상태 변경
        fun confirm() {
            require(_items.isNotEmpty()) { "상품이 없는 주문은 확정할 수 없습니다" }
            transitionTo(OrderStatus.CONFIRMED)
        }

        fun ship() {
            transitionTo(OrderStatus.SHIPPED)
        }

        fun deliver() {
            transitionTo(OrderStatus.DELIVERED)
        }

        fun cancel() {
            transitionTo(OrderStatus.CANCELLED)
        }

        private fun transitionTo(newStatus: OrderStatus) {
            require(_status.canTransitionTo(newStatus)) {
                "상태 전이가 불가능합니다: $_status -> $newStatus"
            }
            _status = newStatus
        }

        // Entity 동등성: 식별자로 비교
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Order) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String {
            return "Order(id=$id, customer=$customerName, status=$status, total=$totalAmount, items=${items.size})"
        }

        companion object {
            fun create(
                customerId: CustomerId,
                customerName: String,
                customerEmail: Email,
                shippingAddress: Address
            ): Order {
                return Order(
                    id = OrderId.generate(),
                    customerId = customerId,
                    customerName = customerName,
                    customerEmail = customerEmail,
                    shippingAddress = shippingAddress,
                    _items = mutableListOf(),
                    _status = OrderStatus.PENDING,
                    createdAt = LocalDateTime.now()
                )
            }
        }
    }

    /**
     * Customer - 고객 Entity
     */
    class Customer private constructor(
        val id: CustomerId,
        private var _name: String,
        private var _email: Email,
        private var _phone: PhoneNumber,
        private var _address: Address
    ) {
        val name: String get() = _name
        val email: Email get() = _email
        val phone: PhoneNumber get() = _phone
        val address: Address get() = _address

        fun updateName(newName: String) {
            require(newName.isNotBlank()) { "이름은 필수입니다" }
            _name = newName
        }

        fun updateEmail(newEmail: Email) {
            _email = newEmail
        }

        fun updatePhone(newPhone: PhoneNumber) {
            _phone = newPhone
        }

        fun updateAddress(newAddress: Address) {
            _address = newAddress
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Customer) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "Customer(id=$id, name=$name, email=$email)"

        companion object {
            fun create(name: String, email: Email, phone: PhoneNumber, address: Address): Customer {
                require(name.isNotBlank()) { "이름은 필수입니다" }
                return Customer(
                    id = CustomerId.generate(),
                    _name = name,
                    _email = email,
                    _phone = phone,
                    _address = address
                )
            }
        }
    }

    /**
     * Product - 상품 Entity
     */
    class Product private constructor(
        val id: ProductId,
        private var _name: String,
        private var _description: String,
        private var _price: Money,
        private var _stock: Quantity
    ) {
        val name: String get() = _name
        val description: String get() = _description
        val price: Money get() = _price
        val stock: Quantity get() = _stock

        val isInStock: Boolean get() = _stock.value > 0

        fun updatePrice(newPrice: Money) {
            _price = newPrice
        }

        fun addStock(quantity: Quantity) {
            _stock = _stock + quantity
        }

        fun decreaseStock(quantity: Quantity) {
            require(_stock >= quantity) { "재고가 부족합니다: 현재 $_stock, 요청 $quantity" }
            _stock = _stock - quantity
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Product) return false
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()

        override fun toString(): String = "Product(id=$id, name=$name, price=$price, stock=$stock)"

        companion object {
            fun create(name: String, description: String, price: Money, stock: Quantity): Product {
                require(name.isNotBlank()) { "상품명은 필수입니다" }
                return Product(
                    id = ProductId.generate(),
                    _name = name,
                    _description = description,
                    _price = price,
                    _stock = stock
                )
            }
        }
    }
}

fun main() {
    println("=== Value Object & Entity 패턴 적용 데모 ===")
    println()

    // 1. Value Object 생성 및 사용
    println("--- 1. Value Objects ---")

    val price1 = Solution.Money.of(1500000)
    val price2 = Solution.Money.of(50000)
    val total = price1 + price2 * 2
    println("금액 계산: $price1 + $price2 x 2 = $total")

    val email = Solution.Email.of("hong@example.com")
    println("이메일: $email (도메인: ${email.domain})")

    val phone = Solution.PhoneNumber.of("010-1234-5678")
    println("전화번호: $phone")

    val address = Solution.Address(
        street = "서울시 강남구 테헤란로 123",
        city = "서울",
        zipCode = Solution.ZipCode.of("06234"),
        country = "대한민국"
    )
    println("주소: $address")
    println()

    // 2. Entity 생성
    println("--- 2. Entities ---")

    val customer = Solution.Customer.create(
        name = "홍길동",
        email = email,
        phone = phone,
        address = address
    )
    println("고객 생성: $customer")

    val product1 = Solution.Product.create(
        name = "노트북",
        description = "고성능 노트북",
        price = Solution.Money.of(1500000),
        stock = Solution.Quantity.of(10)
    )
    val product2 = Solution.Product.create(
        name = "마우스",
        description = "무선 마우스",
        price = Solution.Money.of(50000),
        stock = Solution.Quantity.of(100)
    )
    println("상품 생성: $product1")
    println("상품 생성: $product2")
    println()

    // 3. 주문 생성 및 처리
    println("--- 3. Order Lifecycle ---")

    val order = Solution.Order.create(
        customerId = customer.id,
        customerName = customer.name,
        customerEmail = customer.email,
        shippingAddress = customer.address
    )
    println("주문 생성: $order")

    // 상품 추가
    order.addItem(
        Solution.OrderItem(
            productId = product1.id,
            productName = product1.name,
            unitPrice = product1.price,
            quantity = Solution.Quantity.of(1)
        )
    )
    order.addItem(
        Solution.OrderItem(
            productId = product2.id,
            productName = product2.name,
            unitPrice = product2.price,
            quantity = Solution.Quantity.of(2)
        )
    )
    println("상품 추가 후: $order")
    println("주문 항목:")
    order.items.forEach { println("  - $it") }
    println()

    // 주문 상태 전이
    println("--- 4. Order Status Transitions ---")
    println("현재 상태: ${order.status}")

    order.confirm()
    println("주문 확정: ${order.status}")

    order.ship()
    println("배송 시작: ${order.status}")

    order.deliver()
    println("배송 완료: ${order.status}")
    println()

    // 5. 유효성 검증 데모
    println("--- 5. Validation Demo ---")

    try {
        Solution.Email.of("invalid-email")
    } catch (e: IllegalArgumentException) {
        println("이메일 검증 실패: ${e.message}")
    }

    try {
        Solution.Money.of(-1000)
    } catch (e: IllegalArgumentException) {
        println("금액 검증 실패: ${e.message}")
    }

    try {
        order.cancel()  // 이미 배송 완료된 주문은 취소 불가
    } catch (e: IllegalArgumentException) {
        println("상태 전이 실패: ${e.message}")
    }
    println()

    // 6. Entity 동등성 vs Value Object 동등성
    println("--- 6. Equality Comparison ---")

    val money1 = Solution.Money.of(1000)
    val money2 = Solution.Money.of(1000)
    println("Value Object (Money): money1 == money2 -> ${money1 == money2}")  // true

    val orderId1 = Solution.OrderId.of("ORDER-001")
    val orderId2 = Solution.OrderId.of("ORDER-001")
    println("Value Object (OrderId): orderId1 == orderId2 -> ${orderId1 == orderId2}")  // true

    val order2 = Solution.Order.create(
        customerId = customer.id,
        customerName = customer.name,
        customerEmail = customer.email,
        shippingAddress = customer.address
    )
    println("Entity (Order): order == order2 -> ${order == order2}")  // false (다른 ID)
    println()

    println("=== 패턴 적용 장점 ===")
    println("1. 도메인 의미가 명확함 (Money, Email, Address 등)")
    println("2. 자기 유효성 검증으로 항상 유효한 상태 보장")
    println("3. 불변 Value Object로 안전한 공유 가능")
    println("4. Entity는 식별자로 동등성 비교")
    println("5. 상태 전이 로직이 도메인 객체 내부에 캡슐화")
    println("6. BigDecimal로 정밀한 금액 계산")
}
