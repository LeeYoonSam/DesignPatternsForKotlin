package architecture.ddd.aggregate

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 시스템 - Aggregate 패턴 적용 전
 *
 * 문제점:
 * - 객체 간 경계가 불명확함
 * - 외부에서 내부 객체를 직접 수정 가능
 * - 일관성 규칙이 분산되어 있음
 * - 트랜잭션 경계가 불분명함
 * - 도메인 불변식(Invariant)이 쉽게 깨짐
 */
class Problem {

    // 주문
    class Order(
        val id: String = UUID.randomUUID().toString(),
        var customerId: String,
        var status: String = "PENDING",
        val items: MutableList<OrderItem> = mutableListOf(),  // 외부에서 직접 접근 가능
        var shippingAddress: Address? = null,
        val createdAt: LocalDateTime = LocalDateTime.now()
    ) {
        fun calculateTotal(): BigDecimal {
            return items.fold(BigDecimal.ZERO) { acc, item ->
                acc + item.calculateSubtotal()
            }
        }
    }

    // 주문 항목
    class OrderItem(
        val id: String = UUID.randomUUID().toString(),
        var productId: String,
        var productName: String,
        var unitPrice: BigDecimal,
        var quantity: Int  // 외부에서 직접 수정 가능
    ) {
        fun calculateSubtotal(): BigDecimal = unitPrice * BigDecimal(quantity)
    }

    // 주소
    data class Address(
        var street: String,  // 가변
        var city: String,
        var zipCode: String,
        var country: String
    )

    // 상품
    class Product(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        var price: BigDecimal,
        var stockQuantity: Int  // 외부에서 직접 수정 가능
    )

    // 주문 서비스 - 비즈니스 규칙이 여기에 분산됨
    class OrderService(
        private val orders: MutableMap<String, Order> = mutableMapOf(),
        private val products: MutableMap<String, Product> = mutableMapOf()
    ) {
        fun createOrder(customerId: String): Order {
            val order = Order(customerId = customerId)
            orders[order.id] = order
            return order
        }

        fun addProduct(product: Product) {
            products[product.id] = product
        }

        // 문제: 비즈니스 규칙이 서비스에 분산
        fun addItemToOrder(orderId: String, productId: String, quantity: Int) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val product = products[productId] ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")

            // 규칙 1: 재고 확인 - 서비스에서 검증
            if (product.stockQuantity < quantity) {
                throw IllegalStateException("재고가 부족합니다")
            }

            // 규칙 2: 주문 상태 확인 - 서비스에서 검증
            if (order.status != "PENDING") {
                throw IllegalStateException("대기 중인 주문에만 상품을 추가할 수 있습니다")
            }

            // 규칙 3: 최대 항목 수 확인 - 서비스에서 검증
            if (order.items.size >= 10) {
                throw IllegalStateException("주문당 최대 10개 항목만 추가할 수 있습니다")
            }

            // 재고 차감
            product.stockQuantity -= quantity

            // 기존 항목이 있으면 수량 증가
            val existingItem = order.items.find { it.productId == productId }
            if (existingItem != null) {
                existingItem.quantity += quantity
            } else {
                order.items.add(
                    OrderItem(
                        productId = productId,
                        productName = product.name,
                        unitPrice = product.price,
                        quantity = quantity
                    )
                )
            }
        }

        // 문제: 주문 항목을 직접 수정하면 재고 동기화가 깨짐
        fun removeItemFromOrder(orderId: String, itemId: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            val item = order.items.find { it.id == itemId }
            if (item != null) {
                // 재고 복구 - 하지만 이 로직을 잊으면?
                val product = products[item.productId]
                product?.let { it.stockQuantity += item.quantity }

                order.items.remove(item)
            }
        }

        fun confirmOrder(orderId: String) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")

            // 규칙: 주소 필수
            if (order.shippingAddress == null) {
                throw IllegalStateException("배송 주소가 필요합니다")
            }

            // 규칙: 최소 1개 이상의 항목
            if (order.items.isEmpty()) {
                throw IllegalStateException("주문에 상품이 없습니다")
            }

            // 규칙: 최소 주문 금액
            if (order.calculateTotal() < BigDecimal("10000")) {
                throw IllegalStateException("최소 주문 금액은 10,000원입니다")
            }

            order.status = "CONFIRMED"
        }
    }
}

fun main() {
    val service = Problem.OrderService()

    // 상품 등록
    val laptop = Problem.Product(
        name = "노트북",
        price = BigDecimal("1500000"),
        stockQuantity = 10
    )
    val mouse = Problem.Product(
        name = "마우스",
        price = BigDecimal("50000"),
        stockQuantity = 100
    )
    service.addProduct(laptop)
    service.addProduct(mouse)

    // 주문 생성
    val order = service.createOrder("CUST001")
    println("주문 생성: ${order.id}")

    // 정상적인 상품 추가
    service.addItemToOrder(order.id, laptop.id, 1)
    service.addItemToOrder(order.id, mouse.id, 2)
    println("상품 추가 완료")
    println("현재 재고 - 노트북: ${laptop.stockQuantity}, 마우스: ${mouse.stockQuantity}")

    println()
    println("=== 문제점 시연 ===")
    println()

    // 문제 1: 외부에서 items 리스트에 직접 접근하여 수정
    println("문제 1: 외부에서 items 직접 수정")
    order.items.clear()  // 서비스를 거치지 않고 직접 삭제!
    println("  - 직접 items.clear() 호출 -> 재고 복구 안됨!")
    println("  - 노트북 재고: ${laptop.stockQuantity} (복구되어야 하는데 9로 유지)")

    // 문제 2: 외부에서 OrderItem의 수량 직접 수정
    service.addItemToOrder(order.id, laptop.id, 1)
    val item = order.items.first()
    println()
    println("문제 2: 외부에서 OrderItem 수량 직접 수정")
    println("  - 현재 수량: ${item.quantity}")
    item.quantity = 100  // 직접 수정!
    println("  - 직접 quantity = 100 설정 -> ${item.quantity}")
    println("  - 재고 확인 없이 수량 증가됨!")

    // 문제 3: 주문 상태 직접 변경
    println()
    println("문제 3: 주문 상태 직접 변경")
    order.status = "SHIPPED"  // 비즈니스 규칙 무시하고 직접 변경
    println("  - 직접 status = 'SHIPPED' -> ${order.status}")
    println("  - 주소도 없고, 확정도 안 했는데 배송됨!")

    // 문제 4: 주소 객체 직접 수정
    println()
    println("문제 4: 주소 객체 가변성")
    order.shippingAddress = Problem.Address("서울시 강남구", "서울", "06234", "한국")
    val address = order.shippingAddress!!
    address.street = "부산시 해운대구"  // 직접 수정!
    println("  - 주소 직접 수정 가능: ${order.shippingAddress}")

    println()
    println("=== 문제점 요약 ===")
    println("1. 내부 컬렉션(items)이 외부에 노출되어 직접 수정 가능")
    println("2. 엔티티(OrderItem)의 속성을 외부에서 직접 변경 가능")
    println("3. Aggregate Root를 거치지 않고 내부 객체 수정 가능")
    println("4. 비즈니스 규칙(불변식)이 서비스에 분산되어 우회 가능")
    println("5. 트랜잭션 일관성 경계가 불분명함")
    println("6. 재고와 주문 항목 간의 일관성이 깨지기 쉬움")
}
