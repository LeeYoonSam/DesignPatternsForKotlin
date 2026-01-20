package architecture.ddd.ecommerce

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 전자상거래 주문 시스템 - Value Object & Entity 패턴 적용 전
 *
 * 문제점:
 * - 원시 타입 사용으로 인한 도메인 의미 손실
 * - 유효성 검증 로직이 분산됨
 * - 동등성 비교가 불명확함
 * - 불변성이 보장되지 않음
 * - 식별자 관리가 일관되지 않음
 * - 도메인 로직이 외부에 노출됨
 */
class Problem {

    // 문제가 있는 코드: 원시 타입과 가변 객체 사용
    class Order(
        var id: String?,  // null 가능, 가변
        var customerId: String,
        var customerName: String,
        var customerEmail: String,  // 유효성 검증 없음
        var shippingStreet: String,
        var shippingCity: String,
        var shippingZipCode: String,
        var shippingCountry: String,
        var items: MutableList<OrderItem> = mutableListOf(),
        var status: String = "PENDING",  // 문자열로 상태 관리
        var createdAt: LocalDateTime = LocalDateTime.now()
    ) {
        // 총 금액 계산 - 통화 정보 없음
        fun calculateTotal(): Double {
            return items.sumOf { it.price * it.quantity }
        }

        // 동등성 비교 문제 - id가 null일 수 있음
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Order) return false
            return id == other.id  // id가 null이면 문제 발생
        }

        override fun hashCode(): Int = id?.hashCode() ?: 0
    }

    class OrderItem(
        var productId: String,
        var productName: String,
        var price: Double,  // 통화 정보 없음, 부동소수점 문제
        var quantity: Int
    ) {
        // 소계 계산
        fun getSubtotal(): Double = price * quantity
    }

    class Customer(
        var id: String?,
        var name: String,
        var email: String,  // 이메일 형식 검증 없음
        var phone: String,  // 전화번호 형식 검증 없음
        var street: String,
        var city: String,
        var zipCode: String,
        var country: String
    ) {
        // 이메일 변경 - 유효성 검증이 호출자 책임
        fun updateEmail(newEmail: String) {
            // 검증 없이 그냥 변경
            this.email = newEmail
        }

        // 주소 변경 - 여러 필드를 개별적으로 변경해야 함
        fun updateAddress(street: String, city: String, zipCode: String, country: String) {
            this.street = street
            this.city = city
            this.zipCode = zipCode
            this.country = country
        }
    }

    class Product(
        var id: String?,
        var name: String,
        var description: String,
        var price: Double,  // 통화 정보 없음
        var stock: Int
    ) {
        // 재고 감소 - 음수 가능
        fun decreaseStock(quantity: Int) {
            this.stock -= quantity  // 음수 체크 없음
        }

        // 가격 변경 - 음수 가능
        fun updatePrice(newPrice: Double) {
            this.price = newPrice  // 음수 체크 없음
        }
    }

    // 주문 서비스 - 유효성 검증이 여기저기 분산됨
    class OrderService {
        private val orders = mutableMapOf<String, Order>()

        fun createOrder(
            customerId: String,
            customerName: String,
            customerEmail: String,
            shippingStreet: String,
            shippingCity: String,
            shippingZipCode: String,
            shippingCountry: String
        ): Order {
            // 이메일 유효성 검증 - 서비스에서 수행
            if (!customerEmail.contains("@")) {
                throw IllegalArgumentException("Invalid email: $customerEmail")
            }

            // 우편번호 유효성 검증 - 서비스에서 수행
            if (shippingZipCode.length < 5) {
                throw IllegalArgumentException("Invalid zip code: $shippingZipCode")
            }

            val order = Order(
                id = UUID.randomUUID().toString(),
                customerId = customerId,
                customerName = customerName,
                customerEmail = customerEmail,
                shippingStreet = shippingStreet,
                shippingCity = shippingCity,
                shippingZipCode = shippingZipCode,
                shippingCountry = shippingCountry
            )

            orders[order.id!!] = order
            return order
        }

        fun addItem(orderId: String, productId: String, productName: String, price: Double, quantity: Int) {
            val order = orders[orderId] ?: throw IllegalArgumentException("Order not found: $orderId")

            // 가격 유효성 검증 - 서비스에서 수행
            if (price < 0) {
                throw IllegalArgumentException("Price cannot be negative: $price")
            }

            // 수량 유효성 검증 - 서비스에서 수행
            if (quantity <= 0) {
                throw IllegalArgumentException("Quantity must be positive: $quantity")
            }

            order.items.add(OrderItem(productId, productName, price, quantity))
        }

        // 주문 상태 변경 - 문자열 비교로 상태 전이 검증
        fun updateStatus(orderId: String, newStatus: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("Order not found: $orderId")

            // 상태 전이 검증 - 하드코딩된 문자열
            val validTransitions = mapOf(
                "PENDING" to listOf("CONFIRMED", "CANCELLED"),
                "CONFIRMED" to listOf("SHIPPED", "CANCELLED"),
                "SHIPPED" to listOf("DELIVERED"),
                "DELIVERED" to emptyList(),
                "CANCELLED" to emptyList()
            )

            if (newStatus !in (validTransitions[order.status] ?: emptyList())) {
                throw IllegalStateException("Invalid status transition: ${order.status} -> $newStatus")
            }

            order.status = newStatus
        }
    }
}

fun main() {
    val service = Problem.OrderService()

    println("=== 문제가 있는 코드 데모 ===")
    println()

    // 1. 주문 생성
    val order = service.createOrder(
        customerId = "CUST001",
        customerName = "홍길동",
        customerEmail = "hong@example.com",
        shippingStreet = "서울시 강남구 테헤란로 123",
        shippingCity = "서울",
        shippingZipCode = "06234",
        shippingCountry = "대한민국"
    )
    println("주문 생성: ${order.id}")

    // 2. 상품 추가
    service.addItem(order.id!!, "PROD001", "노트북", 1500000.0, 1)
    service.addItem(order.id!!, "PROD002", "마우스", 50000.0, 2)
    println("상품 추가 완료")

    // 3. 총액 계산 - 부동소수점 문제 발생 가능
    val total = order.calculateTotal()
    println("총 금액: $total 원")  // 통화 정보 없음

    // 4. 문제점 시연
    println()
    println("=== 문제점 시연 ===")

    // 문제 1: 가변 객체로 인한 의도치 않은 변경
    order.status = "INVALID_STATUS"  // 직접 변경 가능
    println("상태가 잘못된 값으로 변경됨: ${order.status}")

    // 문제 2: 원시 타입으로 인한 유효성 검증 누락
    order.customerEmail = "invalid-email"  // 유효성 검증 없이 변경
    println("이메일이 잘못된 형식으로 변경됨: ${order.customerEmail}")

    // 문제 3: 부동소수점 정밀도 문제
    val price1 = 0.1
    val price2 = 0.2
    val sum = price1 + price2
    println("0.1 + 0.2 = $sum (예상: 0.3)")  // 0.30000000000000004

    // 문제 4: null 가능한 식별자
    val newOrder = Problem.Order(
        id = null,  // id가 null
        customerId = "CUST002",
        customerName = "김철수",
        customerEmail = "kim@example.com",
        shippingStreet = "부산시 해운대구",
        shippingCity = "부산",
        shippingZipCode = "48094",
        shippingCountry = "대한민국"
    )
    println("null id를 가진 주문: ${newOrder.id}")
    println("두 주문이 같은가? ${order == newOrder}")  // id가 null이면 문제

    println()
    println("=== 문제점 요약 ===")
    println("1. 원시 타입 사용으로 도메인 의미 손실 (Double로 금액 표현)")
    println("2. 유효성 검증이 서비스 계층에 분산됨")
    println("3. 가변 객체로 인한 의도치 않은 상태 변경 가능")
    println("4. null 가능한 식별자로 인한 동등성 비교 문제")
    println("5. 문자열로 상태 관리 - 타입 안전성 부재")
    println("6. 부동소수점으로 금액 처리 - 정밀도 문제")
}
