# Aggregate Pattern

## 설명
Aggregate는 도메인 주도 설계(DDD)의 핵심 전술적 패턴으로, 관련된 객체들을 하나의 일관성 경계(Consistency Boundary)로 묶어 데이터 변경의 단위를 정의합니다. Aggregate Root를 통해서만 내부 객체에 접근할 수 있으며, 이를 통해 도메인 불변식(Invariant)을 보장합니다.

## 문제점 (패턴 적용 전)
1. 객체 경계 불분명
    - 어디서든 내부 객체 직접 수정 가능
    - 일관성 규칙 우회 가능
    - 트랜잭션 범위 불명확
2. 불변식 위반
    - 비즈니스 규칙이 서비스에 분산
    - 도메인 객체가 무효한 상태로 전이 가능
    - 검증 로직 중복
3. 데이터 정합성 문제
    - 관련 객체 간 동기화 누락
    - 부분 업데이트로 인한 불일치
    - 동시성 문제

## Aggregate 핵심 개념

### 1. Aggregate Root
```kotlin
class Order private constructor(
    val id: OrderId,
    private val _items: MutableList<OrderItem>,
    private var _status: OrderStatus
) {
    // 외부에서는 읽기만 가능
    val items: List<OrderItem> get() = _items.toList()
    val status: OrderStatus get() = _status

    // 모든 변경은 Aggregate Root를 통해서만
    fun addItem(item: OrderItem) {
        requirePendingStatus()
        _items.add(item)
    }
}
```

### 2. 일관성 경계 (Consistency Boundary)
```
┌─────────────────────────────────────────┐
│             Order Aggregate              │
│  ┌─────────────────────────────────┐    │
│  │     Order (Aggregate Root)      │    │
│  │  ┌───────────┐ ┌───────────┐   │    │
│  │  │ OrderItem │ │ OrderItem │   │    │
│  │  └───────────┘ └───────────┘   │    │
│  │  ┌─────────────────────────┐   │    │
│  │  │    ShippingAddress      │   │    │
│  │  └─────────────────────────┘   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### 3. 불변식 (Invariant)
```kotlin
class Order {
    companion object {
        private const val MAX_ITEMS = 10
        private val MIN_AMOUNT = Money.of(10000)
    }

    fun confirm() {
        // 모든 불변식 검증
        require(_items.isNotEmpty()) { "상품이 없습니다" }
        require(_shippingAddress != null) { "배송 주소가 필요합니다" }
        require(totalAmount >= MIN_AMOUNT) { "최소 금액 미달" }

        _status = OrderStatus.CONFIRMED
    }
}
```

## 주요 구성 요소

### Aggregate 내부 구조
| 구성 요소 | 설명 | 예시 |
|----------|------|------|
| Aggregate Root | 외부 접근 유일한 진입점 | Order |
| Entity | 식별자로 구분되는 내부 객체 | OrderItem |
| Value Object | 속성으로 동등성 비교 | Money, Address |
| Domain Event | 상태 변경 이벤트 | OrderConfirmed |

### 예제 구조
```
Order (Aggregate Root)
├── OrderId (Value Object - 식별자)
├── CustomerId (Value Object - 참조)
├── OrderStatus (Enum)
├── List<OrderItem> (Entity)
│   ├── OrderItemId
│   ├── ProductId (다른 Aggregate 참조)
│   ├── Money (unitPrice)
│   └── Quantity
├── Address (Value Object)
└── List<OrderEvent> (Domain Events)
```

## Aggregate 설계 규칙

### 규칙 1: Aggregate Root를 통해서만 접근
```kotlin
// ❌ 잘못된 방법
order.items.add(newItem)  // 직접 접근

// ✅ 올바른 방법
order.addItem(productId, quantity)  // Root를 통해
```

### 규칙 2: Aggregate 간 참조는 ID로만
```kotlin
class Order {
    val customerId: CustomerId  // ✅ ID로 참조
    // val customer: Customer  // ❌ 직접 참조 금지
}
```

### 규칙 3: 하나의 트랜잭션에 하나의 Aggregate만 수정
```kotlin
// ❌ 잘못된 방법 - 여러 Aggregate 동시 수정
fun placeOrder(order: Order, customer: Customer) {
    order.confirm()
    customer.addPoints(100)  // 다른 Aggregate
}

// ✅ 올바른 방법 - 도메인 이벤트 활용
fun placeOrder(order: Order) {
    order.confirm()
    // OrderConfirmed 이벤트 발행 → 별도 트랜잭션에서 포인트 적립
}
```

### 규칙 4: Aggregate는 작게 유지
```kotlin
// ❌ 너무 큰 Aggregate
class Order {
    val items: List<OrderItem>
    val payments: List<Payment>      // 별도 Aggregate로
    val shipments: List<Shipment>    // 별도 Aggregate로
    val reviews: List<Review>        // 별도 Aggregate로
}

// ✅ 적절한 크기
class Order {
    val items: List<OrderItem>       // 주문과 함께 변경됨
    val shippingAddress: Address     // 주문과 함께 변경됨
}
```

## Domain Event 활용

### 이벤트 정의
```kotlin
sealed class OrderEvent {
    abstract val orderId: OrderId
}

data class OrderConfirmed(
    override val orderId: OrderId,
    val confirmedAt: LocalDateTime
) : OrderEvent()
```

### 이벤트 발행
```kotlin
class Order {
    private val _domainEvents = mutableListOf<OrderEvent>()
    val domainEvents: List<OrderEvent> get() = _domainEvents.toList()

    fun confirm() {
        // ... 검증
        _status = OrderStatus.CONFIRMED
        _domainEvents.add(OrderConfirmed(id))
    }

    fun clearDomainEvents() = _domainEvents.clear()
}
```

## Kotlin 구현 기법

### 1. private setter + public getter
```kotlin
class Order {
    private var _status: OrderStatus = OrderStatus.PENDING
    val status: OrderStatus get() = _status
}
```

### 2. 방어적 복사
```kotlin
class Order {
    private val _items = mutableListOf<OrderItem>()
    val items: List<OrderItem> get() = _items.toList()
}
```

### 3. internal 가시성
```kotlin
class OrderItem {
    // Aggregate 내부에서만 호출 가능
    internal fun increaseQuantity(amount: Quantity) {
        _quantity = _quantity + amount
    }
}
```

### 4. private constructor + factory method
```kotlin
class Order private constructor(...) {
    companion object {
        fun create(customerId: CustomerId): Order {
            // 초기 불변식 보장
            return Order(...)
        }
    }
}
```

## 실제 활용 사례

### 1. 전자상거래
```
Order Aggregate: Order → OrderItem, Address
Product Aggregate: Product → ProductVariant
Customer Aggregate: Customer → Address, PaymentMethod
```

### 2. 은행
```
Account Aggregate: Account → Transaction (최근 N개)
Customer Aggregate: Customer → ContactInfo
```

### 3. 예약 시스템
```
Reservation Aggregate: Reservation → ReservationItem
Room Aggregate: Room → Availability
```

## Aggregate vs 다른 패턴

| 패턴 | 목적 | 관계 |
|------|------|------|
| Entity | 식별자 기반 동등성 | Aggregate 내부에 포함 |
| Value Object | 값 기반 동등성 | Aggregate 내부에 포함 |
| Repository | Aggregate 영속화 | Aggregate 단위로 저장/조회 |
| Factory | 복잡한 객체 생성 | Aggregate 생성에 활용 |
| Domain Event | 상태 변경 알림 | Aggregate에서 발행 |

## 주의사항

1. **Aggregate 크기**
   - 너무 크면 동시성 문제, 성능 저하
   - 너무 작으면 트랜잭션 일관성 보장 어려움

2. **Eventual Consistency**
   - Aggregate 간은 최종 일관성 허용
   - 도메인 이벤트로 동기화

3. **Lazy Loading**
   - 큰 컬렉션은 지연 로딩 고려
   - 필요한 데이터만 로드

4. **ID 참조**
   - 다른 Aggregate는 항상 ID로 참조
   - 직접 참조 시 경계 모호해짐

## 결론

Aggregate 패턴은 도메인 모델의 일관성을 보장하는 핵심 패턴입니다. Aggregate Root를 통한 캡슐화로 불변식을 보호하고, 명확한 트랜잭션 경계를 제공합니다. Domain Event와 함께 사용하면 Aggregate 간 느슨한 결합을 유지하면서도 비즈니스 요구사항을 충족할 수 있습니다.
