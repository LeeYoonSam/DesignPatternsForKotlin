package architecture.stranglerfig.migration

/**
 * Strangler Fig Pattern - Solution
 *
 * Strangler Fig Pattern은 레거시 시스템을 점진적으로 새로운 시스템으로 교체하는 전략입니다.
 * 열대의 교살 무화과(Strangler Fig) 나무가 숙주 나무를 서서히 감싸며 대체하는 것에서 유래했습니다.
 *
 * 핵심 전략:
 * 1. Intercept: 레거시로 향하는 요청을 가로챔
 * 2. Route: 기능별로 레거시 또는 신규 시스템으로 라우팅
 * 3. Migrate: 기능을 하나씩 신규 시스템으로 이전
 * 4. Retire: 모든 기능 이전 완료 후 레거시 제거
 *
 * 예시: 주문 관리 시스템의 점진적 마이그레이션
 */

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ========================================
// Phase 0: 레거시 시스템 (기존 코드)
// ========================================

object Legacy {

    class LegacyOrderService {
        private val orders = ConcurrentHashMap<String, MutableMap<String, Any>>()
        private var orderSeq = 1000

        fun createOrder(customerName: String, items: String, totalPrice: Double): String {
            val orderId = "ORD-${orderSeq++}"
            orders[orderId] = mutableMapOf(
                "id" to orderId,
                "customer" to customerName,
                "items" to items,
                "price" to totalPrice,
                "status" to "NEW",
                "created" to LocalDateTime.now().toString()
            )
            println("  [레거시] 주문 생성: $orderId")
            return orderId
        }

        fun getOrder(orderId: String): Map<String, Any>? {
            println("  [레거시] 주문 조회: $orderId")
            return orders[orderId]
        }

        fun processPayment(orderId: String): Boolean {
            val order = orders[orderId] ?: return false
            println("  [레거시] 결제 처리: $orderId")
            order["status"] = "PAID"
            return true
        }

        fun shipOrder(orderId: String): Boolean {
            val order = orders[orderId] ?: return false
            if (order["status"] != "PAID") return false
            println("  [레거시] 배송 처리: $orderId")
            order["status"] = "SHIPPED"
            return true
        }

        fun getStatus(orderId: String): String {
            return orders[orderId]?.get("status") as? String ?: "UNKNOWN"
        }

        fun generateReport(): List<Map<String, Any>> {
            println("  [레거시] 리포트 생성")
            return orders.values.toList()
        }
    }
}

// ========================================
// Phase 1: 새로운 도메인 모델 정의
// ========================================

object NewDomain {

    data class OrderId(val value: String) {
        companion object {
            fun generate() = OrderId("NEW-${UUID.randomUUID().toString().take(8)}")
        }
    }

    data class CustomerId(val value: String)

    data class OrderItem(
        val productName: String,
        val quantity: Int,
        val unitPrice: Double
    ) {
        val totalPrice: Double get() = quantity * unitPrice
    }

    enum class OrderStatus {
        CREATED, PAID, SHIPPED, DELIVERED, CANCELLED;

        companion object {
            fun fromLegacy(legacyStatus: String): OrderStatus = when (legacyStatus) {
                "NEW" -> CREATED
                "PAID" -> PAID
                "SHIPPED" -> SHIPPED
                "DELIVERED" -> DELIVERED
                "CANCELLED" -> CANCELLED
                else -> CREATED
            }
        }
    }

    data class Order(
        val id: OrderId,
        val customerId: CustomerId,
        val items: List<OrderItem>,
        val totalAmount: Double,
        val status: OrderStatus,
        val createdAt: LocalDateTime
    ) {
        fun pay(): Order = copy(status = OrderStatus.PAID)
        fun ship(): Order {
            require(status == OrderStatus.PAID) { "결제 완료된 주문만 배송 가능" }
            return copy(status = OrderStatus.SHIPPED)
        }

        companion object {
            /**
             * 레거시 데이터를 새 도메인 모델로 변환 (Anti-Corruption Layer)
             */
            fun fromLegacy(legacyData: Map<String, Any>): Order {
                return Order(
                    id = OrderId(legacyData["id"] as String),
                    customerId = CustomerId(legacyData["customer"] as String),
                    items = parseItems(legacyData["items"] as? String ?: ""),
                    totalAmount = legacyData["price"] as? Double ?: 0.0,
                    status = OrderStatus.fromLegacy(legacyData["status"] as? String ?: "NEW"),
                    createdAt = LocalDateTime.parse(
                        legacyData["created"] as? String ?: LocalDateTime.now().toString()
                    )
                )
            }

            private fun parseItems(itemsStr: String): List<OrderItem> {
                if (itemsStr.isBlank()) return emptyList()
                return itemsStr.split(",").map { item ->
                    val parts = item.trim().split(" x ")
                    OrderItem(
                        productName = parts.getOrElse(0) { "Unknown" }.trim(),
                        quantity = parts.getOrElse(1) { "1" }.trim().toIntOrNull() ?: 1,
                        unitPrice = 0.0 // 레거시에는 개별 가격 없음
                    )
                }
            }
        }
    }
}

// ========================================
// Phase 2: 새로운 서비스 구현 (기능별)
// ========================================

object NewServices {

    /**
     * 새로운 주문 생성 서비스
     */
    class OrderCreationService {
        private val orders = ConcurrentHashMap<String, NewDomain.Order>()

        fun createOrder(
            customerId: String,
            items: List<NewDomain.OrderItem>
        ): NewDomain.Order {
            val order = NewDomain.Order(
                id = NewDomain.OrderId.generate(),
                customerId = NewDomain.CustomerId(customerId),
                items = items,
                totalAmount = items.sumOf { it.totalPrice },
                status = NewDomain.OrderStatus.CREATED,
                createdAt = LocalDateTime.now()
            )
            orders[order.id.value] = order
            println("  [신규] 주문 생성: ${order.id.value}, 고객: $customerId")
            return order
        }

        fun getOrder(orderId: String): NewDomain.Order? {
            println("  [신규] 주문 조회: $orderId")
            return orders[orderId]
        }

        fun updateOrder(order: NewDomain.Order) {
            orders[order.id.value] = order
        }

        fun getAllOrders(): List<NewDomain.Order> = orders.values.toList()
    }

    /**
     * 새로운 결제 서비스
     */
    class PaymentService {
        private val orderService: OrderCreationService

        constructor(orderService: OrderCreationService) {
            this.orderService = orderService
        }

        fun processPayment(orderId: String): Boolean {
            val order = orderService.getOrder(orderId)
                ?: return false

            println("  [신규] 결제 처리: $orderId, 금액: ${order.totalAmount}")
            orderService.updateOrder(order.pay())
            return true
        }
    }

    /**
     * 새로운 배송 서비스
     */
    class ShippingService(private val orderService: OrderCreationService) {
        fun shipOrder(orderId: String): Boolean {
            val order = orderService.getOrder(orderId)
                ?: return false

            return try {
                val shipped = order.ship()
                println("  [신규] 배송 처리: $orderId")
                orderService.updateOrder(shipped)
                true
            } catch (e: IllegalArgumentException) {
                println("  [신규] 배송 실패: ${e.message}")
                false
            }
        }
    }

    /**
     * 새로운 리포트 서비스
     */
    class ReportService(private val orderService: OrderCreationService) {
        fun generateReport(): List<Map<String, Any>> {
            println("  [신규] 리포트 생성")
            return orderService.getAllOrders().map { order ->
                mapOf(
                    "id" to order.id.value,
                    "customer" to order.customerId.value,
                    "amount" to order.totalAmount,
                    "status" to order.status.name,
                    "items" to order.items.size,
                    "created" to order.createdAt.toString()
                )
            }
        }
    }
}

// ========================================
// Strangler Fig: Facade + Router
// 레거시와 신규 시스템 사이의 라우팅 레이어
// ========================================

/**
 * 기능별 마이그레이션 상태를 관리하는 Feature Flag
 */
enum class MigrationFeature {
    ORDER_CREATION,
    ORDER_QUERY,
    PAYMENT,
    SHIPPING,
    REPORTING
}

class MigrationConfig {
    private val migratedFeatures = mutableSetOf<MigrationFeature>()

    fun enableFeature(feature: MigrationFeature) {
        migratedFeatures.add(feature)
        println("[마이그레이션] 기능 활성화: ${feature.name} → 신규 시스템으로 전환")
    }

    fun disableFeature(feature: MigrationFeature) {
        migratedFeatures.remove(feature)
        println("[마이그레이션] 기능 비활성화: ${feature.name} → 레거시로 롤백")
    }

    fun isMigrated(feature: MigrationFeature): Boolean = feature in migratedFeatures

    fun getMigrationStatus(): Map<MigrationFeature, Boolean> {
        return MigrationFeature.entries.associateWith { it in migratedFeatures }
    }
}

/**
 * Strangler Fig Facade
 *
 * 클라이언트는 이 Facade만 사용하며,
 * 내부적으로 마이그레이션 상태에 따라 레거시/신규 시스템으로 라우팅합니다.
 */
class OrderSystemFacade(
    private val legacyService: Legacy.LegacyOrderService,
    private val newOrderService: NewServices.OrderCreationService,
    private val newPaymentService: NewServices.PaymentService,
    private val newShippingService: NewServices.ShippingService,
    private val newReportService: NewServices.ReportService,
    private val migrationConfig: MigrationConfig
) {
    /**
     * 주문 생성: 마이그레이션 상태에 따라 라우팅
     */
    fun createOrder(customerName: String, items: String, totalPrice: Double): String {
        return if (migrationConfig.isMigrated(MigrationFeature.ORDER_CREATION)) {
            // 신규 시스템으로 라우팅
            val orderItems = parseItemsForNew(items, totalPrice)
            val order = newOrderService.createOrder(customerName, orderItems)
            order.id.value
        } else {
            // 레거시 시스템으로 라우팅
            legacyService.createOrder(customerName, items, totalPrice)
        }
    }

    /**
     * 주문 조회: Anti-Corruption Layer로 응답 통일
     */
    fun getOrder(orderId: String): OrderResponse? {
        return if (migrationConfig.isMigrated(MigrationFeature.ORDER_QUERY)) {
            // 신규 시스템에서 조회
            newOrderService.getOrder(orderId)?.let { order ->
                OrderResponse(
                    id = order.id.value,
                    customer = order.customerId.value,
                    totalAmount = order.totalAmount,
                    status = order.status.name,
                    createdAt = order.createdAt
                )
            }
        } else {
            // 레거시에서 조회 후 통합 포맷으로 변환
            legacyService.getOrder(orderId)?.let { data ->
                OrderResponse(
                    id = data["id"] as String,
                    customer = data["customer"] as String,
                    totalAmount = data["price"] as Double,
                    status = data["status"] as String,
                    createdAt = LocalDateTime.parse(data["created"] as String)
                )
            }
        }
    }

    /**
     * 결제 처리
     */
    fun processPayment(orderId: String): Boolean {
        return if (migrationConfig.isMigrated(MigrationFeature.PAYMENT)) {
            newPaymentService.processPayment(orderId)
        } else {
            legacyService.processPayment(orderId)
        }
    }

    /**
     * 배송 처리
     */
    fun shipOrder(orderId: String): Boolean {
        return if (migrationConfig.isMigrated(MigrationFeature.SHIPPING)) {
            newShippingService.shipOrder(orderId)
        } else {
            legacyService.shipOrder(orderId)
        }
    }

    /**
     * 리포트 생성
     */
    fun generateReport(): List<Map<String, Any>> {
        return if (migrationConfig.isMigrated(MigrationFeature.REPORTING)) {
            newReportService.generateReport()
        } else {
            legacyService.generateReport()
        }
    }

    /**
     * 마이그레이션 상태 조회
     */
    fun getMigrationStatus(): Map<MigrationFeature, Boolean> {
        return migrationConfig.getMigrationStatus()
    }

    private fun parseItemsForNew(items: String, totalPrice: Double): List<NewDomain.OrderItem> {
        return items.split(",").map { item ->
            val parts = item.trim().split(" x ")
            NewDomain.OrderItem(
                productName = parts.getOrElse(0) { "Unknown" }.trim(),
                quantity = parts.getOrElse(1) { "1" }.trim().toIntOrNull() ?: 1,
                unitPrice = totalPrice // 단순화
            )
        }
    }

    /**
     * 통합 응답 포맷 (Anti-Corruption Layer)
     */
    data class OrderResponse(
        val id: String,
        val customer: String,
        val totalAmount: Double,
        val status: String,
        val createdAt: LocalDateTime
    )
}

// ========================================
// Shadow Traffic / Canary 전략
// 안전한 마이그레이션을 위한 검증 도구
// ========================================

/**
 * Shadow Traffic: 레거시 결과와 신규 결과를 비교하여 검증
 */
class ShadowTrafficVerifier(
    private val legacyService: Legacy.LegacyOrderService,
    private val newOrderService: NewServices.OrderCreationService
) {
    data class VerificationResult(
        val feature: String,
        val legacyResult: Any?,
        val newResult: Any?,
        val isConsistent: Boolean,
        val differences: List<String>
    )

    /**
     * 주문 생성을 양쪽 시스템에서 실행하고 결과 비교
     */
    fun verifyOrderCreation(
        customerName: String,
        items: String,
        totalPrice: Double
    ): VerificationResult {
        println("[Shadow] 양쪽 시스템에 동시 요청...")

        // 레거시에서 실행
        val legacyId = legacyService.createOrder(customerName, items, totalPrice)
        val legacyOrder = legacyService.getOrder(legacyId)

        // 신규에서 실행
        val newOrder = newOrderService.createOrder(
            customerName,
            listOf(NewDomain.OrderItem(items, 1, totalPrice))
        )

        // 결과 비교
        val differences = mutableListOf<String>()

        val legacyCustomer = legacyOrder?.get("customer") as? String
        if (legacyCustomer != newOrder.customerId.value) {
            differences.add("고객명: 레거시=$legacyCustomer, 신규=${newOrder.customerId.value}")
        }

        val legacyPrice = legacyOrder?.get("price") as? Double
        if (legacyPrice != newOrder.totalAmount) {
            differences.add("금액: 레거시=$legacyPrice, 신규=${newOrder.totalAmount}")
        }

        val result = VerificationResult(
            feature = "ORDER_CREATION",
            legacyResult = legacyOrder,
            newResult = newOrder,
            isConsistent = differences.isEmpty(),
            differences = differences
        )

        if (result.isConsistent) {
            println("[Shadow] ✅ 결과 일치: 마이그레이션 안전")
        } else {
            println("[Shadow] ⚠️ 결과 불일치:")
            differences.forEach { println("  - $it") }
        }

        return result
    }
}

/**
 * Canary Release: 트래픽 비율 기반 점진적 전환
 */
class CanaryRouter(
    private val migrationConfig: MigrationConfig
) {
    private var canaryPercentage: Int = 0

    fun setCanaryPercentage(percentage: Int) {
        require(percentage in 0..100)
        canaryPercentage = percentage
        println("[Canary] 신규 시스템 트래픽 비율: $percentage%")
    }

    /**
     * 요청 비율에 따라 신규/레거시로 라우팅 결정
     */
    fun shouldRouteToNew(): Boolean {
        val random = (1..100).random()
        val routeToNew = random <= canaryPercentage
        println("[Canary] 요청 라우팅: ${if (routeToNew) "신규 시스템" else "레거시 시스템"} (random=$random, threshold=$canaryPercentage%)")
        return routeToNew
    }

    /**
     * 에러율 기반 자동 롤백
     */
    fun checkHealthAndAdjust(
        totalRequests: Int,
        errorCount: Int,
        errorThreshold: Double = 0.05
    ) {
        val errorRate = if (totalRequests > 0) errorCount.toDouble() / totalRequests else 0.0

        if (errorRate > errorThreshold) {
            println("[Canary] ⚠️ 에러율 ${String.format("%.1f", errorRate * 100)}% > 임계값 ${String.format("%.1f", errorThreshold * 100)}%")
            println("[Canary] 자동 롤백: 트래픽을 레거시로 복구합니다")
            setCanaryPercentage(0)
        } else {
            println("[Canary] ✅ 에러율 ${String.format("%.1f", errorRate * 100)}%: 정상 범위")
        }
    }
}

// ========================================
// Migration Orchestrator: 전체 마이그레이션 관리
// ========================================

class MigrationOrchestrator(
    private val facade: OrderSystemFacade,
    private val migrationConfig: MigrationConfig,
    private val shadowVerifier: ShadowTrafficVerifier,
    private val canaryRouter: CanaryRouter
) {
    /**
     * 단계별 마이그레이션 실행
     */
    fun executeMigrationPhase(phase: Int) {
        println()
        println("╔══════════════════════════════════════╗")
        println("║  마이그레이션 Phase $phase 실행              ║")
        println("╚══════════════════════════════════════╝")

        when (phase) {
            1 -> phase1_OrderCreation()
            2 -> phase2_Payment()
            3 -> phase3_Shipping()
            4 -> phase4_Reporting()
            5 -> phase5_Complete()
        }

        println()
        printMigrationStatus()
    }

    /**
     * Phase 1: 주문 생성 기능 마이그레이션
     */
    private fun phase1_OrderCreation() {
        println("Phase 1: 주문 생성 → 신규 시스템 전환")
        println()

        // Step 1: Shadow Traffic으로 검증
        println("[Step 1] Shadow Traffic 검증")
        shadowVerifier.verifyOrderCreation("테스트고객", "상품A x 1", 30000.0)
        println()

        // Step 2: Canary 배포 (10% → 50% → 100%)
        println("[Step 2] Canary 배포")
        canaryRouter.setCanaryPercentage(10)
        canaryRouter.checkHealthAndAdjust(totalRequests = 100, errorCount = 1)
        println()
        canaryRouter.setCanaryPercentage(50)
        canaryRouter.checkHealthAndAdjust(totalRequests = 200, errorCount = 3)
        println()

        // Step 3: 전체 전환
        println("[Step 3] 전체 전환")
        migrationConfig.enableFeature(MigrationFeature.ORDER_CREATION)
        migrationConfig.enableFeature(MigrationFeature.ORDER_QUERY)
    }

    /**
     * Phase 2: 결제 기능 마이그레이션
     */
    private fun phase2_Payment() {
        println("Phase 2: 결제 → 신규 시스템 전환")
        migrationConfig.enableFeature(MigrationFeature.PAYMENT)
    }

    /**
     * Phase 3: 배송 기능 마이그레이션
     */
    private fun phase3_Shipping() {
        println("Phase 3: 배송 → 신규 시스템 전환")
        migrationConfig.enableFeature(MigrationFeature.SHIPPING)
    }

    /**
     * Phase 4: 리포트 기능 마이그레이션
     */
    private fun phase4_Reporting() {
        println("Phase 4: 리포트 → 신규 시스템 전환")
        migrationConfig.enableFeature(MigrationFeature.REPORTING)
    }

    /**
     * Phase 5: 레거시 제거
     */
    private fun phase5_Complete() {
        println("Phase 5: 마이그레이션 완료!")
        println("- 모든 기능이 신규 시스템으로 전환됨")
        println("- 레거시 시스템 코드 및 인프라 제거 가능")
        println("- Facade에서 레거시 의존성 제거")
    }

    private fun printMigrationStatus() {
        println("--- 마이그레이션 현황 ---")
        facade.getMigrationStatus().forEach { (feature, migrated) ->
            val icon = if (migrated) "✅" else "⬜"
            val system = if (migrated) "신규" else "레거시"
            println("  $icon ${feature.name}: $system")
        }
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║         Strangler Fig Pattern - 점진적 마이그레이션            ║")
    println("║              주문 관리 시스템 전환 데모                         ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    // === 인프라 설정 ===
    val legacyService = Legacy.LegacyOrderService()
    val newOrderService = NewServices.OrderCreationService()
    val newPaymentService = NewServices.PaymentService(newOrderService)
    val newShippingService = NewServices.ShippingService(newOrderService)
    val newReportService = NewServices.ReportService(newOrderService)
    val migrationConfig = MigrationConfig()

    val facade = OrderSystemFacade(
        legacyService = legacyService,
        newOrderService = newOrderService,
        newPaymentService = newPaymentService,
        newShippingService = newShippingService,
        newReportService = newReportService,
        migrationConfig = migrationConfig
    )

    val shadowVerifier = ShadowTrafficVerifier(legacyService, newOrderService)
    val canaryRouter = CanaryRouter(migrationConfig)
    val orchestrator = MigrationOrchestrator(facade, migrationConfig, shadowVerifier, canaryRouter)

    // === Phase 0: 모든 트래픽이 레거시로 라우팅 ===
    println("=== Phase 0: 모든 요청이 레거시 시스템으로 처리됨 ===")
    println()
    val legacyOrder1 = facade.createOrder("김철수", "노트북 x 1", 1500000.0)
    facade.processPayment(legacyOrder1)
    facade.shipOrder(legacyOrder1)
    val orderInfo = facade.getOrder(legacyOrder1)
    println("주문 정보: $orderInfo")
    println()

    // === Phase 1: 주문 생성 마이그레이션 ===
    orchestrator.executeMigrationPhase(1)
    println()

    // Phase 1 이후: 주문 생성은 신규 시스템, 결제/배송은 레거시
    println("=== Phase 1 이후: 신규 시스템으로 주문 생성 ===")
    val newOrder1 = facade.createOrder("이영희", "키보드 x 2", 200000.0)
    println("생성된 주문 ID: $newOrder1")
    // 결제는 아직 레거시 → 신규 주문은 신규 시스템에만 존재하므로 별도 처리 필요
    println()

    // === Phase 2: 결제 마이그레이션 ===
    orchestrator.executeMigrationPhase(2)
    println()

    println("=== Phase 2 이후: 신규 시스템으로 결제 처리 ===")
    val newOrder2 = facade.createOrder("박지민", "마우스 x 1", 80000.0)
    facade.processPayment(newOrder2)
    println()

    // === Phase 3: 배송 마이그레이션 ===
    orchestrator.executeMigrationPhase(3)
    println()

    println("=== Phase 3 이후: 전체 흐름이 신규 시스템 ===")
    val newOrder3 = facade.createOrder("최동훈", "모니터 x 1", 500000.0)
    facade.processPayment(newOrder3)
    facade.shipOrder(newOrder3)
    val order3Info = facade.getOrder(newOrder3)
    println("주문 정보: $order3Info")
    println()

    // === Phase 4: 리포트 마이그레이션 ===
    orchestrator.executeMigrationPhase(4)
    println()

    println("=== Phase 4 이후: 리포트도 신규 시스템 ===")
    val report = facade.generateReport()
    report.forEach { println("  $it") }
    println()

    // === Phase 5: 마이그레이션 완료 ===
    orchestrator.executeMigrationPhase(5)

    println()
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║               Strangler Fig Pattern 장점                     ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 점진적 전환: 기능별로 안전하게 마이그레이션              ║")
    println("║ 2. 낮은 리스크: 문제 시 해당 기능만 롤백 가능              ║")
    println("║ 3. 검증 가능: Shadow Traffic으로 사전 검증                 ║")
    println("║ 4. 비즈니스 연속성: 서비스 중단 없이 전환                  ║")
    println("║ 5. 팀 효율: 기능별로 분담하여 병렬 작업 가능              ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
