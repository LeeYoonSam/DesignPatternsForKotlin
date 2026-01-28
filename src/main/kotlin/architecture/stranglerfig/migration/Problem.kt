package architecture.stranglerfig.migration

/**
 * Strangler Fig Pattern - Problem
 *
 * 이 파일은 Strangler Fig Pattern을 적용하지 않았을 때
 * 레거시 시스템을 교체하면서 발생하는 문제점을 보여줍니다.
 *
 * 문제점:
 * 1. 빅뱅 마이그레이션: 전체 시스템을 한 번에 교체하려고 시도
 * 2. 높은 리스크: 새 시스템이 실패하면 전체 서비스 중단
 * 3. 장기간 병렬 개발: 레거시와 신규 시스템을 동시에 유지보수
 * 4. 롤백 불가: 문제 발생 시 되돌리기 어려움
 * 5. 데이터 불일치: 마이그레이션 중 데이터 정합성 유지 어려움
 */

import java.time.LocalDateTime

// ========================================
// 레거시 시스템: 모놀리식 주문 관리 시스템
// ========================================

/**
 * 문제 1: 거대한 모놀리식 서비스
 * - 모든 기능이 하나의 클래스에 밀집
 * - 기술 부채가 누적된 레거시 코드
 * - 변경이 어렵고 위험함
 */
class LegacyOrderSystem {
    // 하드코딩된 DB 연결
    private val dbConnection = "jdbc:mysql://legacy-db:3306/orders"

    // 레거시 데이터 구조 (Map 기반, 타입 안전하지 않음)
    private val orders = mutableMapOf<String, Map<String, Any>>()
    private var orderSeq = 1000

    fun createOrder(customerName: String, items: String, totalPrice: Double): String {
        val orderId = "ORD-${orderSeq++}"
        // 레거시: Map 기반 데이터 저장 (타입 안전하지 않음)
        orders[orderId] = mapOf(
            "id" to orderId,
            "customer" to customerName,
            "items" to items,
            "price" to totalPrice,
            "status" to "NEW",
            "created" to LocalDateTime.now().toString(),
            // 레거시 필드: 더 이상 사용하지 않지만 제거 불가
            "legacy_flag" to "Y",
            "old_system_ref" to "LEGACY-${System.currentTimeMillis()}"
        )
        println("[레거시] 주문 생성: $orderId, 고객: $customerName")
        return orderId
    }

    fun getOrder(orderId: String): Map<String, Any>? {
        println("[레거시] 주문 조회: $orderId")
        return orders[orderId]
    }

    fun processPayment(orderId: String): Boolean {
        val order = orders[orderId] ?: return false
        // 레거시 결제 로직 (복잡하고 이해하기 어려운 코드)
        val price = order["price"] as? Double ?: return false

        // 레거시 결제 게이트웨이 호출 시뮬레이션
        println("[레거시] 결제 처리: $orderId, 금액: $price")
        orders[orderId] = order + ("status" to "PAID")
        return true
    }

    fun shipOrder(orderId: String): Boolean {
        val order = orders[orderId] ?: return false
        if (order["status"] != "PAID") return false

        // 레거시 배송 로직
        println("[레거시] 배송 처리: $orderId")
        orders[orderId] = order + ("status" to "SHIPPED")
        return true
    }

    fun getOrderStatus(orderId: String): String {
        return orders[orderId]?.get("status") as? String ?: "UNKNOWN"
    }

    fun generateReport(): String {
        // 레거시 리포트 (문자열 조합)
        val sb = StringBuilder()
        sb.appendLine("=== 레거시 주문 리포트 ===")
        orders.forEach { (id, data) ->
            sb.appendLine("$id | ${data["customer"]} | ${data["price"]} | ${data["status"]}")
        }
        return sb.toString()
    }
}

// ========================================
// 빅뱅 마이그레이션 시도 (문제가 있는 접근법)
// ========================================

/**
 * 문제 2: 빅뱅 방식으로 전체 시스템을 한번에 교체하려는 시도
 * - 모든 기능을 동시에 새로 작성해야 함
 * - 개발 기간이 매우 길어짐
 * - 배포 시 높은 리스크
 */
class NewOrderSystem {
    data class Order(
        val id: String,
        val customerId: String,
        val items: List<OrderItem>,
        val totalAmount: Double,
        val status: OrderStatus,
        val createdAt: LocalDateTime
    )

    data class OrderItem(
        val productId: String,
        val quantity: Int,
        val price: Double
    )

    enum class OrderStatus { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED }

    private val orders = mutableMapOf<String, Order>()

    fun createOrder(customerId: String, items: List<OrderItem>): Order {
        val order = Order(
            id = "NEW-${System.currentTimeMillis()}",
            customerId = customerId,
            items = items,
            totalAmount = items.sumOf { it.price * it.quantity },
            status = OrderStatus.CREATED,
            createdAt = LocalDateTime.now()
        )
        orders[order.id] = order
        return order
    }

    // 모든 기능을 한번에 새로 구현해야 함...
    // 결제, 배송, 리포트 등 전체 기능 완성 전까지 배포 불가
}

/**
 * 문제 3: 빅뱅 마이그레이션의 리스크
 * - 새 시스템의 모든 기능이 완성되어야 전환 가능
 * - 전환 실패 시 전체 서비스 중단 위험
 * - 롤백 계획이 복잡하고 불확실
 */
class BigBangMigration {
    private val legacySystem = LegacyOrderSystem()
    private val newSystem = NewOrderSystem()

    fun migrate() {
        println("=== 빅뱅 마이그레이션 시작 ===")
        println("1. 레거시 시스템 중단")
        println("2. 모든 데이터 마이그레이션 (수시간 소요)")
        println("3. 새 시스템 배포")
        println("4. 문제 발생 시... 롤백이 매우 어려움!")
        println()
        println("문제점:")
        println("- 마이그레이션 중 서비스 다운타임 발생")
        println("- 새 시스템에 버그가 있으면 전체 서비스 장애")
        println("- 데이터 마이그레이션 실패 시 복구 불가능할 수 있음")
        println("- 개발팀 전체가 장기간 새 시스템 개발에 집중해야 함")
    }
}

/**
 * 문제 4: 레거시와 신규 시스템 병렬 운영 시 데이터 불일치
 */
class DataInconsistencyProblem {
    private val legacySystem = LegacyOrderSystem()
    private val newSystem = NewOrderSystem()

    fun demonstrateProblem() {
        // 레거시에서 주문 생성
        val legacyOrderId = legacySystem.createOrder("김철수", "상품A x 2", 50000.0)

        // 신규 시스템에는 이 주문이 없음 → 데이터 불일치!
        // 어떤 시스템이 진실의 원천(Source of Truth)인가?
        println("레거시 주문: ${legacySystem.getOrder(legacyOrderId)}")
        println("신규 시스템에는 이 주문이 없음 → 데이터 불일치 발생!")
    }
}

/**
 * 문제점 요약:
 *
 * 1. 빅뱅 마이그레이션의 위험성
 *    - 전체 시스템을 한번에 교체하면 실패 시 복구 어려움
 *    - 장기간 개발 후 한번에 배포하는 것은 매우 위험
 *
 * 2. 레거시 시스템의 제약
 *    - 기술 부채가 누적된 코드를 한번에 버리기 어려움
 *    - 레거시 기능을 완전히 이해하지 못한 상태에서 재작성 위험
 *
 * 3. 병렬 운영의 어려움
 *    - 두 시스템 간 데이터 정합성 유지 어려움
 *    - 어떤 시스템이 진실의 원천인지 불명확
 *
 * 4. 조직적 부담
 *    - 레거시 유지보수 + 신규 개발을 동시에 수행
 *    - 팀 리소스 분산으로 양쪽 모두 품질 저하
 *
 * Strangler Fig Pattern으로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 점진적 마이그레이션 구현을 확인하세요.
 */

fun main() {
    println("=== Strangler Fig Pattern 적용 전 문제점 ===")
    println()

    println("--- 1. 레거시 시스템 동작 ---")
    val legacy = LegacyOrderSystem()
    val orderId = legacy.createOrder("김철수", "노트북 x 1", 1500000.0)
    legacy.processPayment(orderId)
    legacy.shipOrder(orderId)
    println("상태: ${legacy.getOrderStatus(orderId)}")
    println(legacy.generateReport())
    println()

    println("--- 2. 빅뱅 마이그레이션의 위험성 ---")
    val migration = BigBangMigration()
    migration.migrate()
    println()

    println("--- 3. 데이터 불일치 문제 ---")
    val inconsistency = DataInconsistencyProblem()
    inconsistency.demonstrateProblem()
    println()

    println("Solution.kt에서 Strangler Fig Pattern을 적용한 해결책을 확인하세요.")
}
