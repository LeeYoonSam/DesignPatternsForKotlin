# Strangler Fig Pattern

## 개요

Strangler Fig Pattern은 Martin Fowler가 명명한 아키텍처 패턴으로, 레거시 시스템을 **점진적으로** 새로운 시스템으로 교체하는 전략입니다. 열대의 교살 무화과(Strangler Fig) 나무가 숙주 나무를 서서히 감싸며 대체하는 것에서 유래했습니다.

## 문제 상황

레거시 시스템을 교체할 때 흔히 발생하는 문제:

- **빅뱅 마이그레이션**: 전체 시스템을 한 번에 교체하면 실패 시 복구 불가
- **장기간 병렬 개발**: 레거시와 신규 시스템을 동시에 유지보수
- **데이터 불일치**: 마이그레이션 중 데이터 정합성 유지 어려움
- **롤백 불가**: 문제 발생 시 되돌리기 어려움

## 해결 방법

기능 단위로 레거시를 감싸서(Strangle) 하나씩 새 시스템으로 교체합니다.

```
Phase 0: 모든 요청 → 레거시
Phase 1: 주문 생성 → 신규 / 나머지 → 레거시
Phase 2: 주문+결제 → 신규 / 나머지 → 레거시
Phase 3: 주문+결제+배송 → 신규 / 리포트 → 레거시
Phase 4: 모든 요청 → 신규
Phase 5: 레거시 제거
```

## 핵심 구성 요소

### 1. Facade (Strangler Facade)

클라이언트와 레거시/신규 시스템 사이의 라우팅 레이어입니다.

```kotlin
class OrderSystemFacade(
    private val legacyService: LegacyOrderService,
    private val newOrderService: OrderCreationService,
    private val migrationConfig: MigrationConfig
) {
    fun createOrder(customerName: String, items: String, totalPrice: Double): String {
        return if (migrationConfig.isMigrated(MigrationFeature.ORDER_CREATION)) {
            // 신규 시스템으로 라우팅
            val order = newOrderService.createOrder(customerName, orderItems)
            order.id.value
        } else {
            // 레거시 시스템으로 라우팅
            legacyService.createOrder(customerName, items, totalPrice)
        }
    }
}
```

### 2. Migration Config (Feature Flag)

기능별 마이그레이션 상태를 관리합니다.

```kotlin
enum class MigrationFeature {
    ORDER_CREATION, ORDER_QUERY, PAYMENT, SHIPPING, REPORTING
}

class MigrationConfig {
    private val migratedFeatures = mutableSetOf<MigrationFeature>()

    fun enableFeature(feature: MigrationFeature) {
        migratedFeatures.add(feature)
    }

    fun disableFeature(feature: MigrationFeature) {
        migratedFeatures.remove(feature)  // 롤백!
    }

    fun isMigrated(feature: MigrationFeature): Boolean =
        feature in migratedFeatures
}
```

### 3. Anti-Corruption Layer

레거시 데이터 모델과 신규 도메인 모델 사이의 변환 레이어입니다.

```kotlin
data class Order(/* ... */) {
    companion object {
        // 레거시 → 신규 변환
        fun fromLegacy(legacyData: Map<String, Any>): Order {
            return Order(
                id = OrderId(legacyData["id"] as String),
                customerId = CustomerId(legacyData["customer"] as String),
                status = OrderStatus.fromLegacy(legacyData["status"] as String),
                // ...
            )
        }
    }
}
```

### 4. Shadow Traffic Verification

신규 시스템 전환 전에 양쪽 시스템의 결과를 비교 검증합니다.

```kotlin
class ShadowTrafficVerifier(
    private val legacyService: LegacyOrderService,
    private val newOrderService: OrderCreationService
) {
    fun verifyOrderCreation(customer: String, items: String, price: Double): VerificationResult {
        // 양쪽 실행
        val legacyResult = legacyService.createOrder(customer, items, price)
        val newResult = newOrderService.createOrder(customer, orderItems)

        // 결과 비교
        val differences = compareResults(legacyResult, newResult)
        return VerificationResult(isConsistent = differences.isEmpty())
    }
}
```

### 5. Canary Release

트래픽 비율을 점진적으로 조절하여 안전하게 전환합니다.

```kotlin
class CanaryRouter {
    private var canaryPercentage: Int = 0  // 0% → 10% → 50% → 100%

    fun shouldRouteToNew(): Boolean {
        return (1..100).random() <= canaryPercentage
    }

    // 에러율이 임계값 초과 시 자동 롤백
    fun checkHealthAndAdjust(totalRequests: Int, errorCount: Int) {
        val errorRate = errorCount.toDouble() / totalRequests
        if (errorRate > 0.05) {
            setCanaryPercentage(0)  // 자동 롤백
        }
    }
}
```

## 마이그레이션 전략 흐름

```
┌──────────────────────────────────────────────────────────────┐
│                      Client Request                          │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
              ┌────────────────┐
              │   Strangler    │
              │    Facade      │
              │  (Router)      │
              └───┬──────┬─────┘
                  │      │
          ┌───────┘      └────────┐
          ▼                       ▼
  ┌───────────────┐      ┌───────────────┐
  │   Legacy      │      │   New         │
  │   System      │      │   System      │
  │               │      │               │
  │ ⬜ 주문 생성   │      │ ✅ 주문 생성   │
  │ ⬜ 결제       │      │ ✅ 결제       │
  │ ✅ 배송       │      │ ⬜ 배송       │
  │ ✅ 리포트     │      │ ⬜ 리포트     │
  └───────────────┘      └───────────────┘

  ※ 기능별로 점진적으로 ✅ → ⬜, ⬜ → ✅ 전환
```

## 단계별 마이그레이션 프로세스

### Step 1: Shadow Traffic (검증)
```
요청 → Facade → 레거시 (실제 응답)
              → 신규 (결과 비교만, 응답 안함)
```

### Step 2: Canary Release (점진적 전환)
```
요청 → Facade → 10% → 신규 시스템
              → 90% → 레거시 시스템
```

### Step 3: Full Migration (완전 전환)
```
요청 → Facade → 100% → 신규 시스템
```

### Step 4: Legacy Removal (레거시 제거)
```
요청 → 신규 시스템 (Facade 제거 가능)
```

## 롤백 전략

```kotlin
// 문제 발생 시 해당 기능만 레거시로 롤백
migrationConfig.disableFeature(MigrationFeature.PAYMENT)

// Canary 기반 자동 롤백
canaryRouter.checkHealthAndAdjust(
    totalRequests = 1000,
    errorCount = 80  // 8% 에러 → 자동 롤백
)
```

## 장점

1. **점진적 전환**: 기능 단위로 안전하게 마이그레이션
2. **낮은 리스크**: 문제 발생 시 해당 기능만 롤백 가능
3. **비즈니스 연속성**: 서비스 중단 없이 전환
4. **검증 가능**: Shadow Traffic으로 사전 검증
5. **팀 효율**: 기능별 분담하여 병렬 작업 가능
6. **학습 기회**: 점진적 전환 과정에서 새 시스템에 대한 이해도 증가

## 단점

1. **복잡성 증가**: Facade, Config, ACL 등 추가 레이어 필요
2. **일시적 중복**: 전환 기간 동안 양쪽 시스템 유지 필요
3. **데이터 동기화**: 양쪽 시스템의 데이터 정합성 관리 필요
4. **장기 프로젝트**: 완전한 전환까지 시간이 오래 걸릴 수 있음

## 적용 시점

- 대규모 레거시 시스템을 새로운 기술 스택으로 교체할 때
- 빅뱅 마이그레이션의 리스크가 너무 높을 때
- 서비스 중단 없이 시스템을 전환해야 할 때
- 마이크로서비스로의 전환을 계획 중일 때

## Clean Architecture / Hexagonal Architecture와의 관계

| 패턴 | 역할 |
|------|------|
| **Strangler Fig** | 레거시 → 신규 시스템 전환 전략 |
| **Clean Architecture** | 신규 시스템의 내부 구조 설계 |
| **Hexagonal Architecture** | 신규 시스템의 포트/어댑터 경계 |
| **Anti-Corruption Layer** | 레거시 ↔ 신규 데이터 변환 |

실무에서는 Strangler Fig으로 전환하면서, 신규 시스템은 Clean/Hexagonal Architecture로 설계하는 것이 일반적입니다.

## 관련 패턴

- **Anti-Corruption Layer**: 레거시와 신규 모델 간 변환
- **Feature Toggle**: 기능별 활성화/비활성화
- **Facade Pattern**: 복잡한 시스템을 단순한 인터페이스로 감싸기
- **Adapter Pattern**: 호환되지 않는 인터페이스 연결

## 참고 자료

- [Martin Fowler - Strangler Fig Application](https://martinfowler.com/bliki/StranglerFigApplication.html)
- [Microsoft - Strangler Fig Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/strangler-fig)
- [Sam Newman - Monolith to Microservices](https://samnewman.io/books/monolith-to-microservices/)
