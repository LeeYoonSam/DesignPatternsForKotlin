package architecture.hexagonal.payment

/**
 * Hexagonal Architecture (Ports & Adapters) Pattern - Solution
 *
 * Hexagonal Architecture는 애플리케이션의 핵심 비즈니스 로직(도메인)을
 * 외부 시스템(UI, DB, 외부 서비스)으로부터 완전히 격리시키는 아키텍처 패턴입니다.
 *
 * 핵심 개념:
 * 1. Domain (Application Core): 순수한 비즈니스 로직
 * 2. Ports: 도메인과 외부 세계 사이의 인터페이스
 *    - Inbound Ports (Primary/Driving): 외부에서 도메인으로 들어오는 인터페이스
 *    - Outbound Ports (Secondary/Driven): 도메인에서 외부로 나가는 인터페이스
 * 3. Adapters: 포트의 구현체
 *    - Driving Adapters: REST Controller, CLI, Message Consumer 등
 *    - Driven Adapters: DB Repository, External API Client 등
 *
 * 장점:
 * - 비즈니스 로직의 완전한 격리
 * - 외부 시스템 교체가 용이
 * - 테스트 용이성 (포트를 통한 Mock 주입)
 * - 다양한 인터페이스 지원 가능
 */

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ========================================
// Domain Layer (Application Core)
// 순수한 비즈니스 로직, 외부 의존성 없음
// ========================================

object Domain {

    // === Value Objects ===

    @JvmInline
    value class PaymentId(val value: String) {
        companion object {
            fun generate(): PaymentId = PaymentId(UUID.randomUUID().toString())
        }
    }

    @JvmInline
    value class OrderId(val value: String)

    @JvmInline
    value class Money(val amount: Double) {
        init {
            require(amount >= 0) { "금액은 0 이상이어야 합니다" }
        }

        operator fun plus(other: Money) = Money(amount + other.amount)
        operator fun minus(other: Money) = Money(amount - other.amount)
        fun isPositive() = amount > 0
    }

    @JvmInline
    value class CardNumber(val value: String) {
        init {
            require(value.length >= 13 && value.length <= 19) {
                "카드 번호는 13-19자리여야 합니다"
            }
            require(value.all { it.isDigit() }) {
                "카드 번호는 숫자만 포함해야 합니다"
            }
        }

        fun masked(): String = "*".repeat(value.length - 4) + value.takeLast(4)
    }

    // === Entities ===

    enum class PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REFUNDED
    }

    data class Payment(
        val id: PaymentId,
        val orderId: OrderId,
        val amount: Money,
        val cardNumber: CardNumber,
        val status: PaymentStatus,
        val createdAt: LocalDateTime,
        val processedAt: LocalDateTime? = null,
        val failureReason: String? = null
    ) {
        fun process(): Payment = copy(
            status = PaymentStatus.PROCESSING
        )

        fun complete(processedAt: LocalDateTime): Payment = copy(
            status = PaymentStatus.COMPLETED,
            processedAt = processedAt
        )

        fun fail(reason: String): Payment = copy(
            status = PaymentStatus.FAILED,
            failureReason = reason
        )

        fun refund(): Payment {
            require(status == PaymentStatus.COMPLETED) {
                "완료된 결제만 환불할 수 있습니다"
            }
            return copy(status = PaymentStatus.REFUNDED)
        }
    }

    data class Order(
        val id: OrderId,
        val totalAmount: Money,
        val isPaid: Boolean = false
    ) {
        fun markAsPaid(): Order = copy(isPaid = true)
    }

    // === Domain Services ===

    class PaymentValidator {
        fun validate(order: Order, amount: Money): ValidationResult {
            val errors = mutableListOf<String>()

            if (order.isPaid) {
                errors.add("이미 결제된 주문입니다")
            }

            if (!amount.isPositive()) {
                errors.add("결제 금액은 0보다 커야 합니다")
            }

            if (amount.amount != order.totalAmount.amount) {
                errors.add("결제 금액이 주문 금액과 일치하지 않습니다")
            }

            return if (errors.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(errors)
            }
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    // === Domain Events ===

    sealed class PaymentEvent {
        abstract val paymentId: PaymentId
        abstract val occurredAt: LocalDateTime

        data class PaymentCreated(
            override val paymentId: PaymentId,
            val orderId: OrderId,
            val amount: Money,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : PaymentEvent()

        data class PaymentCompleted(
            override val paymentId: PaymentId,
            val orderId: OrderId,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : PaymentEvent()

        data class PaymentFailed(
            override val paymentId: PaymentId,
            val reason: String,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : PaymentEvent()

        data class PaymentRefunded(
            override val paymentId: PaymentId,
            override val occurredAt: LocalDateTime = LocalDateTime.now()
        ) : PaymentEvent()
    }
}

// ========================================
// Ports (Interfaces)
// 도메인과 외부 세계 사이의 계약
// ========================================

object Ports {

    // === Inbound Ports (Primary/Driving Ports) ===
    // 외부에서 애플리케이션으로 들어오는 진입점

    /**
     * 결제 처리 유스케이스 포트
     * Driving Adapter(Controller, CLI 등)가 이 포트를 통해 도메인에 접근
     */
    interface ProcessPaymentUseCase {
        fun processPayment(command: ProcessPaymentCommand): PaymentResult
    }

    data class ProcessPaymentCommand(
        val orderId: String,
        val amount: Double,
        val cardNumber: String
    )

    sealed class PaymentResult {
        data class Success(
            val paymentId: String,
            val message: String
        ) : PaymentResult()

        data class Failure(
            val errorCode: String,
            val message: String
        ) : PaymentResult()
    }

    /**
     * 결제 조회 유스케이스 포트
     */
    interface GetPaymentUseCase {
        fun getPayment(paymentId: String): PaymentInfo?
        fun getPaymentsByOrder(orderId: String): List<PaymentInfo>
    }

    data class PaymentInfo(
        val id: String,
        val orderId: String,
        val amount: Double,
        val status: String,
        val maskedCardNumber: String,
        val createdAt: LocalDateTime,
        val processedAt: LocalDateTime?
    )

    /**
     * 환불 유스케이스 포트
     */
    interface RefundPaymentUseCase {
        fun refundPayment(paymentId: String): RefundResult
    }

    sealed class RefundResult {
        data class Success(val message: String) : RefundResult()
        data class Failure(val message: String) : RefundResult()
    }

    // === Outbound Ports (Secondary/Driven Ports) ===
    // 애플리케이션에서 외부 시스템으로 나가는 인터페이스

    /**
     * 결제 저장소 포트
     * 도메인은 이 인터페이스만 알고, 실제 DB는 모름
     */
    interface PaymentRepository {
        fun save(payment: Domain.Payment): Domain.Payment
        fun findById(id: Domain.PaymentId): Domain.Payment?
        fun findByOrderId(orderId: Domain.OrderId): List<Domain.Payment>
    }

    /**
     * 주문 저장소 포트
     */
    interface OrderRepository {
        fun findById(id: Domain.OrderId): Domain.Order?
        fun save(order: Domain.Order): Domain.Order
    }

    /**
     * 결제 게이트웨이 포트
     * 실제 카드사/PG 연동은 어댑터에서 처리
     */
    interface PaymentGateway {
        fun charge(cardNumber: Domain.CardNumber, amount: Domain.Money): GatewayResponse
        fun refund(paymentId: Domain.PaymentId, amount: Domain.Money): GatewayResponse
    }

    sealed class GatewayResponse {
        data class Success(val transactionId: String) : GatewayResponse()
        data class Failure(val errorCode: String, val message: String) : GatewayResponse()
    }

    /**
     * 알림 서비스 포트
     */
    interface NotificationService {
        fun sendPaymentConfirmation(orderId: Domain.OrderId, amount: Domain.Money)
        fun sendPaymentFailure(orderId: Domain.OrderId, reason: String)
        fun sendRefundConfirmation(orderId: Domain.OrderId, amount: Domain.Money)
    }

    /**
     * 이벤트 발행 포트
     */
    interface EventPublisher {
        fun publish(event: Domain.PaymentEvent)
    }
}

// ========================================
// Application Services (Use Case Implementations)
// 포트 구현, 도메인 로직 조율
// ========================================

object Application {

    /**
     * 결제 처리 서비스
     * Inbound Port 구현 + Outbound Port 사용
     */
    class PaymentService(
        private val paymentRepository: Ports.PaymentRepository,
        private val orderRepository: Ports.OrderRepository,
        private val paymentGateway: Ports.PaymentGateway,
        private val notificationService: Ports.NotificationService,
        private val eventPublisher: Ports.EventPublisher,
        private val paymentValidator: Domain.PaymentValidator = Domain.PaymentValidator()
    ) : Ports.ProcessPaymentUseCase, Ports.GetPaymentUseCase, Ports.RefundPaymentUseCase {

        override fun processPayment(command: Ports.ProcessPaymentCommand): Ports.PaymentResult {
            // 1. 입력값을 도메인 객체로 변환
            val orderId = Domain.OrderId(command.orderId)
            val amount = Domain.Money(command.amount)
            val cardNumber = try {
                Domain.CardNumber(command.cardNumber)
            } catch (e: IllegalArgumentException) {
                return Ports.PaymentResult.Failure("INVALID_CARD", e.message ?: "유효하지 않은 카드 번호")
            }

            // 2. 주문 조회
            val order = orderRepository.findById(orderId)
                ?: return Ports.PaymentResult.Failure("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다")

            // 3. 도메인 검증
            when (val validationResult = paymentValidator.validate(order, amount)) {
                is Domain.ValidationResult.Invalid ->
                    return Ports.PaymentResult.Failure("VALIDATION_ERROR", validationResult.errors.joinToString(", "))
                is Domain.ValidationResult.Valid -> { /* 검증 통과 */ }
            }

            // 4. 결제 엔티티 생성
            val payment = Domain.Payment(
                id = Domain.PaymentId.generate(),
                orderId = orderId,
                amount = amount,
                cardNumber = cardNumber,
                status = Domain.PaymentStatus.PENDING,
                createdAt = LocalDateTime.now()
            )

            // 5. 결제 저장 (처리 중 상태)
            val processingPayment = paymentRepository.save(payment.process())

            // 6. 결제 게이트웨이 호출 (Outbound Port 사용)
            return when (val gatewayResponse = paymentGateway.charge(cardNumber, amount)) {
                is Ports.GatewayResponse.Success -> {
                    // 7a. 성공: 결제 완료 처리
                    val completedPayment = processingPayment.complete(LocalDateTime.now())
                    paymentRepository.save(completedPayment)

                    // 8a. 주문 상태 업데이트
                    orderRepository.save(order.markAsPaid())

                    // 9a. 알림 발송
                    notificationService.sendPaymentConfirmation(orderId, amount)

                    // 10a. 이벤트 발행
                    eventPublisher.publish(
                        Domain.PaymentEvent.PaymentCompleted(
                            paymentId = completedPayment.id,
                            orderId = orderId
                        )
                    )

                    Ports.PaymentResult.Success(
                        paymentId = completedPayment.id.value,
                        message = "결제가 완료되었습니다"
                    )
                }
                is Ports.GatewayResponse.Failure -> {
                    // 7b. 실패: 결제 실패 처리
                    val failedPayment = processingPayment.fail(gatewayResponse.message)
                    paymentRepository.save(failedPayment)

                    // 8b. 알림 발송
                    notificationService.sendPaymentFailure(orderId, gatewayResponse.message)

                    // 9b. 이벤트 발행
                    eventPublisher.publish(
                        Domain.PaymentEvent.PaymentFailed(
                            paymentId = failedPayment.id,
                            reason = gatewayResponse.message
                        )
                    )

                    Ports.PaymentResult.Failure(
                        errorCode = gatewayResponse.errorCode,
                        message = gatewayResponse.message
                    )
                }
            }
        }

        override fun getPayment(paymentId: String): Ports.PaymentInfo? {
            val payment = paymentRepository.findById(Domain.PaymentId(paymentId))
                ?: return null

            return payment.toInfo()
        }

        override fun getPaymentsByOrder(orderId: String): List<Ports.PaymentInfo> {
            return paymentRepository.findByOrderId(Domain.OrderId(orderId))
                .map { it.toInfo() }
        }

        override fun refundPayment(paymentId: String): Ports.RefundResult {
            val payment = paymentRepository.findById(Domain.PaymentId(paymentId))
                ?: return Ports.RefundResult.Failure("결제를 찾을 수 없습니다")

            // 도메인 규칙 적용
            val refundedPayment = try {
                payment.refund()
            } catch (e: IllegalArgumentException) {
                return Ports.RefundResult.Failure(e.message ?: "환불할 수 없습니다")
            }

            // 환불 게이트웨이 호출
            when (val response = paymentGateway.refund(payment.id, payment.amount)) {
                is Ports.GatewayResponse.Success -> {
                    paymentRepository.save(refundedPayment)
                    notificationService.sendRefundConfirmation(payment.orderId, payment.amount)
                    eventPublisher.publish(Domain.PaymentEvent.PaymentRefunded(payment.id))
                    return Ports.RefundResult.Success("환불이 완료되었습니다")
                }
                is Ports.GatewayResponse.Failure -> {
                    return Ports.RefundResult.Failure(response.message)
                }
            }
        }

        private fun Domain.Payment.toInfo() = Ports.PaymentInfo(
            id = id.value,
            orderId = orderId.value,
            amount = amount.amount,
            status = status.name,
            maskedCardNumber = cardNumber.masked(),
            createdAt = createdAt,
            processedAt = processedAt
        )
    }
}

// ========================================
// Adapters (Infrastructure Layer)
// 포트의 실제 구현체
// ========================================

object Adapters {

    // === Driving Adapters (Primary Adapters) ===
    // 외부에서 애플리케이션으로 요청을 전달

    /**
     * REST API Adapter
     * HTTP 요청을 UseCase 호출로 변환
     */
    class RestPaymentController(
        private val processPaymentUseCase: Ports.ProcessPaymentUseCase,
        private val getPaymentUseCase: Ports.GetPaymentUseCase,
        private val refundPaymentUseCase: Ports.RefundPaymentUseCase
    ) {
        // POST /payments
        fun createPayment(request: CreatePaymentRequest): ApiResponse {
            val command = Ports.ProcessPaymentCommand(
                orderId = request.orderId,
                amount = request.amount,
                cardNumber = request.cardNumber
            )

            return when (val result = processPaymentUseCase.processPayment(command)) {
                is Ports.PaymentResult.Success -> ApiResponse(
                    status = 201,
                    body = mapOf(
                        "success" to true,
                        "paymentId" to result.paymentId,
                        "message" to result.message
                    )
                )
                is Ports.PaymentResult.Failure -> ApiResponse(
                    status = 400,
                    body = mapOf(
                        "success" to false,
                        "errorCode" to result.errorCode,
                        "message" to result.message
                    )
                )
            }
        }

        // GET /payments/{id}
        fun getPayment(paymentId: String): ApiResponse {
            val payment = getPaymentUseCase.getPayment(paymentId)
                ?: return ApiResponse(status = 404, body = mapOf("message" to "결제를 찾을 수 없습니다"))

            return ApiResponse(
                status = 200,
                body = mapOf(
                    "id" to payment.id,
                    "orderId" to payment.orderId,
                    "amount" to payment.amount,
                    "status" to payment.status,
                    "cardNumber" to payment.maskedCardNumber
                )
            )
        }

        // POST /payments/{id}/refund
        fun refundPayment(paymentId: String): ApiResponse {
            return when (val result = refundPaymentUseCase.refundPayment(paymentId)) {
                is Ports.RefundResult.Success -> ApiResponse(
                    status = 200,
                    body = mapOf("success" to true, "message" to result.message)
                )
                is Ports.RefundResult.Failure -> ApiResponse(
                    status = 400,
                    body = mapOf("success" to false, "message" to result.message)
                )
            }
        }

        data class CreatePaymentRequest(
            val orderId: String,
            val amount: Double,
            val cardNumber: String
        )

        data class ApiResponse(
            val status: Int,
            val body: Map<String, Any>
        )
    }

    /**
     * CLI Adapter
     * 명령줄 인터페이스에서 UseCase 호출
     */
    class CliPaymentAdapter(
        private val processPaymentUseCase: Ports.ProcessPaymentUseCase,
        private val getPaymentUseCase: Ports.GetPaymentUseCase
    ) {
        fun processCommand(args: Array<String>): String {
            if (args.isEmpty()) {
                return """
                    사용법:
                    - pay <orderId> <amount> <cardNumber>: 결제 처리
                    - get <paymentId>: 결제 조회
                """.trimIndent()
            }

            return when (args[0]) {
                "pay" -> {
                    if (args.size < 4) return "오류: pay <orderId> <amount> <cardNumber>"

                    val command = Ports.ProcessPaymentCommand(
                        orderId = args[1],
                        amount = args[2].toDoubleOrNull() ?: return "오류: 유효하지 않은 금액",
                        cardNumber = args[3]
                    )

                    when (val result = processPaymentUseCase.processPayment(command)) {
                        is Ports.PaymentResult.Success -> "결제 성공! ID: ${result.paymentId}"
                        is Ports.PaymentResult.Failure -> "결제 실패: ${result.message}"
                    }
                }
                "get" -> {
                    if (args.size < 2) return "오류: get <paymentId>"

                    val payment = getPaymentUseCase.getPayment(args[1])
                        ?: return "결제를 찾을 수 없습니다"

                    """
                        결제 정보:
                        - ID: ${payment.id}
                        - 주문: ${payment.orderId}
                        - 금액: ${payment.amount}
                        - 상태: ${payment.status}
                        - 카드: ${payment.maskedCardNumber}
                    """.trimIndent()
                }
                else -> "알 수 없는 명령: ${args[0]}"
            }
        }
    }

    /**
     * Message Queue Adapter
     * 메시지 큐에서 결제 요청 처리
     */
    class MessageQueuePaymentAdapter(
        private val processPaymentUseCase: Ports.ProcessPaymentUseCase
    ) {
        fun handleMessage(message: PaymentMessage) {
            println("[MQ] 결제 요청 수신: ${message.orderId}")

            val command = Ports.ProcessPaymentCommand(
                orderId = message.orderId,
                amount = message.amount,
                cardNumber = message.cardNumber
            )

            when (val result = processPaymentUseCase.processPayment(command)) {
                is Ports.PaymentResult.Success ->
                    println("[MQ] 결제 처리 완료: ${result.paymentId}")
                is Ports.PaymentResult.Failure ->
                    println("[MQ] 결제 처리 실패: ${result.message}")
            }
        }

        data class PaymentMessage(
            val orderId: String,
            val amount: Double,
            val cardNumber: String
        )
    }

    // === Driven Adapters (Secondary Adapters) ===
    // 애플리케이션에서 외부 시스템으로 요청

    /**
     * In-Memory Payment Repository
     * 테스트 및 개발용
     */
    class InMemoryPaymentRepository : Ports.PaymentRepository {
        private val payments = ConcurrentHashMap<String, Domain.Payment>()

        override fun save(payment: Domain.Payment): Domain.Payment {
            payments[payment.id.value] = payment
            return payment
        }

        override fun findById(id: Domain.PaymentId): Domain.Payment? {
            return payments[id.value]
        }

        override fun findByOrderId(orderId: Domain.OrderId): List<Domain.Payment> {
            return payments.values.filter { it.orderId == orderId }
        }
    }

    /**
     * In-Memory Order Repository
     */
    class InMemoryOrderRepository : Ports.OrderRepository {
        private val orders = ConcurrentHashMap<String, Domain.Order>()

        override fun findById(id: Domain.OrderId): Domain.Order? {
            return orders[id.value]
        }

        override fun save(order: Domain.Order): Domain.Order {
            orders[order.id.value] = order
            return order
        }

        fun addOrder(order: Domain.Order) {
            orders[order.id.value] = order
        }
    }

    /**
     * Fake Payment Gateway
     * 테스트용 가짜 결제 게이트웨이
     */
    class FakePaymentGateway : Ports.PaymentGateway {
        var shouldFail = false
        var failureMessage = "결제 거부됨"

        override fun charge(cardNumber: Domain.CardNumber, amount: Domain.Money): Ports.GatewayResponse {
            println("[Gateway] 결제 요청: ${cardNumber.masked()}, ${amount.amount}원")

            return if (shouldFail) {
                Ports.GatewayResponse.Failure("DECLINED", failureMessage)
            } else {
                Ports.GatewayResponse.Success(UUID.randomUUID().toString())
            }
        }

        override fun refund(paymentId: Domain.PaymentId, amount: Domain.Money): Ports.GatewayResponse {
            println("[Gateway] 환불 요청: ${paymentId.value}, ${amount.amount}원")
            return Ports.GatewayResponse.Success(UUID.randomUUID().toString())
        }
    }

    /**
     * Stripe Payment Gateway Adapter
     * 실제 Stripe API 연동 (예시)
     */
    class StripePaymentGateway(private val apiKey: String) : Ports.PaymentGateway {
        override fun charge(cardNumber: Domain.CardNumber, amount: Domain.Money): Ports.GatewayResponse {
            // 실제로는 Stripe SDK를 사용하여 API 호출
            println("[Stripe] 결제 요청 - API Key: ${apiKey.take(4)}..., 금액: ${amount.amount}")

            // Stripe API 호출 시뮬레이션
            return try {
                // val charge = Stripe.charges.create(...)
                Ports.GatewayResponse.Success("ch_${UUID.randomUUID().toString().take(8)}")
            } catch (e: Exception) {
                Ports.GatewayResponse.Failure("STRIPE_ERROR", e.message ?: "Unknown error")
            }
        }

        override fun refund(paymentId: Domain.PaymentId, amount: Domain.Money): Ports.GatewayResponse {
            println("[Stripe] 환불 요청 - ${paymentId.value}")
            return Ports.GatewayResponse.Success("re_${UUID.randomUUID().toString().take(8)}")
        }
    }

    /**
     * Console Notification Service
     * 개발/테스트용 콘솔 출력
     */
    class ConsoleNotificationService : Ports.NotificationService {
        override fun sendPaymentConfirmation(orderId: Domain.OrderId, amount: Domain.Money) {
            println("[알림] 결제 완료 - 주문: ${orderId.value}, 금액: ${amount.amount}원")
        }

        override fun sendPaymentFailure(orderId: Domain.OrderId, reason: String) {
            println("[알림] 결제 실패 - 주문: ${orderId.value}, 사유: $reason")
        }

        override fun sendRefundConfirmation(orderId: Domain.OrderId, amount: Domain.Money) {
            println("[알림] 환불 완료 - 주문: ${orderId.value}, 금액: ${amount.amount}원")
        }
    }

    /**
     * Email Notification Service
     * 실제 이메일 발송 (예시)
     */
    class EmailNotificationService(private val smtpHost: String) : Ports.NotificationService {
        override fun sendPaymentConfirmation(orderId: Domain.OrderId, amount: Domain.Money) {
            println("[Email via $smtpHost] 결제 완료 메일 발송 - ${orderId.value}")
            // 실제 이메일 발송 로직
        }

        override fun sendPaymentFailure(orderId: Domain.OrderId, reason: String) {
            println("[Email via $smtpHost] 결제 실패 메일 발송 - ${orderId.value}")
        }

        override fun sendRefundConfirmation(orderId: Domain.OrderId, amount: Domain.Money) {
            println("[Email via $smtpHost] 환불 완료 메일 발송 - ${orderId.value}")
        }
    }

    /**
     * Console Event Publisher
     */
    class ConsoleEventPublisher : Ports.EventPublisher {
        override fun publish(event: Domain.PaymentEvent) {
            println("[이벤트] ${event::class.simpleName}: ${event.paymentId.value}")
        }
    }

    /**
     * Kafka Event Publisher
     * 실제 Kafka 연동 (예시)
     */
    class KafkaEventPublisher(private val bootstrapServers: String) : Ports.EventPublisher {
        override fun publish(event: Domain.PaymentEvent) {
            println("[Kafka -> $bootstrapServers] 이벤트 발행: ${event::class.simpleName}")
            // 실제 Kafka producer 로직
        }
    }
}

// ========================================
// Dependency Injection / Configuration
// ========================================

object Configuration {

    /**
     * 개발 환경 설정
     */
    fun createDevelopmentConfig(): AppConfig {
        val paymentRepository = Adapters.InMemoryPaymentRepository()
        val orderRepository = Adapters.InMemoryOrderRepository()
        val paymentGateway = Adapters.FakePaymentGateway()
        val notificationService = Adapters.ConsoleNotificationService()
        val eventPublisher = Adapters.ConsoleEventPublisher()

        val paymentService = Application.PaymentService(
            paymentRepository = paymentRepository,
            orderRepository = orderRepository,
            paymentGateway = paymentGateway,
            notificationService = notificationService,
            eventPublisher = eventPublisher
        )

        return AppConfig(
            paymentService = paymentService,
            orderRepository = orderRepository,
            paymentGateway = paymentGateway
        )
    }

    /**
     * 프로덕션 환경 설정
     */
    fun createProductionConfig(): AppConfig {
        // 실제 구현체들로 교체
        val paymentRepository = Adapters.InMemoryPaymentRepository() // 실제로는 JpaPaymentRepository 등
        val orderRepository = Adapters.InMemoryOrderRepository()
        val paymentGateway = Adapters.StripePaymentGateway("sk_live_xxx")
        val notificationService = Adapters.EmailNotificationService("smtp.example.com")
        val eventPublisher = Adapters.KafkaEventPublisher("kafka:9092")

        val paymentService = Application.PaymentService(
            paymentRepository = paymentRepository,
            orderRepository = orderRepository,
            paymentGateway = paymentGateway,
            notificationService = notificationService,
            eventPublisher = eventPublisher
        )

        return AppConfig(
            paymentService = paymentService,
            orderRepository = orderRepository,
            paymentGateway = paymentGateway
        )
    }

    data class AppConfig(
        val paymentService: Application.PaymentService,
        val orderRepository: Adapters.InMemoryOrderRepository,
        val paymentGateway: Ports.PaymentGateway
    )
}

// ========================================
// Testing (쉬운 테스트를 위한 예시)
// ========================================

object Testing {

    /**
     * 포트를 Mock으로 대체하여 쉽게 테스트
     */
    class PaymentServiceTest {

        fun testSuccessfulPayment() {
            // Given: Mock 어댑터 설정
            val paymentRepository = Adapters.InMemoryPaymentRepository()
            val orderRepository = Adapters.InMemoryOrderRepository()
            val paymentGateway = Adapters.FakePaymentGateway()
            val notificationService = Adapters.ConsoleNotificationService()
            val eventPublisher = Adapters.ConsoleEventPublisher()

            // 테스트 주문 생성
            orderRepository.addOrder(
                Domain.Order(
                    id = Domain.OrderId("order-123"),
                    totalAmount = Domain.Money(50000.0)
                )
            )

            val paymentService = Application.PaymentService(
                paymentRepository, orderRepository, paymentGateway, notificationService, eventPublisher
            )

            // When: 결제 처리
            val result = paymentService.processPayment(
                Ports.ProcessPaymentCommand(
                    orderId = "order-123",
                    amount = 50000.0,
                    cardNumber = "4111111111111111"
                )
            )

            // Then: 검증
            when (result) {
                is Ports.PaymentResult.Success -> {
                    println("✅ 테스트 통과: 결제 성공")
                    assert(result.paymentId.isNotEmpty())
                }
                is Ports.PaymentResult.Failure -> {
                    println("❌ 테스트 실패: ${result.message}")
                }
            }
        }

        fun testPaymentGatewayFailure() {
            // Given: 실패하도록 설정된 Gateway
            val paymentGateway = Adapters.FakePaymentGateway().apply {
                shouldFail = true
                failureMessage = "카드 한도 초과"
            }

            val orderRepository = Adapters.InMemoryOrderRepository()
            orderRepository.addOrder(
                Domain.Order(
                    id = Domain.OrderId("order-456"),
                    totalAmount = Domain.Money(1000000.0)
                )
            )

            val paymentService = Application.PaymentService(
                paymentRepository = Adapters.InMemoryPaymentRepository(),
                orderRepository = orderRepository,
                paymentGateway = paymentGateway,
                notificationService = Adapters.ConsoleNotificationService(),
                eventPublisher = Adapters.ConsoleEventPublisher()
            )

            // When
            val result = paymentService.processPayment(
                Ports.ProcessPaymentCommand(
                    orderId = "order-456",
                    amount = 1000000.0,
                    cardNumber = "4111111111111111"
                )
            )

            // Then
            when (result) {
                is Ports.PaymentResult.Failure -> {
                    println("✅ 테스트 통과: 예상대로 결제 실패 - ${result.message}")
                }
                is Ports.PaymentResult.Success -> {
                    println("❌ 테스트 실패: 결제가 성공하면 안됨")
                }
            }
        }
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║     Hexagonal Architecture (Ports & Adapters) Pattern        ║")
    println("║              결제 처리 시스템 데모                              ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    // 개발 환경 설정
    val config = Configuration.createDevelopmentConfig()

    // 테스트 주문 생성
    config.orderRepository.addOrder(
        Domain.Order(
            id = Domain.OrderId("order-001"),
            totalAmount = Domain.Money(50000.0)
        )
    )
    config.orderRepository.addOrder(
        Domain.Order(
            id = Domain.OrderId("order-002"),
            totalAmount = Domain.Money(100000.0)
        )
    )

    println("=== 1. REST API Adapter 사용 ===")
    val restController = Adapters.RestPaymentController(
        processPaymentUseCase = config.paymentService,
        getPaymentUseCase = config.paymentService,
        refundPaymentUseCase = config.paymentService
    )

    val createResponse = restController.createPayment(
        Adapters.RestPaymentController.CreatePaymentRequest(
            orderId = "order-001",
            amount = 50000.0,
            cardNumber = "4111111111111111"
        )
    )
    println("POST /payments 응답: $createResponse")
    println()

    // 결제 ID 추출
    val paymentId = (createResponse.body["paymentId"] as? String) ?: ""

    if (paymentId.isNotEmpty()) {
        val getResponse = restController.getPayment(paymentId)
        println("GET /payments/$paymentId 응답: $getResponse")
        println()
    }

    println("=== 2. CLI Adapter 사용 ===")
    val cliAdapter = Adapters.CliPaymentAdapter(
        processPaymentUseCase = config.paymentService,
        getPaymentUseCase = config.paymentService
    )

    println(cliAdapter.processCommand(arrayOf("pay", "order-002", "100000.0", "5500000000000004")))
    println()

    println("=== 3. Message Queue Adapter 사용 ===")
    val mqAdapter = Adapters.MessageQueuePaymentAdapter(
        processPaymentUseCase = config.paymentService
    )

    // 이미 결제된 주문에 대한 중복 결제 시도
    mqAdapter.handleMessage(
        Adapters.MessageQueuePaymentAdapter.PaymentMessage(
            orderId = "order-001",
            amount = 50000.0,
            cardNumber = "4111111111111111"
        )
    )
    println()

    println("=== 4. 환불 처리 ===")
    if (paymentId.isNotEmpty()) {
        val refundResponse = restController.refundPayment(paymentId)
        println("POST /payments/$paymentId/refund 응답: $refundResponse")
    }
    println()

    println("=== 5. 단위 테스트 실행 ===")
    val test = Testing.PaymentServiceTest()
    test.testSuccessfulPayment()
    println()
    test.testPaymentGatewayFailure()
    println()

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                    Hexagonal Architecture 장점                ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 비즈니스 로직(Domain)이 외부 시스템과 완전히 분리           ║")
    println("║ 2. 다양한 Driving Adapter로 동일 로직 노출 (REST, CLI, MQ)   ║")
    println("║ 3. Driven Adapter 교체가 용이 (DB, Gateway 변경)             ║")
    println("║ 4. 포트 인터페이스를 통해 테스트가 매우 쉬움                    ║")
    println("║ 5. 기술 변경이 비즈니스 로직에 영향을 주지 않음                 ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
