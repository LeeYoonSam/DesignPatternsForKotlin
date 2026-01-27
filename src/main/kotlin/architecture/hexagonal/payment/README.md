# Hexagonal Architecture (Ports & Adapters) Pattern

## 개요

Hexagonal Architecture(헥사고날 아키텍처)는 Alistair Cockburn이 2005년에 제안한 아키텍처 패턴으로, **Ports and Adapters**(포트와 어댑터) 패턴이라고도 불립니다. 애플리케이션의 핵심 비즈니스 로직(도메인)을 외부 시스템(UI, 데이터베이스, 외부 서비스)으로부터 완전히 격리시키는 것이 목표입니다.

## 왜 "Hexagonal"인가?

육각형은 다양한 방향에서 연결될 수 있는 모양을 상징합니다. 실제 변의 개수보다는 애플리케이션이 여러 외부 시스템과 동등하게 상호작용할 수 있다는 점을 강조합니다.

```
                    ┌─────────────────┐
                    │   REST API      │
                    │   (Driving)     │
                    └────────┬────────┘
                             │
        ┌────────────────────▼────────────────────┐
        │                                          │
┌───────┴───────┐                        ┌─────────┴───────┐
│     CLI       │                        │  Message Queue  │
│   (Driving)   │                        │    (Driving)    │
└───────┬───────┘                        └─────────┬───────┘
        │          ┌──────────────────┐            │
        │          │                  │            │
        └──────────►    Application   ◄────────────┘
                   │      Core        │
        ┌──────────►    (Domain)      ◄────────────┐
        │          │                  │            │
        │          └──────────────────┘            │
┌───────┴───────┐                        ┌─────────┴───────┐
│   Database    │                        │  Payment        │
│   (Driven)    │                        │  Gateway        │
└───────────────┘                        │   (Driven)      │
                                         └─────────────────┘
                    ┌─────────────────┐
                    │  Notification   │
                    │    (Driven)     │
                    └─────────────────┘
```

## 핵심 개념

### 1. Application Core (Domain)

애플리케이션의 핵심 비즈니스 로직이 위치합니다. 외부 의존성이 전혀 없어야 합니다.

```kotlin
object Domain {
    // Value Objects
    @JvmInline
    value class PaymentId(val value: String)

    @JvmInline
    value class Money(val amount: Double) {
        init {
            require(amount >= 0) { "금액은 0 이상이어야 합니다" }
        }
    }

    // Entity
    data class Payment(
        val id: PaymentId,
        val orderId: OrderId,
        val amount: Money,
        val status: PaymentStatus
    ) {
        fun complete(): Payment = copy(status = PaymentStatus.COMPLETED)
        fun fail(reason: String): Payment = copy(status = PaymentStatus.FAILED)
    }

    // Domain Service
    class PaymentValidator {
        fun validate(order: Order, amount: Money): ValidationResult {
            // 순수한 비즈니스 규칙 검증
        }
    }
}
```

### 2. Ports (인터페이스)

도메인과 외부 세계 사이의 **계약**을 정의합니다.

#### Inbound Ports (Primary/Driving Ports)
외부에서 애플리케이션으로 들어오는 진입점을 정의합니다.

```kotlin
// 외부(Controller, CLI)가 호출하는 유스케이스
interface ProcessPaymentUseCase {
    fun processPayment(command: ProcessPaymentCommand): PaymentResult
}

interface GetPaymentUseCase {
    fun getPayment(paymentId: String): PaymentInfo?
}

interface RefundPaymentUseCase {
    fun refundPayment(paymentId: String): RefundResult
}
```

#### Outbound Ports (Secondary/Driven Ports)
애플리케이션에서 외부 시스템으로 나가는 인터페이스를 정의합니다.

```kotlin
// 도메인이 필요로 하는 외부 기능 정의
interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: PaymentId): Payment?
}

interface PaymentGateway {
    fun charge(cardNumber: CardNumber, amount: Money): GatewayResponse
    fun refund(paymentId: PaymentId, amount: Money): GatewayResponse
}

interface NotificationService {
    fun sendPaymentConfirmation(orderId: OrderId, amount: Money)
}
```

### 3. Adapters (구현체)

포트의 실제 구현체입니다.

#### Driving Adapters (Primary Adapters)
외부에서 애플리케이션으로 요청을 전달합니다.

```kotlin
// REST API Adapter
class RestPaymentController(
    private val processPaymentUseCase: ProcessPaymentUseCase,
    private val getPaymentUseCase: GetPaymentUseCase
) {
    // POST /payments
    fun createPayment(request: CreatePaymentRequest): ApiResponse {
        val command = ProcessPaymentCommand(
            orderId = request.orderId,
            amount = request.amount,
            cardNumber = request.cardNumber
        )
        return when (val result = processPaymentUseCase.processPayment(command)) {
            is PaymentResult.Success -> ApiResponse(201, mapOf("paymentId" to result.paymentId))
            is PaymentResult.Failure -> ApiResponse(400, mapOf("error" to result.message))
        }
    }
}

// CLI Adapter
class CliPaymentAdapter(
    private val processPaymentUseCase: ProcessPaymentUseCase
) {
    fun processCommand(args: Array<String>): String {
        val command = ProcessPaymentCommand(...)
        return when (val result = processPaymentUseCase.processPayment(command)) {
            is PaymentResult.Success -> "결제 성공: ${result.paymentId}"
            is PaymentResult.Failure -> "결제 실패: ${result.message}"
        }
    }
}
```

#### Driven Adapters (Secondary Adapters)
애플리케이션에서 외부 시스템으로 요청을 전달합니다.

```kotlin
// In-Memory Repository (테스트용)
class InMemoryPaymentRepository : PaymentRepository {
    private val payments = ConcurrentHashMap<String, Payment>()

    override fun save(payment: Payment) = payment.also {
        payments[it.id.value] = it
    }
    override fun findById(id: PaymentId) = payments[id.value]
}

// JPA Repository (프로덕션용)
class JpaPaymentRepository(
    private val entityManager: EntityManager
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        entityManager.persist(payment.toEntity())
        return payment
    }
    // ...
}

// Stripe Payment Gateway
class StripePaymentGateway(
    private val apiKey: String
) : PaymentGateway {
    override fun charge(cardNumber: CardNumber, amount: Money): GatewayResponse {
        // Stripe API 호출
    }
}
```

## Clean Architecture와의 비교

| 측면 | Hexagonal Architecture | Clean Architecture |
|------|----------------------|-------------------|
| 핵심 개념 | Ports & Adapters | Layers & Dependencies |
| 레이어 구조 | 명시적인 레이어 없음 | 4개 레이어 (Entity, Use Case, Interface, Framework) |
| 의존성 방향 | 외부 → 내부 (Port 통해) | 외부 → 내부 (Interface 통해) |
| 강조점 | 다양한 외부 시스템 연결 | 의존성 규칙 및 계층 분리 |
| 테스트 | Port Mock으로 테스트 | Layer별 테스트 |

## 의존성 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                         Adapters                                │
│  ┌──────────────┐                        ┌──────────────┐       │
│  │   REST API   │                        │   Database   │       │
│  │  Controller  │                        │  Repository  │       │
│  └──────┬───────┘                        └──────┬───────┘       │
│         │ implements                             │ implements   │
│         ▼                                        ▼              │
│  ┌──────────────┐                        ┌──────────────┐       │
│  │   Inbound    │                        │   Outbound   │       │
│  │    Port      │◄──────────────────────►│    Port      │       │
│  └──────┬───────┘                        └──────┬───────┘       │
│         │                                        │              │
│         │         ┌──────────────────┐          │              │
│         │         │                  │          │              │
│         └────────►│  Application     │◄─────────┘              │
│                   │    Service       │                          │
│                   │                  │                          │
│                   │   (implements    │                          │
│                   │   inbound port,  │                          │
│                   │   uses outbound  │                          │
│                   │      port)       │                          │
│                   └────────┬─────────┘                          │
│                            │                                    │
│                            ▼                                    │
│                   ┌──────────────────┐                          │
│                   │     Domain       │                          │
│                   │   (Entities,     │                          │
│                   │  Value Objects)  │                          │
│                   └──────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

## 테스트 용이성

Hexagonal Architecture의 가장 큰 장점 중 하나는 테스트가 매우 쉽다는 것입니다.

```kotlin
class PaymentServiceTest {

    @Test
    fun `결제 성공 테스트`() {
        // Given: 테스트용 어댑터 (In-Memory)
        val paymentRepository = InMemoryPaymentRepository()
        val orderRepository = InMemoryOrderRepository()
        val paymentGateway = FakePaymentGateway() // 항상 성공
        val notificationService = MockNotificationService()

        // 테스트 데이터
        orderRepository.addOrder(Order(OrderId("order-1"), Money(50000.0)))

        val service = PaymentService(
            paymentRepository, orderRepository,
            paymentGateway, notificationService
        )

        // When
        val result = service.processPayment(
            ProcessPaymentCommand("order-1", 50000.0, "4111111111111111")
        )

        // Then
        assertThat(result).isInstanceOf(PaymentResult.Success::class.java)
    }

    @Test
    fun `결제 게이트웨이 실패 테스트`() {
        // Given: 실패하도록 설정된 게이트웨이
        val paymentGateway = FakePaymentGateway().apply {
            shouldFail = true
            failureMessage = "잔액 부족"
        }

        // When & Then
        // 비즈니스 로직은 동일하지만 게이트웨이 동작만 다르게 테스트
    }
}
```

## 디렉토리 구조 예시

```
src/main/kotlin/
├── domain/                      # Application Core
│   ├── model/
│   │   ├── Payment.kt          # Entity
│   │   ├── PaymentId.kt        # Value Object
│   │   └── Money.kt            # Value Object
│   ├── service/
│   │   └── PaymentValidator.kt # Domain Service
│   └── event/
│       └── PaymentEvent.kt     # Domain Events
│
├── application/                 # Use Cases (Application Services)
│   ├── port/
│   │   ├── inbound/            # Inbound Ports
│   │   │   ├── ProcessPaymentUseCase.kt
│   │   │   └── GetPaymentUseCase.kt
│   │   └── outbound/           # Outbound Ports
│   │       ├── PaymentRepository.kt
│   │       ├── PaymentGateway.kt
│   │       └── NotificationService.kt
│   └── service/
│       └── PaymentService.kt   # Port Implementation
│
└── adapter/                     # Adapters
    ├── inbound/                 # Driving Adapters
    │   ├── rest/
    │   │   └── PaymentController.kt
    │   ├── cli/
    │   │   └── CliPaymentAdapter.kt
    │   └── message/
    │       └── PaymentMessageConsumer.kt
    └── outbound/                # Driven Adapters
        ├── persistence/
        │   ├── JpaPaymentRepository.kt
        │   └── InMemoryPaymentRepository.kt
        ├── payment/
        │   ├── StripePaymentGateway.kt
        │   └── FakePaymentGateway.kt
        └── notification/
            ├── EmailNotificationService.kt
            └── SmsNotificationService.kt
```

## 장점

1. **관심사의 완벽한 분리**: 비즈니스 로직이 기술적 세부사항과 완전히 분리
2. **테스트 용이성**: 포트를 통해 Mock 주입이 쉬움
3. **기술 교체 용이**: 어댑터만 교체하면 DB, 외부 서비스 변경 가능
4. **다중 인터페이스 지원**: 동일 로직을 REST, CLI, MQ 등 다양한 방식으로 노출
5. **유지보수성**: 변경의 영향 범위가 제한적

## 단점

1. **초기 복잡성**: 인터페이스와 구현체가 많아짐
2. **간단한 앱에는 과도함**: CRUD 위주의 앱에는 오버엔지니어링
3. **학습 곡선**: 포트/어댑터 개념 이해 필요

## 적용 시점

- 복잡한 비즈니스 로직이 있는 경우
- 외부 시스템이 자주 변경될 수 있는 경우
- 다양한 인터페이스(REST, CLI, 메시지)를 지원해야 하는 경우
- 높은 테스트 커버리지가 필요한 경우
- 장기적으로 유지보수할 시스템인 경우

## 관련 패턴

- **Clean Architecture**: 유사한 목표, 레이어 기반 접근
- **Onion Architecture**: 동심원 레이어 구조
- **DDD (Domain-Driven Design)**: 도메인 모델링과 함께 사용
- **CQRS**: 읽기/쓰기 분리와 함께 적용 가능
- **Dependency Injection**: 어댑터 주입에 필수

## 참고 자료

- [Alistair Cockburn - Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Netflix - Ready for changes with Hexagonal Architecture](https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749)
- [Herberto Graca - DDD, Hexagonal, Onion, Clean, CQRS, How I put it all together](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)
