package architecture.ddd.domainevent

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 전자상거래 주문 시스템 - Domain Event 패턴 적용 전
 *
 * 문제점:
 * - 도메인 객체 간 강한 결합
 * - 부수 효과가 핵심 로직에 섞여 있음
 * - 트랜잭션 범위가 너무 넓어짐
 * - 새로운 기능 추가 시 기존 코드 수정 필요
 * - 테스트하기 어려움
 * - 감사 로그 추적이 어려움
 */
class Problem {

    data class OrderId(val value: String = UUID.randomUUID().toString())
    data class CustomerId(val value: String)
    data class ProductId(val value: String)

    // 주문
    class Order(
        val id: OrderId = OrderId(),
        val customerId: CustomerId,
        var status: String = "PENDING",
        val items: MutableList<OrderItem> = mutableListOf(),
        val createdAt: LocalDateTime = LocalDateTime.now()
    ) {
        fun calculateTotal(): BigDecimal =
            items.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
    }

    data class OrderItem(
        val productId: ProductId,
        val productName: String,
        val unitPrice: BigDecimal,
        val quantity: Int
    ) {
        val subtotal: BigDecimal get() = unitPrice * BigDecimal(quantity)
    }

    // 고객
    class Customer(
        val id: CustomerId,
        var name: String,
        var email: String,
        var points: Int = 0
    )

    // 상품
    class Product(
        val id: ProductId,
        var name: String,
        var price: BigDecimal,
        var stock: Int
    )

    // 알림 서비스
    class NotificationService {
        fun sendEmail(email: String, subject: String, body: String) {
            println("  [Email] To: $email, Subject: $subject")
        }

        fun sendSms(phone: String, message: String) {
            println("  [SMS] To: $phone, Message: $message")
        }

        fun sendPush(userId: String, message: String) {
            println("  [Push] To: $userId, Message: $message")
        }
    }

    // 재고 서비스
    class InventoryService(private val products: MutableMap<ProductId, Product>) {
        fun decreaseStock(productId: ProductId, quantity: Int) {
            val product = products[productId]
                ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")
            if (product.stock < quantity) {
                throw IllegalStateException("재고가 부족합니다")
            }
            product.stock -= quantity
            println("  [Inventory] ${product.name} 재고 감소: -$quantity (남은 재고: ${product.stock})")
        }

        fun increaseStock(productId: ProductId, quantity: Int) {
            val product = products[productId]
                ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")
            product.stock += quantity
            println("  [Inventory] ${product.name} 재고 증가: +$quantity (남은 재고: ${product.stock})")
        }
    }

    // 포인트 서비스
    class PointService(private val customers: MutableMap<CustomerId, Customer>) {
        fun addPoints(customerId: CustomerId, points: Int) {
            val customer = customers[customerId]
                ?: throw IllegalArgumentException("고객을 찾을 수 없습니다")
            customer.points += points
            println("  [Points] ${customer.name}에게 $points 포인트 적립 (총: ${customer.points})")
        }

        fun deductPoints(customerId: CustomerId, points: Int) {
            val customer = customers[customerId]
                ?: throw IllegalArgumentException("고객을 찾을 수 없습니다")
            if (customer.points < points) {
                throw IllegalStateException("포인트가 부족합니다")
            }
            customer.points -= points
            println("  [Points] ${customer.name}에게서 $points 포인트 차감 (총: ${customer.points})")
        }
    }

    // 통계 서비스
    class StatisticsService {
        private var totalOrders = 0
        private var totalRevenue = BigDecimal.ZERO

        fun recordOrder(orderId: OrderId, amount: BigDecimal) {
            totalOrders++
            totalRevenue += amount
            println("  [Statistics] 주문 기록 - 총 주문: $totalOrders, 총 매출: $totalRevenue")
        }

        fun recordCancellation(orderId: OrderId, amount: BigDecimal) {
            totalOrders--
            totalRevenue -= amount
            println("  [Statistics] 취소 기록 - 총 주문: $totalOrders, 총 매출: $totalRevenue")
        }
    }

    // 감사 로그 서비스
    class AuditLogService {
        fun log(action: String, entityType: String, entityId: String, details: String) {
            println("  [Audit] $action - $entityType($entityId): $details")
        }
    }

    // 문제가 있는 주문 서비스 - 모든 부수 효과가 직접 결합
    class OrderService(
        private val orders: MutableMap<OrderId, Order> = mutableMapOf(),
        private val customers: MutableMap<CustomerId, Customer>,
        private val products: MutableMap<ProductId, Product>,
        private val notificationService: NotificationService,
        private val inventoryService: InventoryService,
        private val pointService: PointService,
        private val statisticsService: StatisticsService,
        private val auditLogService: AuditLogService
    ) {
        // 문제: 주문 확정 시 모든 부수 효과가 직접 결합
        fun confirmOrder(orderId: OrderId) {
            val order = orders[orderId]
                ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val customer = customers[order.customerId]
                ?: throw IllegalArgumentException("고객을 찾을 수 없습니다")

            println("주문 확정 처리 시작: ${orderId.value}")

            // 1. 주문 상태 변경 (핵심 로직)
            order.status = "CONFIRMED"
            println("  [Order] 주문 상태 변경: CONFIRMED")

            // 2. 재고 차감 (부수 효과 1)
            order.items.forEach { item ->
                inventoryService.decreaseStock(item.productId, item.quantity)
            }

            // 3. 포인트 적립 (부수 효과 2)
            val pointsToAdd = (order.calculateTotal().toInt() * 0.01).toInt()
            pointService.addPoints(order.customerId, pointsToAdd)

            // 4. 이메일 발송 (부수 효과 3)
            notificationService.sendEmail(
                customer.email,
                "주문 확정",
                "주문이 확정되었습니다. 주문번호: ${orderId.value}"
            )

            // 5. 푸시 알림 (부수 효과 4)
            notificationService.sendPush(
                customer.id.value,
                "주문이 확정되었습니다!"
            )

            // 6. 통계 기록 (부수 효과 5)
            statisticsService.recordOrder(orderId, order.calculateTotal())

            // 7. 감사 로그 (부수 효과 6)
            auditLogService.log(
                "CONFIRM",
                "Order",
                orderId.value,
                "총액: ${order.calculateTotal()}"
            )

            println("주문 확정 처리 완료")
        }

        // 문제: 주문 취소도 마찬가지로 모든 부수 효과가 결합
        fun cancelOrder(orderId: OrderId, reason: String) {
            val order = orders[orderId]
                ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val customer = customers[order.customerId]
                ?: throw IllegalArgumentException("고객을 찾을 수 없습니다")

            println("주문 취소 처리 시작: ${orderId.value}")

            // 1. 주문 상태 변경 (핵심 로직)
            val previousStatus = order.status
            order.status = "CANCELLED"
            println("  [Order] 주문 상태 변경: CANCELLED")

            // 2. 확정된 주문이었다면 재고 복구
            if (previousStatus == "CONFIRMED") {
                order.items.forEach { item ->
                    inventoryService.increaseStock(item.productId, item.quantity)
                }

                // 3. 포인트 차감
                val pointsToDeduct = (order.calculateTotal().toInt() * 0.01).toInt()
                try {
                    pointService.deductPoints(order.customerId, pointsToDeduct)
                } catch (e: Exception) {
                    println("  [Warning] 포인트 차감 실패: ${e.message}")
                }

                // 4. 통계 업데이트
                statisticsService.recordCancellation(orderId, order.calculateTotal())
            }

            // 5. 이메일 발송
            notificationService.sendEmail(
                customer.email,
                "주문 취소",
                "주문이 취소되었습니다. 사유: $reason"
            )

            // 6. 감사 로그
            auditLogService.log(
                "CANCEL",
                "Order",
                orderId.value,
                "사유: $reason"
            )

            println("주문 취소 처리 완료")
        }

        fun createOrder(customerId: CustomerId): Order {
            val order = Order(customerId = customerId)
            orders[order.id] = order
            return order
        }

        fun addItem(orderId: OrderId, productId: ProductId, quantity: Int) {
            val order = orders[orderId] ?: throw IllegalArgumentException("주문을 찾을 수 없습니다")
            val product = products[productId] ?: throw IllegalArgumentException("상품을 찾을 수 없습니다")

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
}

fun main() {
    // 초기 데이터 설정
    val customers = mutableMapOf<Problem.CustomerId, Problem.Customer>()
    val products = mutableMapOf<Problem.ProductId, Problem.Product>()

    val customerId = Problem.CustomerId("CUST001")
    val customer = Problem.Customer(customerId, "홍길동", "hong@example.com", 1000)
    customers[customerId] = customer

    val productId1 = Problem.ProductId("PROD001")
    val product1 = Problem.Product(productId1, "노트북", BigDecimal("1500000"), 10)
    products[productId1] = product1

    val productId2 = Problem.ProductId("PROD002")
    val product2 = Problem.Product(productId2, "마우스", BigDecimal("50000"), 100)
    products[productId2] = product2

    // 서비스 생성
    val notificationService = Problem.NotificationService()
    val inventoryService = Problem.InventoryService(products)
    val pointService = Problem.PointService(customers)
    val statisticsService = Problem.StatisticsService()
    val auditLogService = Problem.AuditLogService()

    val orderService = Problem.OrderService(
        customers = customers,
        products = products,
        notificationService = notificationService,
        inventoryService = inventoryService,
        pointService = pointService,
        statisticsService = statisticsService,
        auditLogService = auditLogService
    )

    // 주문 생성 및 확정
    println("=== 주문 생성 ===")
    val order = orderService.createOrder(customerId)
    orderService.addItem(order.id, productId1, 1)
    orderService.addItem(order.id, productId2, 2)
    println("주문 생성됨: ${order.id.value}")
    println("주문 총액: ${order.calculateTotal()}")
    println()

    println("=== 주문 확정 ===")
    orderService.confirmOrder(order.id)
    println()

    println("=== 주문 취소 ===")
    orderService.cancelOrder(order.id, "고객 요청")
    println()

    println("=== 문제점 요약 ===")
    println("1. OrderService가 모든 서비스에 직접 의존 (강한 결합)")
    println("2. 새 기능 추가 시 OrderService 수정 필요 (OCP 위반)")
    println("3. 트랜잭션 범위가 너무 넓음 (일부 실패 시 전체 롤백)")
    println("4. 부수 효과가 핵심 로직에 섞여 있음")
    println("5. 테스트하기 어려움 (모든 의존성 모킹 필요)")
    println("6. 이벤트 기반 처리 불가 (비동기 처리 어려움)")
    println("7. 이벤트 히스토리 추적 불가")
}
