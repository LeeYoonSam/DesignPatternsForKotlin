# Domain Service Pattern

## 설명
Domain Service는 도메인 주도 설계(DDD)에서 특정 Entity나 Value Object에 자연스럽게 속하지 않는 도메인 로직을 캡슐화하는 패턴입니다. 여러 Aggregate 간의 조율이 필요하거나, 도메인 개념이지만 상태를 가지지 않는 연산을 표현할 때 사용합니다.

## Domain Service가 필요한 경우

### 1. 여러 Aggregate 간 조율
```kotlin
// 송금: 두 계좌(Aggregate) 간의 조율 필요
class MoneyTransferService {
    fun transfer(sourceAccount: Account, targetAccount: Account, amount: Money)
}
```

### 2. Entity에 어울리지 않는 도메인 로직
```kotlin
// 수수료 계산: 어떤 Entity의 책임인가?
class TransferFeeCalculationService {
    fun calculate(amount: Money, accountType: AccountType): Money
}
```

### 3. 외부 시스템과의 도메인 수준 상호작용
```kotlin
// 환율 조회: 외부 API지만 도메인 개념
interface ExchangeRateService {
    fun getRate(from: Currency, to: Currency): BigDecimal
}
```

## 문제점 (패턴 적용 전)

1. **Entity에 억지로 로직 배치**
   - 송금 로직이 Account Entity에 있음
   - Entity가 다른 Entity를 직접 수정 (캡슐화 위반)

2. **Application Service에 도메인 로직 유출**
   - 수수료 계산이 Application Service에 있음
   - 한도 확인, AML 체크 등이 분산

3. **테스트 어려움**
   - 도메인 로직이 여러 곳에 분산
   - 모든 의존성을 모킹해야 함

4. **중복 코드**
   - 같은 비즈니스 규칙이 여러 곳에 존재

## Domain Service 특징

### 1. 무상태 (Stateless)
```kotlin
class TransferFeeCalculationService {
    // 상태 없음 - 입력만으로 결과 계산
    fun calculate(amount: Money, accountType: AccountType): Money {
        val feeRate = when (accountType) {
            AccountType.PREMIUM -> BigDecimal("0.001")
            AccountType.SAVINGS -> BigDecimal("0.002")
            AccountType.CHECKING -> BigDecimal("0.003")
        }
        return amount * feeRate
    }
}
```

### 2. 도메인 언어로 표현
```kotlin
// ✅ 도메인 언어 사용
class MoneyTransferService { ... }
class CreditScoreCalculationService { ... }
class ShippingCostCalculationService { ... }

// ❌ 기술적 이름
class TransferHelper { ... }
class CalculatorUtil { ... }
```

### 3. 인터페이스로 추상화
```kotlin
interface ExchangeRateService {
    fun getRate(from: Currency, to: Currency): BigDecimal
    fun convert(amount: Money, targetCurrency: Currency): Money
}

// 구현체는 인프라 레이어에
class ExternalApiExchangeRateService : ExchangeRateService { ... }
class CachedExchangeRateService : ExchangeRateService { ... }
```

## 주요 구성 요소

### 예제: 은행 송금 시스템

```
┌─────────────────────────────────────────────────────────┐
│                   Application Service                    │
│  ┌─────────────────────────────────────────────────┐   │
│  │              TransferApplicationService          │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    Domain Services                       │
│  ┌─────────────────┐  ┌─────────────────┐              │
│  │ MoneyTransfer   │  │ FeeCalculation  │              │
│  │ Service         │  │ Service         │              │
│  └────────┬────────┘  └─────────────────┘              │
│           │           ┌─────────────────┐              │
│           │           │ ExchangeRate    │              │
│           │           │ Service         │              │
│           │           └─────────────────┘              │
│           │           ┌─────────────────┐              │
│           │           │ TransferLimit   │              │
│           │           │ Service         │              │
│           │           └─────────────────┘              │
│           │           ┌─────────────────┐              │
│           │           │ AmlCheck        │              │
│           │           │ Service         │              │
│           │           └─────────────────┘              │
└───────────┼─────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────┐
│                    Domain Model                          │
│  ┌─────────────────┐  ┌─────────────────┐              │
│  │     Account     │  │      Money      │              │
│  │    (Entity)     │  │ (Value Object)  │              │
│  └─────────────────┘  └─────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

### Domain Service 구현 예시

| 서비스 | 역할 | 특징 |
|--------|------|------|
| `MoneyTransferService` | 계좌 간 송금 조율 | 핵심 도메인 서비스 |
| `TransferFeeCalculationService` | 수수료 계산 | 순수 계산 로직 |
| `ExchangeRateService` | 환율 조회/환전 | 외부 연동 추상화 |
| `TransferLimitService` | 송금 한도 검증 | 정책 기반 검증 |
| `AmlCheckService` | 자금세탁방지 검사 | 규제 준수 로직 |

## Domain Service vs 다른 서비스

| 구분 | Domain Service | Application Service | Infrastructure Service |
|------|---------------|---------------------|----------------------|
| 위치 | 도메인 레이어 | 애플리케이션 레이어 | 인프라 레이어 |
| 역할 | 도메인 로직 | 유스케이스 조율 | 기술적 구현 |
| 상태 | 무상태 | 무상태 | 상태 가능 |
| 예시 | 수수료 계산 | 주문 처리 흐름 | 이메일 발송 |
| 의존성 | 도메인 객체만 | 도메인 + 인프라 | 외부 시스템 |

## Kotlin 구현 기법

### 1. 인터페이스 + 구현체 분리
```kotlin
// 도메인 레이어 (인터페이스)
interface ExchangeRateService {
    fun getRate(from: Currency, to: Currency): BigDecimal
}

// 인프라 레이어 (구현체)
class ExternalApiExchangeRateService(
    private val httpClient: HttpClient
) : ExchangeRateService {
    override fun getRate(from: Currency, to: Currency): BigDecimal {
        // 외부 API 호출
    }
}
```

### 2. 결과 객체 반환
```kotlin
class TransferFeeCalculationService {
    data class FeeCalculationResult(
        val baseFee: Money,
        val discountRate: BigDecimal,
        val finalFee: Money,
        val breakdown: String
    )

    fun calculate(...): FeeCalculationResult
}
```

### 3. Command 객체 사용
```kotlin
class MoneyTransferService {
    data class TransferCommand(
        val fromAccountId: AccountId,
        val toAccountId: AccountId,
        val amount: Money,
        val description: String
    )

    fun transfer(command: TransferCommand): TransferResult
}
```

### 4. 의존성 주입
```kotlin
class MoneyTransferService(
    private val feeService: TransferFeeCalculationService,
    private val exchangeService: ExchangeRateService,
    private val limitService: TransferLimitService,
    private val amlService: AmlCheckService
) {
    fun transfer(...): TransferResult {
        // 각 서비스 활용
    }
}
```

## 실제 활용 사례

### 1. 금융 도메인
```kotlin
class CreditScoreCalculationService { ... }
class LoanEligibilityService { ... }
class InterestRateCalculationService { ... }
class FraudDetectionService { ... }
```

### 2. 전자상거래
```kotlin
class ShippingCostCalculationService { ... }
class DiscountCalculationService { ... }
class InventoryAllocationService { ... }
class PricingService { ... }
```

### 3. 물류
```kotlin
class RouteOptimizationService { ... }
class DeliveryTimeEstimationService { ... }
class CargoAllocationService { ... }
```

### 4. 예약 시스템
```kotlin
class AvailabilityCheckService { ... }
class PricingCalculationService { ... }
class OverbookingPolicyService { ... }
```

## 관련 패턴

| 패턴 | 관계 |
|------|------|
| Entity | Domain Service가 조율하는 대상 |
| Value Object | Domain Service의 입출력 타입 |
| Aggregate | Domain Service가 여러 Aggregate 조율 |
| Repository | Domain Service가 조회에 사용 |
| Factory | 복잡한 객체 생성 시 협력 |

## 주의사항

### 1. 빈약한 도메인 모델 방지
```kotlin
// ❌ 모든 로직을 Domain Service로
class OrderService {
    fun calculateTotal(order: Order): Money { ... }
    fun addItem(order: Order, item: Item) { ... }
    fun confirm(order: Order) { ... }
}

// ✅ Entity가 할 수 있는 건 Entity에
class Order {
    fun calculateTotal(): Money { ... }
    fun addItem(item: Item) { ... }
    fun confirm() { ... }
}
```

### 2. Domain Service 남용 금지
- Entity에 속할 수 있는 로직은 Entity에
- Domain Service는 정말 필요할 때만 사용
- "어디에 둘지 모르겠으면 Domain Service"는 잘못된 접근

### 3. 기술적 관심사 배제
```kotlin
// ❌ 기술적 관심사 포함
class TransferService {
    fun transfer(...) {
        // 트랜잭션 관리
        // 로깅
        // 캐싱
    }
}

// ✅ 순수 도메인 로직만
class MoneyTransferService {
    fun transfer(...) {
        // 도메인 규칙만
    }
}
```

## 결론

Domain Service는 Entity나 Value Object에 자연스럽게 속하지 않는 도메인 로직을 위한 패턴입니다. 여러 Aggregate 간의 조율, 복잡한 계산, 외부 시스템과의 도메인 수준 상호작용 등에 사용됩니다. 무상태로 설계하여 테스트가 용이하고, 도메인 언어로 명명하여 비즈니스 의도를 명확히 표현합니다. 단, Entity에 속할 수 있는 로직까지 Domain Service로 빼면 빈약한 도메인 모델이 되므로 주의해야 합니다.
