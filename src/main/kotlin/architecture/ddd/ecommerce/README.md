# Value Object & Entity Pattern

## 설명
Value Object와 Entity는 도메인 주도 설계(DDD)의 핵심 빌딩 블록입니다. 도메인 모델을 풍부하게 표현하고, 비즈니스 로직을 캡슐화하며, 코드의 명확성과 안전성을 높이는 데 사용됩니다.

### Value Object (값 객체)
속성 값으로 동등성이 결정되는 불변 객체입니다. 개념적 정체성이 없으며, 속성 값이 같으면 동일한 것으로 간주됩니다.

### Entity (엔티티)
고유 식별자(Identity)로 구분되는 도메인 객체입니다. 속성이 변해도 동일한 식별자를 가지면 같은 엔티티로 간주됩니다.

## 문제점 (패턴 적용 전)
1. 원시 타입 집착 (Primitive Obsession)
    - Double로 금액 표현 → 통화 정보 손실, 정밀도 문제
    - String으로 이메일 표현 → 유효성 검증 분산
    - 도메인 의미 손실
2. 유효성 검증 분산
    - 서비스 계층에서 검증 → 중복 코드
    - 검증 누락 가능성
    - 불변식(Invariant) 보장 어려움
3. 가변 객체 문제
    - 의도치 않은 상태 변경
    - 공유 시 부작용 발생
    - 스레드 안전성 문제
4. 동등성 비교 혼란
    - null 가능한 식별자
    - 값과 식별자 비교 혼동

## Value Object 특징

### 1. 불변성 (Immutability)
```kotlin
data class Money(val amount: BigDecimal, val currency: Currency) {
    operator fun plus(other: Money): Money {
        return Money(amount + other.amount, currency)  // 새 객체 반환
    }
}
```

### 2. 값 동등성 (Value Equality)
```kotlin
val money1 = Money.of(1000)
val money2 = Money.of(1000)
println(money1 == money2)  // true - 값이 같으면 동일
```

### 3. 자기 유효성 검증 (Self-Validation)
```kotlin
@JvmInline
value class Email private constructor(val value: String) {
    init {
        require(isValid(value)) { "유효하지 않은 이메일: $value" }
    }
}
```

### 4. 부작용 없는 행위 (Side-Effect-Free)
```kotlin
// 원본을 변경하지 않고 새 객체 반환
fun Money.times(multiplier: Int): Money {
    return Money(amount * multiplier, currency)
}
```

## Entity 특징

### 1. 식별자 동등성 (Identity Equality)
```kotlin
class Order(val id: OrderId, ...) {
    override fun equals(other: Any?): Boolean {
        if (other !is Order) return false
        return id == other.id  // 식별자로 비교
    }
}
```

### 2. 연속성 (Continuity)
```kotlin
val order = Order.create(...)
order.confirm()  // 상태가 변해도
order.ship()     // 같은 주문
order.deliver()  // (동일한 id)
```

### 3. 생명주기 (Lifecycle)
```kotlin
enum class OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    abstract fun canTransitionTo(next: OrderStatus): Boolean
}
```

## 주요 구성 요소

### Value Objects
| 클래스 | 설명 | 특징 |
|--------|------|------|
| `Money` | 금액 | BigDecimal, 통화, 연산자 오버로딩 |
| `Email` | 이메일 | 형식 검증, inline class |
| `PhoneNumber` | 전화번호 | 형식 변환, 검증 |
| `Address` | 주소 | 복합 Value Object |
| `ZipCode` | 우편번호 | 형식 검증 |
| `Quantity` | 수량 | 음수 방지, 연산 |

### Entity Identifiers (Value Objects)
| 클래스 | 설명 |
|--------|------|
| `OrderId` | 주문 식별자 |
| `CustomerId` | 고객 식별자 |
| `ProductId` | 상품 식별자 |

### Entities
| 클래스 | 설명 | 특징 |
|--------|------|------|
| `Order` | 주문 | 상태 전이, 항목 관리 |
| `Customer` | 고객 | 정보 업데이트 |
| `Product` | 상품 | 재고 관리 |

## Kotlin 구현 기법

### 1. data class
```kotlin
data class Money(val amount: BigDecimal, val currency: Currency)
```
- 자동 equals/hashCode
- copy() 메서드
- 불변성 보장

### 2. @JvmInline value class
```kotlin
@JvmInline
value class Email(val value: String)
```
- 런타임 오버헤드 없음
- 타입 안전성
- 래퍼 클래스의 장점

### 3. private constructor + companion object
```kotlin
class Order private constructor(...) {
    companion object {
        fun create(...): Order = Order(...)
    }
}
```
- 생성 제어
- 팩토리 메서드 패턴
- 불변식 보장

### 4. Backing Property
```kotlin
class Order {
    private var _status: OrderStatus = PENDING
    val status: OrderStatus get() = _status
}
```
- 읽기 전용 노출
- 내부 변경 허용

## Value Object vs Entity 비교

| 특성 | Value Object | Entity |
|------|--------------|--------|
| 동등성 | 속성 값으로 비교 | 식별자로 비교 |
| 가변성 | 불변 | 가변 가능 |
| 수명 | 무한 (값은 영원) | 생명주기 있음 |
| 공유 | 자유롭게 공유 | 참조로 공유 |
| 예시 | Money, Address | Order, Customer |

## 실제 활용 사례

1. **전자상거래**
   - Value Object: Money, Address, SKU
   - Entity: Order, Customer, Product

2. **은행 시스템**
   - Value Object: Money, AccountNumber, TransactionId
   - Entity: Account, Customer, Transaction

3. **예약 시스템**
   - Value Object: DateRange, TimeSlot, Price
   - Entity: Reservation, Room, Guest

4. **배송 시스템**
   - Value Object: Address, Weight, Dimension
   - Entity: Shipment, Package, Driver

## 관련 패턴

| 패턴 | 관계 |
|------|------|
| Repository | Entity 저장 및 조회 |
| Factory | Entity/Value Object 생성 |
| Aggregate | Entity 그룹화 및 일관성 경계 |
| Domain Event | Entity 상태 변경 알림 |

## 주의사항

1. **Value Object 크기**
   - 너무 큰 Value Object는 피함
   - 관련 속성만 그룹화

2. **Entity 식별자**
   - 비즈니스 키 vs 대리 키 선택
   - 식별자도 Value Object로 구현

3. **불변성 vs 성능**
   - 빈번한 변경 시 성능 고려
   - 필요시 Builder 패턴 활용

4. **영속화**
   - ORM 매핑 고려
   - Value Object 임베딩

## 결론

Value Object와 Entity 패턴은 도메인 모델의 명확성과 안전성을 크게 향상시킵니다. Value Object는 불변성과 자기 유효성 검증을 통해 버그를 줄이고, Entity는 비즈니스 규칙을 캡슐화하여 도메인 로직을 명확하게 표현합니다. Kotlin의 data class, value class, 프로퍼티 등을 활용하면 이러한 패턴을 간결하고 효과적으로 구현할 수 있습니다.
