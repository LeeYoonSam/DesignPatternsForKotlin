# Domain Event Pattern

## 설명
Domain Event는 도메인 주도 설계(DDD)에서 도메인 내에서 발생한 중요한 비즈니스 사건을 표현하는 패턴입니다. "주문이 확정되었다", "결제가 완료되었다"와 같이 과거 시제로 표현되며, Aggregate 간의 느슨한 결합을 가능하게 합니다.

## 문제점 (패턴 적용 전)
1. 강한 결합
    - 주문 서비스가 재고, 포인트, 알림 서비스에 직접 의존
    - 새 기능 추가 시 기존 코드 수정 필요
    - 단위 테스트 시 모든 의존성 모킹 필요
2. 트랜잭션 문제
    - 모든 부수 효과가 하나의 트랜잭션에 포함
    - 일부 실패 시 전체 롤백
    - 트랜잭션 범위가 너무 넓어짐
3. 확장성 부족
    - 새로운 요구사항 추가가 어려움
    - 코드 변경 시 영향 범위가 넓음
4. 추적성 부족
    - 상태 변경 히스토리 추적 어려움
    - 감사 로그 구현이 복잡함

## Domain Event 특징

### 1. 과거 시제 명명
```kotlin
// ✅ 올바른 명명 - 이미 발생한 사실
class OrderConfirmed(...)
class PaymentCompleted(...)
class ItemShipped(...)

// ❌ 잘못된 명명 - 명령어 형태
class ConfirmOrder(...)
class CompletePayment(...)
```

### 2. 불변성
```kotlin
data class OrderConfirmed(
    val orderId: OrderId,
    val customerId: CustomerId,
    val totalAmount: Money,
    val occurredAt: LocalDateTime = LocalDateTime.now()
) : DomainEvent
// 모든 필드가 val (불변)
```

### 3. 자기 완결성
```kotlin
data class OrderConfirmed(
    val orderId: OrderId,
    val customerId: CustomerId,
    val totalAmount: Money,
    // 이벤트 처리에 필요한 모든 정보 포함
    val items: List<OrderItemSnapshot>
) : DomainEvent
```

## 주요 구성 요소

### 1. Domain Event 인터페이스
```kotlin
interface DomainEvent {
    val eventId: String       // 이벤트 고유 ID
    val occurredAt: LocalDateTime  // 발생 시각
    val aggregateId: String   // 발생 Aggregate ID
    val aggregateType: String // Aggregate 타입
}
```

### 2. 구체적인 이벤트
```kotlin
data class OrderConfirmed(
    val orderId: OrderId,
    val customerId: CustomerId,
    val totalAmount: Money,
    val items: List<OrderItemSnapshot>
) : BaseDomainEvent(orderId.value, "Order")
```

### 3. Event Dispatcher (이벤트 버스)
```kotlin
class EventDispatcher {
    private val handlers = mutableMapOf<KClass<*>, MutableList<EventHandler<*>>>()

    fun <T : DomainEvent> register(eventType: KClass<T>, handler: EventHandler<T>)
    fun dispatch(event: DomainEvent)
}
```

### 4. Event Handler
```kotlin
fun interface EventHandler<T : DomainEvent> {
    fun handle(event: T)
}

class InventoryEventHandler {
    fun onOrderConfirmed(event: OrderConfirmed) {
        event.items.forEach { item ->
            decreaseStock(item.productId, item.quantity)
        }
    }
}
```

## 이벤트 발행 패턴

### Aggregate 내부에서 이벤트 수집
```kotlin
class Order {
    private val _domainEvents = mutableListOf<DomainEvent>()
    val domainEvents: List<DomainEvent> get() = _domainEvents.toList()

    fun confirm() {
        require(_status == OrderStatus.PENDING)
        _status = OrderStatus.CONFIRMED

        // 이벤트 수집 (아직 발행 안 함)
        _domainEvents.add(OrderConfirmed(...))
    }

    fun clearDomainEvents() = _domainEvents.clear()
}
```

### Application Service에서 이벤트 발행
```kotlin
class OrderApplicationService(
    private val eventDispatcher: EventDispatcher
) {
    fun confirmOrder(orderId: OrderId) {
        val order = orderRepository.findById(orderId)

        // 핵심 도메인 로직
        order.confirm()
        orderRepository.save(order)

        // 이벤트 발행 (트랜잭션 커밋 후)
        eventDispatcher.dispatchAll(order.domainEvents)
        order.clearDomainEvents()
    }
}
```

## 이벤트 처리 방식

### 동기 처리 (In-Process)
```kotlin
eventDispatcher.register<OrderConfirmed> { event ->
    // 같은 트랜잭션에서 즉시 처리
    inventoryService.decreaseStock(event.items)
}
```

### 비동기 처리 (Message Queue)
```kotlin
// 발행 측
messageQueue.publish("order.confirmed", event)

// 구독 측 (별도 프로세스)
@EventListener("order.confirmed")
fun onOrderConfirmed(event: OrderConfirmed) {
    // 별도 트랜잭션에서 처리
    notificationService.sendEmail(event)
}
```

## 이벤트 흐름도

```
┌─────────────┐
│    Order    │ ──── confirm() ────┐
│ (Aggregate) │                    │
└─────────────┘                    ▼
                           ┌──────────────────┐
                           │  OrderConfirmed  │
                           │  (Domain Event)  │
                           └────────┬─────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                    ▼               ▼               ▼
            ┌───────────┐   ┌───────────┐   ┌───────────┐
            │ Inventory │   │  Points   │   │ Notifica- │
            │  Handler  │   │  Handler  │   │ tion      │
            └───────────┘   └───────────┘   └───────────┘
                    │               │               │
                    ▼               ▼               ▼
              재고 차감       포인트 적립      이메일 발송
```

## Event Sourcing과의 관계

### Domain Event만 사용
```kotlin
// 현재 상태만 저장
orderRepository.save(order)
// 이벤트는 부수 효과 처리용
eventDispatcher.dispatch(event)
```

### Event Sourcing 적용
```kotlin
// 이벤트가 곧 상태의 원천
eventStore.append(orderId, events)
// 상태 복원: 이벤트 재생
val order = eventStore.load(orderId).fold(Order()) { order, event ->
    order.apply(event)
}
```

## 실제 활용 사례

### 1. 주문 확정 시
```
OrderConfirmed 발생
├─→ InventoryHandler: 재고 차감
├─→ PointHandler: 포인트 적립
├─→ NotificationHandler: 이메일 발송
├─→ StatisticsHandler: 통계 업데이트
└─→ AuditHandler: 감사 로그 기록
```

### 2. 결제 완료 시
```
PaymentCompleted 발생
├─→ OrderHandler: 주문 상태 변경
├─→ InvoiceHandler: 영수증 생성
├─→ NotificationHandler: 알림 발송
└─→ AccountingHandler: 회계 처리
```

### 3. 회원 가입 시
```
UserRegistered 발생
├─→ WelcomeEmailHandler: 환영 이메일
├─→ PointHandler: 가입 포인트 지급
├─→ CouponHandler: 신규 회원 쿠폰 발급
└─→ AnalyticsHandler: 가입 통계
```

## Kotlin 구현 기법

### 1. sealed class로 이벤트 그룹화
```kotlin
sealed class OrderEvent : DomainEvent {
    abstract val orderId: OrderId
}

data class OrderCreated(...) : OrderEvent()
data class OrderConfirmed(...) : OrderEvent()
data class OrderCancelled(...) : OrderEvent()
```

### 2. inline reified로 타입 안전 등록
```kotlin
inline fun <reified T : DomainEvent> register(handler: EventHandler<T>) {
    register(T::class, handler)
}

// 사용
eventDispatcher.register<OrderConfirmed> { event ->
    // event는 OrderConfirmed 타입으로 추론됨
}
```

### 3. fun interface로 간결한 핸들러
```kotlin
fun interface EventHandler<T : DomainEvent> {
    fun handle(event: T)
}

// 람다로 간단히 등록
eventDispatcher.register<OrderConfirmed> { println(it) }
```

## 관련 패턴

| 패턴 | 관계 |
|------|------|
| Aggregate | 이벤트의 발생 주체 |
| Event Sourcing | 이벤트를 상태의 원천으로 사용 |
| CQRS | 이벤트로 읽기 모델 업데이트 |
| Saga | 이벤트로 분산 트랜잭션 조율 |
| Pub/Sub | 이벤트 전달 메커니즘 |

## 주의사항

1. **이벤트 순서**
   - 동일 Aggregate 이벤트는 순서 보장 필요
   - 분산 환경에서 순서 처리 고려

2. **이벤트 버전 관리**
   - 이벤트 스키마 변경 시 하위 호환성
   - 버전 필드 추가 고려

3. **멱등성**
   - 핸들러는 멱등하게 구현
   - 중복 처리 방지 메커니즘

4. **이벤트 크기**
   - 필요한 정보만 포함
   - 너무 큰 이벤트는 성능 저하

5. **실패 처리**
   - 핸들러 실패 시 재시도 정책
   - Dead Letter Queue 활용

## 결론

Domain Event 패턴은 도메인 객체 간의 결합도를 낮추고, 비즈니스 로직의 부수 효과를 명확하게 분리합니다. 새로운 기능 추가 시 기존 코드 수정 없이 핸들러만 추가하면 되어 개방-폐쇄 원칙(OCP)을 준수합니다. Event Sourcing, CQRS, Saga 패턴의 기반이 되며, 마이크로서비스 아키텍처에서 서비스 간 통신의 핵심 메커니즘으로 활용됩니다.
