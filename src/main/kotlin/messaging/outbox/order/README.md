# Outbox Pattern

## 개요

Outbox Pattern은 마이크로서비스에서 **DB 저장과 메시지 발행의 원자성**을 보장하는 패턴입니다. 분산 시스템에서 데이터 일관성 문제를 해결하는 핵심 패턴 중 하나입니다.

## 문제 상황

마이크로서비스에서 비즈니스 로직 수행 후 다른 서비스에 이벤트를 발행할 때:

```kotlin
fun createOrder(request: CreateOrderRequest): Order {
    // Step 1: DB에 주문 저장
    val order = orderRepository.save(newOrder)  // ✅ 성공

    // Step 2: 메시지 브로커에 이벤트 발행
    messageBroker.publish(OrderCreatedEvent(order))  // ❌ 실패 가능!

    return order
}
```

**문제점:**
- DB 저장 성공 → 메시지 발행 실패 → **데이터 불일치**
- 메시지 발행 성공 → DB 저장 실패 → **유령 이벤트**
- 2PC(Two-Phase Commit)는 성능 저하 및 브로커 미지원 문제

## 해결 방법

이벤트를 메시지 브로커에 직접 발행하지 않고, **DB의 Outbox 테이블에 저장**합니다.

```
┌────────────────────────────────────────────────────────────────┐
│                      Order Service                              │
│                                                                 │
│  ┌─────────────┐                                               │
│  │  Business   │                                               │
│  │   Logic     │                                               │
│  └──────┬──────┘                                               │
│         │                                                       │
│         ▼                                                       │
│  ┌─────────────────────────────────────────┐                   │
│  │           DB Transaction                 │                   │
│  │  ┌───────────────┐  ┌─────────────────┐ │                   │
│  │  │ Orders Table  │  │  Outbox Table   │ │                   │
│  │  │   (INSERT)    │  │    (INSERT)     │ │                   │
│  │  └───────────────┘  └─────────────────┘ │                   │
│  │           ↑                   ↑         │                   │
│  │           └───── 같은 트랜잭션 ─────┘         │                   │
│  └─────────────────────────────────────────┘                   │
│                                                                 │
│  ┌─────────────┐      ┌─────────────────┐                      │
│  │   Outbox    │ ───► │  Message Broker │                      │
│  │   Relay     │      │    (Kafka)      │                      │
│  └─────────────┘      └─────────────────┘                      │
└────────────────────────────────────────────────────────────────┘
```

## Outbox 테이블 구조

```sql
CREATE TABLE outbox (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type  VARCHAR(255) NOT NULL,  -- 'Order'
    aggregate_id    VARCHAR(255) NOT NULL,  -- 'order-123'
    event_type      VARCHAR(255) NOT NULL,  -- 'OrderCreated'
    payload         TEXT NOT NULL,          -- JSON 직렬화된 이벤트
    created_at      TIMESTAMP NOT NULL,
    processed_at    TIMESTAMP NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status ON outbox(status, created_at);
```

## 핵심 구현

### 1. Order Service (이벤트를 Outbox에 저장)

```kotlin
class OrderService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OutboxRepository
) {
    @Transactional  // 하나의 트랜잭션
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. 주문 저장
        val order = orderRepository.save(Order(...))

        // 2. 이벤트를 Outbox 테이블에 저장 (같은 트랜잭션)
        val outboxRecord = OutboxRecord(
            aggregateType = "Order",
            aggregateId = order.id,
            eventType = "OrderCreated",
            payload = Json.encodeToString(OrderCreatedEvent(order)),
            createdAt = LocalDateTime.now()
        )
        outboxRepository.save(outboxRecord)

        return order
    }
}
```

### 2. Outbox Relay (폴링 방식)

```kotlin
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val messageBroker: MessageBroker
) {
    @Scheduled(fixedRate = 1000)  // 1초마다 폴링
    fun pollAndPublish() {
        // PENDING 상태의 레코드 조회
        val records = outboxRepository.findByStatus(OutboxStatus.PENDING, limit = 100)

        records.forEach { record ->
            try {
                // 메시지 브로커로 발행
                messageBroker.publish(
                    topic = "${record.aggregateType}.${record.eventType}",
                    message = record.payload
                )
                // 발행 성공 → PUBLISHED로 마킹
                outboxRepository.markAsPublished(record.id)
            } catch (e: Exception) {
                // 발행 실패 → FAILED로 마킹 (나중에 재시도)
                outboxRepository.markAsFailed(record.id)
            }
        }
    }
}
```

### 3. CDC 방식 (Debezium)

```
┌──────────┐     ┌───────────┐     ┌──────────┐     ┌─────────┐
│ Database │ ──► │ Debezium  │ ──► │  Kafka   │ ──► │Consumer │
│ (binlog) │     │ Connector │     │ Connect  │     │         │
└──────────┘     └───────────┘     └──────────┘     └─────────┘
```

폴링 대신 DB의 트랜잭션 로그(binlog/WAL)를 구독하여 실시간 발행:

```json
{
  "name": "outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "table.include.list": "orders.outbox",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter"
  }
}
```

## 폴링 vs CDC 비교

| 특성 | Polling Publisher | CDC (Debezium) |
|------|-------------------|----------------|
| 지연 시간 | 폴링 간격에 따라 다름 | 실시간에 가까움 |
| DB 부하 | 폴링 쿼리 실행 | 없음 (로그 읽기) |
| 복잡도 | 낮음 | 높음 (인프라 추가) |
| 순서 보장 | 타임스탬프 기반 | 로그 순서 기반 (더 정확) |
| 확장성 | 제한적 | 높음 |

## 멱등성 (Idempotency) 처리

At-Least-Once 특성으로 중복 발행이 가능하므로 컨슈머 측에서 멱등성을 보장해야 합니다:

```kotlin
class InventoryServiceConsumer(
    private val idempotencyStore: IdempotencyStore
) {
    fun handleOrderCreated(eventId: String, payload: OrderCreatedEvent) {
        // 이미 처리된 이벤트인지 확인
        if (idempotencyStore.hasProcessed(eventId)) {
            return  // 중복 이벤트 무시
        }

        // 비즈니스 로직 처리
        inventoryService.reserveStock(payload.productId, payload.quantity)

        // 처리 완료 마킹
        idempotencyStore.markAsProcessed(eventId)
    }
}
```

## 재시도 전략

```kotlin
class OutboxRelay {
    @Scheduled(fixedRate = 60000)  // 1분마다
    fun retryFailedRecords() {
        val failedRecords = outboxRepository.findByStatus(
            status = OutboxStatus.FAILED,
            createdBefore = LocalDateTime.now().minusMinutes(5)  // 5분 이상 된 것만
        )

        failedRecords.forEach { record ->
            if (record.retryCount < MAX_RETRIES) {
                record.status = OutboxStatus.PENDING
                record.retryCount++
                outboxRepository.save(record)
            } else {
                // Dead Letter Queue로 이동
                deadLetterService.send(record)
            }
        }
    }
}
```

## 장점

1. **원자성 보장**: DB 트랜잭션으로 비즈니스 데이터와 이벤트를 함께 저장
2. **At-Least-Once 보장**: 메시지 브로커 장애 시에도 재시도 가능
3. **순서 보장**: Outbox 테이블의 생성 순서대로 발행
4. **장애 복구 용이**: 실패한 이벤트 추적 및 재시도
5. **느슨한 결합**: 메시지 브로커 장애가 비즈니스 로직에 영향 없음

## 단점

1. **추가 DB 부하**: Outbox 테이블 INSERT + Relay 조회
2. **지연 시간**: 폴링 방식은 폴링 간격만큼 지연
3. **복잡성 증가**: Relay 프로세스 관리 필요
4. **정리 필요**: 발행 완료된 레코드 주기적 삭제

## 적용 시점

- 마이크로서비스 간 이벤트 기반 통신이 필요할 때
- DB 저장과 메시지 발행의 원자성이 중요할 때
- 메시지 유실이 비즈니스에 큰 영향을 주는 경우
- 이벤트 소싱 없이 이벤트 기반 아키텍처를 구현할 때

## 관련 패턴

- **Saga Pattern**: 분산 트랜잭션 관리에 Outbox 패턴 활용
- **Event Sourcing**: 이벤트 저장소가 Outbox 역할 수행
- **Dead Letter Queue**: 발행 실패 이벤트 처리
- **Domain Event**: Outbox에 저장되는 이벤트의 형태

## 참고 자료

- [Chris Richardson - Pattern: Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium - Outbox Event Router](https://debezium.io/documentation/reference/transformations/outbox-event-router.html)
- [Confluent - Microservices Data Patterns](https://www.confluent.io/blog/microservices-data-patterns-cqrs-event-sourcing-outbox/)
