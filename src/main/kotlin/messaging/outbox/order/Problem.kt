package messaging.outbox.order

/**
 * Outbox Pattern - Problem
 *
 * 이 파일은 Outbox Pattern을 적용하지 않았을 때 발생하는
 * 분산 시스템의 데이터 일관성 문제를 보여줍니다.
 *
 * 문제점:
 * 1. DB 저장과 메시지 발행이 원자적이지 않음
 * 2. DB 저장 성공 후 메시지 발행 실패 시 불일치 발생
 * 3. 메시지 브로커 장애 시 전체 트랜잭션 실패
 * 4. 이중 발행(Duplicate) 또는 누락(Missing) 문제
 * 5. 분산 트랜잭션(2PC)의 복잡성과 성능 문제
 */

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ========================================
// 문제 시나리오: 주문 생성 후 이벤트 발행
// ========================================

/**
 * 문제 1: DB 저장과 메시지 발행의 원자성 미보장
 *
 * DB 트랜잭션과 메시지 브로커는 별개의 시스템이므로
 * 둘 다 성공하거나 둘 다 실패하도록 보장할 수 없음
 */
class ProblematicOrderService(
    private val orderRepository: SimpleOrderRepository,
    private val messagePublisher: UnreliableMessagePublisher
) {
    /**
     * 문제가 있는 주문 생성 로직
     *
     * Case 1: DB 저장 성공 → 메시지 발행 실패
     *         → 주문은 생성되었지만 다른 서비스는 모름 (데이터 불일치)
     *
     * Case 2: DB 저장 성공 → 메시지 발행 중 애플리케이션 크래시
     *         → 주문 존재하지만 이벤트 유실
     *
     * Case 3: 메시지 발행 성공 → DB 저장 실패
     *         → 존재하지 않는 주문에 대한 이벤트가 발행됨
     */
    fun createOrder(customerId: String, productId: String, quantity: Int): OrderResult {
        val orderId = UUID.randomUUID().toString()
        val order = Order(
            id = orderId,
            customerId = customerId,
            productId = productId,
            quantity = quantity,
            status = OrderStatus.CREATED,
            createdAt = LocalDateTime.now()
        )

        // Step 1: DB에 주문 저장
        try {
            orderRepository.save(order)
            println("[Order Service] 주문 저장 완료: $orderId")
        } catch (e: Exception) {
            println("[Order Service] ❌ 주문 저장 실패: ${e.message}")
            return OrderResult.Failure("주문 저장 실패: ${e.message}")
        }

        // Step 2: 메시지 발행 (여기서 문제 발생 가능!)
        try {
            val event = OrderCreatedEvent(
                orderId = orderId,
                customerId = customerId,
                productId = productId,
                quantity = quantity,
                timestamp = LocalDateTime.now()
            )
            messagePublisher.publish("order.created", event)
            println("[Order Service] 이벤트 발행 완료: $orderId")
        } catch (e: Exception) {
            // 주문은 이미 DB에 저장됨 → 데이터 불일치!
            println("[Order Service] ❌ 이벤트 발행 실패: ${e.message}")
            println("[Order Service] ⚠️ 경고: 주문은 저장되었지만 이벤트가 발행되지 않음!")
            // 어떻게 해야 하나?
            // - 주문 롤백? → 이미 커밋됨
            // - 재시도? → 중복 발행 가능성
            // - 무시? → 데이터 불일치 지속
            return OrderResult.PartialSuccess(orderId, "주문 저장됨, 이벤트 발행 실패")
        }

        return OrderResult.Success(orderId)
    }
}

/**
 * 문제 2: 분산 트랜잭션(2PC) 시도
 *
 * Two-Phase Commit은 이론적으로 원자성을 보장하지만:
 * - 구현이 매우 복잡
 * - 성능 저하 (락 대기)
 * - 메시지 브로커가 2PC를 지원하지 않을 수 있음
 * - 코디네이터 장애 시 전체 시스템 블록
 */
class TwoPhaseCommitOrderService {
    fun createOrderWith2PC(customerId: String, productId: String): String {
        println("=== 2PC 시도 ===")
        println("Phase 1 (Prepare):")
        println("  - DB: PREPARE 요청")
        println("  - Message Broker: PREPARE 요청")
        println("  → 둘 다 OK면 Phase 2로")
        println()
        println("Phase 2 (Commit):")
        println("  - DB: COMMIT")
        println("  - Message Broker: COMMIT")
        println()
        println("문제점:")
        println("  - Kafka 등 대부분의 메시지 브로커는 2PC 미지원")
        println("  - 락 대기로 인한 성능 저하")
        println("  - 코디네이터 장애 시 전체 시스템 블록")
        println("  - 구현 복잡도 매우 높음")

        return "2PC는 현실적으로 어려움"
    }
}

/**
 * 문제 3: 메시지 먼저 발행하고 DB 저장하는 방식
 *
 * 순서를 바꿔도 문제는 동일:
 * - 메시지 발행 성공 → DB 저장 실패 → 유령 이벤트
 */
class MessageFirstOrderService(
    private val orderRepository: SimpleOrderRepository,
    private val messagePublisher: UnreliableMessagePublisher
) {
    fun createOrder(customerId: String, productId: String): OrderResult {
        val orderId = UUID.randomUUID().toString()

        // Step 1: 메시지 먼저 발행
        try {
            val event = OrderCreatedEvent(
                orderId = orderId,
                customerId = customerId,
                productId = productId,
                quantity = 1,
                timestamp = LocalDateTime.now()
            )
            messagePublisher.publish("order.created", event)
            println("[Order Service] 이벤트 발행 완료: $orderId")
        } catch (e: Exception) {
            return OrderResult.Failure("이벤트 발행 실패")
        }

        // Step 2: DB 저장
        try {
            val order = Order(
                id = orderId,
                customerId = customerId,
                productId = productId,
                quantity = 1,
                status = OrderStatus.CREATED,
                createdAt = LocalDateTime.now()
            )
            orderRepository.save(order)
            println("[Order Service] 주문 저장 완료: $orderId")
        } catch (e: Exception) {
            // 이벤트는 이미 발행됨 → 존재하지 않는 주문에 대한 이벤트!
            println("[Order Service] ❌ 주문 저장 실패!")
            println("[Order Service] ⚠️ 경고: 존재하지 않는 주문에 대한 이벤트가 발행됨!")
            return OrderResult.Failure("DB 저장 실패, 유령 이벤트 발생")
        }

        return OrderResult.Success(orderId)
    }
}

// ========================================
// 지원 클래스들
// ========================================

data class Order(
    val id: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val status: OrderStatus,
    val createdAt: LocalDateTime
)

enum class OrderStatus { CREATED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

data class OrderCreatedEvent(
    val orderId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val timestamp: LocalDateTime
)

sealed class OrderResult {
    data class Success(val orderId: String) : OrderResult()
    data class PartialSuccess(val orderId: String, val warning: String) : OrderResult()
    data class Failure(val reason: String) : OrderResult()
}

class SimpleOrderRepository {
    private val orders = ConcurrentHashMap<String, Order>()
    var shouldFail = false

    fun save(order: Order) {
        if (shouldFail) throw RuntimeException("DB 연결 실패")
        orders[order.id] = order
    }

    fun findById(id: String): Order? = orders[id]
}

/**
 * 불안정한 메시지 퍼블리셔 (장애 시뮬레이션)
 */
class UnreliableMessagePublisher {
    var shouldFail = false
    var failureRate = 0.0 // 0.0 ~ 1.0

    fun publish(topic: String, event: Any) {
        if (shouldFail || Math.random() < failureRate) {
            throw RuntimeException("메시지 브로커 연결 실패")
        }
        println("[Message Broker] 메시지 발행: $topic -> $event")
    }
}

/**
 * 문제점 요약:
 *
 * 1. 두 개의 분리된 시스템 (DB, Message Broker)
 *    - 하나의 트랜잭션으로 묶을 수 없음
 *    - 부분 실패 시 불일치 발생
 *
 * 2. At-Least-Once vs At-Most-Once 딜레마
 *    - 재시도하면 중복 발행 가능
 *    - 재시도 안하면 메시지 유실 가능
 *
 * 3. 순서 보장 어려움
 *    - 여러 이벤트가 순서대로 발행되어야 하는 경우
 *    - 일부만 성공하면 순서 꼬임
 *
 * 4. 장애 복구의 어려움
 *    - 어떤 이벤트가 발행되었고 안 되었는지 추적 어려움
 *    - 재시작 시 어디서부터 다시 발행해야 하는지 모름
 *
 * Outbox Pattern으로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 구현을 확인하세요.
 */

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║           Outbox Pattern 적용 전 문제점 데모                   ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val orderRepository = SimpleOrderRepository()
    val messagePublisher = UnreliableMessagePublisher()
    val orderService = ProblematicOrderService(orderRepository, messagePublisher)

    println("=== 시나리오 1: 정상 케이스 ===")
    val result1 = orderService.createOrder("customer-1", "product-1", 2)
    println("결과: $result1")
    println()

    println("=== 시나리오 2: 메시지 브로커 장애 ===")
    messagePublisher.shouldFail = true
    val result2 = orderService.createOrder("customer-2", "product-2", 1)
    println("결과: $result2")
    println()

    messagePublisher.shouldFail = false

    println("=== 시나리오 3: DB 장애 ===")
    orderRepository.shouldFail = true
    val result3 = orderService.createOrder("customer-3", "product-3", 3)
    println("결과: $result3")
    println()

    println("=== 2PC 시도 ===")
    val twoPC = TwoPhaseCommitOrderService()
    twoPC.createOrderWith2PC("customer-4", "product-4")
    println()

    println("Solution.kt에서 Outbox Pattern을 적용한 해결책을 확인하세요.")
}
