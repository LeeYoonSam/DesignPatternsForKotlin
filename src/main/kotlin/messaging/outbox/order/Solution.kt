package messaging.outbox.order

/**
 * Outbox Pattern - Solution
 *
 * Outbox Pattern은 DB 저장과 메시지 발행의 원자성을 보장하는 패턴입니다.
 *
 * 핵심 아이디어:
 * 1. 이벤트를 메시지 브로커에 직접 발행하지 않고 DB의 Outbox 테이블에 저장
 * 2. DB 트랜잭션으로 비즈니스 데이터와 Outbox 레코드를 함께 저장 (원자성 보장)
 * 3. 별도의 프로세스(Relay/Poller)가 Outbox 테이블을 읽어 메시지 브로커로 발행
 *
 * 장점:
 * - At-Least-Once 보장 (최소 한 번 발행)
 * - 로컬 트랜잭션만 사용 (2PC 불필요)
 * - 장애 복구 용이 (Outbox 테이블에서 재시도)
 * - 순서 보장 가능
 */

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ========================================
// Domain Model
// ========================================

object Domain {

    @JvmInline
    value class OrderId(val value: String) {
        companion object {
            fun generate() = OrderId(UUID.randomUUID().toString().take(8))
        }
    }

    @JvmInline
    value class CustomerId(val value: String)

    @JvmInline
    value class ProductId(val value: String)

    enum class OrderStatus {
        CREATED, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED
    }

    data class Order(
        val id: OrderId,
        val customerId: CustomerId,
        val productId: ProductId,
        val quantity: Int,
        val totalAmount: Double,
        val status: OrderStatus,
        val createdAt: LocalDateTime
    ) {
        fun confirm() = copy(status = OrderStatus.CONFIRMED)
        fun pay() = copy(status = OrderStatus.PAID)
        fun ship() = copy(status = OrderStatus.SHIPPED)
        fun cancel() = copy(status = OrderStatus.CANCELLED)
    }

    // === Domain Events ===

    sealed class OrderEvent {
        abstract val orderId: String
        abstract val occurredAt: LocalDateTime

        data class OrderCreated(
            override val orderId: String,
            val customerId: String,
            val productId: String,
            val quantity: Int,
            val totalAmount: Double,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : OrderEvent()

        data class OrderConfirmed(
            override val orderId: String,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : OrderEvent()

        data class OrderPaid(
            override val orderId: String,
            val amount: Double,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : OrderEvent()

        data class OrderShipped(
            override val orderId: String,
            val trackingNumber: String,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : OrderEvent()

        data class OrderCancelled(
            override val orderId: String,
            val reason: String,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : OrderEvent()
    }
}

// ========================================
// Outbox Table Model
// ========================================

/**
 * Outbox 테이블 레코드
 *
 * 실제 DB에서는 다음과 같은 테이블로 저장:
 * CREATE TABLE outbox (
 *     id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *     aggregate_type VARCHAR(255) NOT NULL,
 *     aggregate_id VARCHAR(255) NOT NULL,
 *     event_type VARCHAR(255) NOT NULL,
 *     payload TEXT NOT NULL,
 *     created_at TIMESTAMP NOT NULL,
 *     processed_at TIMESTAMP NULL,
 *     status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
 * );
 */
data class OutboxRecord(
    val id: Long,
    val aggregateType: String,      // ex: "Order"
    val aggregateId: String,        // ex: "order-123"
    val eventType: String,          // ex: "OrderCreated"
    val payload: String,            // JSON 직렬화된 이벤트
    val createdAt: LocalDateTime,
    var processedAt: LocalDateTime? = null,
    var status: OutboxStatus = OutboxStatus.PENDING
)

enum class OutboxStatus {
    PENDING,    // 발행 대기 중
    PROCESSING, // 발행 처리 중
    PUBLISHED,  // 발행 완료
    FAILED      // 발행 실패 (재시도 예정)
}

// ========================================
// Repository Layer
// ========================================

/**
 * 주문 저장소
 */
class OrderRepository {
    private val orders = ConcurrentHashMap<String, Domain.Order>()

    fun save(order: Domain.Order) {
        orders[order.id.value] = order
        println("  [OrderRepo] 주문 저장: ${order.id.value}")
    }

    fun findById(id: Domain.OrderId): Domain.Order? = orders[id.value]

    fun findAll(): List<Domain.Order> = orders.values.toList()
}

/**
 * Outbox 저장소
 */
class OutboxRepository {
    private val outbox = ConcurrentHashMap<Long, OutboxRecord>()
    private val idGenerator = AtomicLong(1)

    fun save(record: OutboxRecord): OutboxRecord {
        val savedRecord = if (record.id == 0L) {
            record.copy(id = idGenerator.getAndIncrement())
        } else {
            record
        }
        outbox[savedRecord.id] = savedRecord
        println("  [OutboxRepo] Outbox 저장: ID=${savedRecord.id}, Type=${savedRecord.eventType}")
        return savedRecord
    }

    fun findPendingRecords(limit: Int = 100): List<OutboxRecord> {
        return outbox.values
            .filter { it.status == OutboxStatus.PENDING }
            .sortedBy { it.createdAt }
            .take(limit)
    }

    fun findFailedRecords(limit: Int = 100): List<OutboxRecord> {
        return outbox.values
            .filter { it.status == OutboxStatus.FAILED }
            .sortedBy { it.createdAt }
            .take(limit)
    }

    fun update(record: OutboxRecord) {
        outbox[record.id] = record
    }

    fun markAsProcessing(id: Long) {
        outbox[id]?.let { it.status = OutboxStatus.PROCESSING }
    }

    fun markAsPublished(id: Long) {
        outbox[id]?.let {
            it.status = OutboxStatus.PUBLISHED
            it.processedAt = LocalDateTime.now()
        }
    }

    fun markAsFailed(id: Long) {
        outbox[id]?.let { it.status = OutboxStatus.FAILED }
    }

    fun getStats(): Map<OutboxStatus, Int> {
        return outbox.values.groupingBy { it.status }.eachCount()
    }
}

// ========================================
// Unit of Work (트랜잭션 시뮬레이션)
// ========================================

/**
 * Unit of Work: 하나의 트랜잭션 내에서 여러 작업을 수행
 *
 * 실제 구현에서는 @Transactional 어노테이션이나
 * DB 트랜잭션 매니저를 사용
 */
class UnitOfWork(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository
) {
    private val pendingOrders = mutableListOf<Domain.Order>()
    private val pendingOutboxRecords = mutableListOf<OutboxRecord>()
    private var committed = false

    fun registerOrder(order: Domain.Order) {
        pendingOrders.add(order)
    }

    fun registerOutboxRecord(record: OutboxRecord) {
        pendingOutboxRecords.add(record)
    }

    /**
     * 트랜잭션 커밋: 주문과 Outbox 레코드를 함께 저장
     * 실제로는 DB 트랜잭션으로 원자성 보장
     */
    fun commit() {
        if (committed) throw IllegalStateException("이미 커밋됨")

        println("  [UnitOfWork] 트랜잭션 시작")
        try {
            // 하나의 트랜잭션 내에서 모두 저장
            pendingOrders.forEach { orderRepository.save(it) }
            pendingOutboxRecords.forEach { outboxRepository.save(it) }

            committed = true
            println("  [UnitOfWork] 트랜잭션 커밋 완료")
        } catch (e: Exception) {
            println("  [UnitOfWork] 트랜잭션 롤백: ${e.message}")
            throw e
        }
    }

    fun rollback() {
        pendingOrders.clear()
        pendingOutboxRecords.clear()
        println("  [UnitOfWork] 트랜잭션 롤백")
    }
}

// ========================================
// Order Service (Outbox Pattern 적용)
// ========================================

class OrderService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository
) {
    private val json = Json { prettyPrint = false }

    /**
     * 주문 생성 - Outbox Pattern 적용
     *
     * 1. 주문 생성
     * 2. OrderCreated 이벤트를 Outbox 테이블에 저장
     * 3. 둘 다 하나의 트랜잭션으로 커밋
     *
     * → 메시지 브로커 장애와 무관하게 항상 성공 또는 실패
     */
    fun createOrder(
        customerId: String,
        productId: String,
        quantity: Int,
        unitPrice: Double
    ): Domain.Order {
        println("[OrderService] 주문 생성 시작")

        val unitOfWork = UnitOfWork(orderRepository, outboxRepository)

        try {
            // 1. 주문 엔티티 생성
            val order = Domain.Order(
                id = Domain.OrderId.generate(),
                customerId = Domain.CustomerId(customerId),
                productId = Domain.ProductId(productId),
                quantity = quantity,
                totalAmount = quantity * unitPrice,
                status = Domain.OrderStatus.CREATED,
                createdAt = LocalDateTime.now()
            )

            // 2. 도메인 이벤트 생성
            val event = Domain.OrderEvent.OrderCreated(
                orderId = order.id.value,
                customerId = customerId,
                productId = productId,
                quantity = quantity,
                totalAmount = order.totalAmount
            )

            // 3. Outbox 레코드 생성
            val outboxRecord = OutboxRecord(
                id = 0,
                aggregateType = "Order",
                aggregateId = order.id.value,
                eventType = "OrderCreated",
                payload = serializeEvent(event),
                createdAt = LocalDateTime.now()
            )

            // 4. Unit of Work에 등록
            unitOfWork.registerOrder(order)
            unitOfWork.registerOutboxRecord(outboxRecord)

            // 5. 트랜잭션 커밋 (원자적으로 둘 다 저장)
            unitOfWork.commit()

            println("[OrderService] 주문 생성 완료: ${order.id.value}")
            return order

        } catch (e: Exception) {
            unitOfWork.rollback()
            throw e
        }
    }

    /**
     * 주문 확정
     */
    fun confirmOrder(orderId: String): Domain.Order {
        println("[OrderService] 주문 확정: $orderId")

        val unitOfWork = UnitOfWork(orderRepository, outboxRepository)

        val order = orderRepository.findById(Domain.OrderId(orderId))
            ?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")

        val confirmedOrder = order.confirm()

        val event = Domain.OrderEvent.OrderConfirmed(
            orderId = orderId
        )

        val outboxRecord = OutboxRecord(
            id = 0,
            aggregateType = "Order",
            aggregateId = orderId,
            eventType = "OrderConfirmed",
            payload = serializeEvent(event),
            createdAt = LocalDateTime.now()
        )

        unitOfWork.registerOrder(confirmedOrder)
        unitOfWork.registerOutboxRecord(outboxRecord)
        unitOfWork.commit()

        return confirmedOrder
    }

    /**
     * 주문 취소
     */
    fun cancelOrder(orderId: String, reason: String): Domain.Order {
        println("[OrderService] 주문 취소: $orderId, 사유: $reason")

        val unitOfWork = UnitOfWork(orderRepository, outboxRepository)

        val order = orderRepository.findById(Domain.OrderId(orderId))
            ?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")

        val cancelledOrder = order.cancel()

        val event = Domain.OrderEvent.OrderCancelled(
            orderId = orderId,
            reason = reason
        )

        val outboxRecord = OutboxRecord(
            id = 0,
            aggregateType = "Order",
            aggregateId = orderId,
            eventType = "OrderCancelled",
            payload = serializeEvent(event),
            createdAt = LocalDateTime.now()
        )

        unitOfWork.registerOrder(cancelledOrder)
        unitOfWork.registerOutboxRecord(outboxRecord)
        unitOfWork.commit()

        return cancelledOrder
    }

    private fun serializeEvent(event: Domain.OrderEvent): String {
        return when (event) {
            is Domain.OrderEvent.OrderCreated ->
                """{"orderId":"${event.orderId}","customerId":"${event.customerId}","productId":"${event.productId}","quantity":${event.quantity},"totalAmount":${event.totalAmount},"occurredAt":"${event.occurredAt}"}"""
            is Domain.OrderEvent.OrderConfirmed ->
                """{"orderId":"${event.orderId}","occurredAt":"${event.occurredAt}"}"""
            is Domain.OrderEvent.OrderPaid ->
                """{"orderId":"${event.orderId}","amount":${event.amount},"occurredAt":"${event.occurredAt}"}"""
            is Domain.OrderEvent.OrderShipped ->
                """{"orderId":"${event.orderId}","trackingNumber":"${event.trackingNumber}","occurredAt":"${event.occurredAt}"}"""
            is Domain.OrderEvent.OrderCancelled ->
                """{"orderId":"${event.orderId}","reason":"${event.reason}","occurredAt":"${event.occurredAt}"}"""
        }
    }
}

// ========================================
// Message Broker (시뮬레이션)
// ========================================

interface MessageBroker {
    fun publish(topic: String, message: String): Boolean
}

class KafkaMessageBroker : MessageBroker {
    var isHealthy = true
    private val publishedMessages = mutableListOf<Pair<String, String>>()

    override fun publish(topic: String, message: String): Boolean {
        if (!isHealthy) {
            println("  [Kafka] ❌ 발행 실패 - 브로커 장애")
            return false
        }

        publishedMessages.add(topic to message)
        println("  [Kafka] ✅ 메시지 발행: $topic")
        return true
    }

    fun getPublishedMessages(): List<Pair<String, String>> = publishedMessages.toList()
}

// ========================================
// Outbox Relay (Message Relay / Polling Publisher)
// ========================================

/**
 * Outbox Relay: Outbox 테이블을 폴링하여 메시지 브로커로 발행
 *
 * 두 가지 구현 방식:
 * 1. Polling Publisher: 주기적으로 Outbox 테이블 조회
 * 2. Transaction Log Tailing (CDC): DB 변경 로그 구독 (Debezium 등)
 *
 * 이 예제는 Polling Publisher 방식 구현
 */
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val messageBroker: MessageBroker
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var running = false

    /**
     * Relay 시작 - 주기적으로 Outbox 폴링
     */
    fun start(intervalMs: Long = 1000) {
        running = true
        println("[OutboxRelay] 시작 (폴링 간격: ${intervalMs}ms)")

        executor.scheduleAtFixedRate({
            if (running) {
                try {
                    pollAndPublish()
                } catch (e: Exception) {
                    println("[OutboxRelay] 에러: ${e.message}")
                }
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Outbox 테이블에서 PENDING 레코드를 조회하여 발행
     */
    private fun pollAndPublish() {
        val pendingRecords = outboxRepository.findPendingRecords(10)

        if (pendingRecords.isEmpty()) return

        println("[OutboxRelay] ${pendingRecords.size}개의 대기 중인 메시지 발견")

        pendingRecords.forEach { record ->
            processRecord(record)
        }
    }

    /**
     * 개별 레코드 처리
     */
    private fun processRecord(record: OutboxRecord) {
        try {
            // 처리 중 상태로 변경 (동시 처리 방지)
            outboxRepository.markAsProcessing(record.id)

            // 토픽 결정
            val topic = determineTopicName(record)

            // 메시지 브로커로 발행
            val success = messageBroker.publish(topic, record.payload)

            if (success) {
                // 발행 성공 → PUBLISHED로 마킹
                outboxRepository.markAsPublished(record.id)
                println("[OutboxRelay] ✅ 발행 성공: ID=${record.id}")
            } else {
                // 발행 실패 → FAILED로 마킹 (나중에 재시도)
                outboxRepository.markAsFailed(record.id)
                println("[OutboxRelay] ⚠️ 발행 실패: ID=${record.id}")
            }
        } catch (e: Exception) {
            outboxRepository.markAsFailed(record.id)
            println("[OutboxRelay] ❌ 에러: ${e.message}")
        }
    }

    /**
     * 이벤트 타입에 따른 토픽 결정
     */
    private fun determineTopicName(record: OutboxRecord): String {
        return "${record.aggregateType.lowercase()}.${record.eventType.lowercase()}"
    }

    /**
     * 실패한 레코드 재시도
     */
    fun retryFailedRecords() {
        println("[OutboxRelay] 실패한 레코드 재시도 시작")
        val failedRecords = outboxRepository.findFailedRecords()

        failedRecords.forEach { record ->
            // PENDING으로 되돌려 다시 처리되도록
            record.status = OutboxStatus.PENDING
            outboxRepository.update(record)
        }

        println("[OutboxRelay] ${failedRecords.size}개 레코드 재시도 예약됨")
    }

    fun stop() {
        running = false
        executor.shutdown()
        println("[OutboxRelay] 중지")
    }

    fun printStats() {
        val stats = outboxRepository.getStats()
        println("[OutboxRelay] 통계: $stats")
    }
}

// ========================================
// CDC (Change Data Capture) 방식 - 개념 설명
// ========================================

/**
 * CDC 기반 Outbox Relay (Debezium 사용 시)
 *
 * Polling 대신 DB의 트랜잭션 로그를 구독하여 실시간으로 이벤트 발행
 *
 * 장점:
 * - 실시간에 가까운 지연 시간
 * - DB 부하 감소 (폴링 쿼리 없음)
 * - 순서 보장이 더 확실
 *
 * 단점:
 * - 추가 인프라 필요 (Debezium, Kafka Connect 등)
 * - 설정 복잡도 증가
 */
object CDCOutboxRelayConcept {
    fun explain() {
        println("""
            === CDC 기반 Outbox Relay ===

            구성 요소:
            1. Database (MySQL/PostgreSQL)
            2. Debezium Connector
            3. Kafka Connect
            4. Kafka

            동작 방식:
            1. 애플리케이션이 Outbox 테이블에 INSERT
            2. Debezium이 DB의 binlog/WAL을 읽음
            3. 변경 이벤트를 Kafka 토픽으로 발행
            4. 컨슈머가 해당 토픽을 구독하여 처리

            Debezium 설정 예시:
            ```json
            {
              "name": "outbox-connector",
              "config": {
                "connector.class": "io.debezium.connector.mysql.MySqlConnector",
                "database.hostname": "localhost",
                "database.port": "3306",
                "database.user": "debezium",
                "database.password": "password",
                "database.server.id": "1",
                "table.include.list": "orders.outbox",
                "transforms": "outbox",
                "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter"
              }
            }
            ```
        """.trimIndent())
    }
}

// ========================================
// Consumer (다른 마이크로서비스)
// ========================================

/**
 * 이벤트 컨슈머 예시: 재고 서비스
 */
class InventoryServiceConsumer {
    fun handleOrderCreated(payload: String) {
        println("[InventoryService] 주문 생성 이벤트 수신: $payload")
        println("[InventoryService] → 재고 차감 처리")
    }

    fun handleOrderCancelled(payload: String) {
        println("[InventoryService] 주문 취소 이벤트 수신: $payload")
        println("[InventoryService] → 재고 복구 처리")
    }
}

/**
 * 이벤트 컨슈머 예시: 알림 서비스
 */
class NotificationServiceConsumer {
    fun handleOrderCreated(payload: String) {
        println("[NotificationService] 주문 생성 이벤트 수신")
        println("[NotificationService] → 고객에게 주문 확인 알림 발송")
    }

    fun handleOrderConfirmed(payload: String) {
        println("[NotificationService] 주문 확정 이벤트 수신")
        println("[NotificationService] → 결제 안내 알림 발송")
    }
}

// ========================================
// Idempotency (멱등성) 처리
// ========================================

/**
 * 이벤트 중복 처리 방지를 위한 멱등성 키 저장소
 *
 * At-Least-Once 특성 때문에 동일 이벤트가 여러 번 발행될 수 있음
 * 컨슈머 측에서 멱등성을 보장해야 함
 */
class IdempotencyStore {
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * 이벤트가 이미 처리되었는지 확인
     */
    fun hasProcessed(eventId: String): Boolean {
        return processedEventIds.contains(eventId)
    }

    /**
     * 이벤트 처리 완료 마킹
     */
    fun markAsProcessed(eventId: String) {
        processedEventIds.add(eventId)
    }

    /**
     * 멱등성 보장 래퍼
     */
    fun <T> executeIdempotently(eventId: String, action: () -> T): T? {
        if (hasProcessed(eventId)) {
            println("[Idempotency] 이미 처리된 이벤트, 스킵: $eventId")
            return null
        }

        val result = action()
        markAsProcessed(eventId)
        return result
    }
}

// ========================================
// Main - 전체 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║              Outbox Pattern - 주문 시스템 데모                 ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    // === 인프라 설정 ===
    val orderRepository = OrderRepository()
    val outboxRepository = OutboxRepository()
    val messageBroker = KafkaMessageBroker()

    val orderService = OrderService(orderRepository, outboxRepository)
    val outboxRelay = OutboxRelay(outboxRepository, messageBroker)

    // === 시나리오 1: 정상 주문 생성 ===
    println("=== 시나리오 1: 정상 주문 생성 ===")
    val order1 = orderService.createOrder(
        customerId = "cust-001",
        productId = "prod-001",
        quantity = 2,
        unitPrice = 50000.0
    )
    println()

    // === 시나리오 2: 추가 주문들 ===
    println("=== 시나리오 2: 추가 주문 생성 ===")
    val order2 = orderService.createOrder("cust-002", "prod-002", 1, 30000.0)
    val order3 = orderService.createOrder("cust-003", "prod-003", 3, 15000.0)
    println()

    // === 시나리오 3: 주문 확정 ===
    println("=== 시나리오 3: 주문 확정 ===")
    orderService.confirmOrder(order1.id.value)
    println()

    // === Outbox 상태 확인 ===
    println("=== Outbox 상태 ===")
    outboxRelay.printStats()
    println()

    // === 시나리오 4: Outbox Relay 실행 (메시지 발행) ===
    println("=== 시나리오 4: Outbox Relay 실행 ===")
    outboxRelay.start(500)

    // 메시지 발행을 위해 잠시 대기
    Thread.sleep(2000)
    outboxRelay.stop()
    println()

    // === 발행된 메시지 확인 ===
    println("=== 발행된 메시지 ===")
    messageBroker.getPublishedMessages().forEachIndexed { index, (topic, _) ->
        println("  ${index + 1}. Topic: $topic")
    }
    println()

    // === 시나리오 5: 메시지 브로커 장애 시뮬레이션 ===
    println("=== 시나리오 5: 메시지 브로커 장애 ===")
    messageBroker.isHealthy = false

    // 장애 중에 주문 생성 (Outbox에는 저장됨)
    val order4 = orderService.createOrder("cust-004", "prod-004", 1, 25000.0)
    println("  → 주문은 DB에 저장됨, 메시지는 Outbox에 대기")
    println()

    // Relay 실행 시도 (실패)
    val tempRelay = OutboxRelay(outboxRepository, messageBroker)
    tempRelay.start(500)
    Thread.sleep(1000)
    tempRelay.stop()
    println()

    // === 시나리오 6: 브로커 복구 후 재시도 ===
    println("=== 시나리오 6: 브로커 복구 후 재시도 ===")
    messageBroker.isHealthy = true

    tempRelay.retryFailedRecords()
    val recoveryRelay = OutboxRelay(outboxRepository, messageBroker)
    recoveryRelay.start(500)
    Thread.sleep(1500)
    recoveryRelay.stop()
    println()

    // === 최종 통계 ===
    println("=== 최종 Outbox 통계 ===")
    recoveryRelay.printStats()
    println()

    // === CDC 방식 설명 ===
    println("=== CDC 방식 (Debezium) 설명 ===")
    CDCOutboxRelayConcept.explain()
    println()

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                    Outbox Pattern 장점                       ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 원자성 보장: DB 트랜잭션으로 비즈니스 데이터+이벤트 저장    ║")
    println("║ 2. At-Least-Once: 메시지 브로커 장애 시에도 재시도 가능       ║")
    println("║ 3. 순서 보장: Outbox 테이블의 순서대로 발행                   ║")
    println("║ 4. 장애 복구: 실패한 이벤트 추적 및 재시도 용이               ║")
    println("║ 5. 느슨한 결합: 메시지 브로커 장애가 비즈니스 로직에 영향 없음 ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
