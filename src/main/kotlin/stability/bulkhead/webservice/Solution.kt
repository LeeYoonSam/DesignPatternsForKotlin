package stability.bulkhead.webservice

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 각 서비스를 격리하여 안정성을 확보하는 방법
 * 각 서비스가 격리되어 있어 한 서비스의 실패가 다른 서비스에 영향을 미치지 않는 것을 확인할 수 있음
 */
class BulkheadContext(
    private val maxParallelism: Int,
    private val name: String
) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BulkheadContext>
    override val key: CoroutineContext.Key<*> = Key

    private val dispatcher = Dispatchers.Default.limitedParallelism(maxParallelism)

    suspend fun <T> execute(block: suspend () -> T): T {
        return withContext(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                throw BulkheadException("Error in bulkhead '$name': ${e.message}")
            }
        }
    }
}

class BulkheadException(message: String) : Exception(message)

class BulkheadUserService(private val userService: UserService) {
    private val bulkhead = BulkheadContext(maxParallelism = 5, name = "UserService")

    suspend fun getUserProfile(userId: String): String {
        return bulkhead.execute { userService.getUserProfile(userId) }
    }
}

class BulkheadPaymentService(private val paymentService: PaymentService) {
    private val bulkhead = BulkheadContext(maxParallelism = 2, name = "PaymentService")

    suspend fun processPayment(userId: String): String {
        return bulkhead.execute { paymentService.processPayment(userId) }
    }
}

class BulkheadNotificationService(private val notificationService: NotificationService) {
    private val bulkhead = BulkheadContext(maxParallelism = 3, name = "NotificationService")

    suspend fun sendNotification(userId: String): String {
        return bulkhead.execute { notificationService.sendNotification(userId) }
    }
}

class ResilientWebService(
    private val userService: BulkheadUserService,
    private val paymentService: BulkheadPaymentService,
    private val notificationService: BulkheadNotificationService,
) {
    suspend fun processUserRequest(userId: String) {
        supervisorScope {
            val profile = async { userService.getUserProfile(userId) }
            val payment = async {
                try {
                    paymentService.processPayment(userId)
                } catch (e: Exception) {
                    "Payment failed: ${e.message}"
                }
            }
            val notification = async { notificationService.sendNotification(userId) }

            println("Profile: ${profile.await()}")
            println("Payment: ${payment.await()}")
            println("Notification: ${notification.await()}")
        }
    }
}

suspend fun main() {
    val resilientWebService = ResilientWebService(
        BulkheadUserService(UserService()),
        BulkheadPaymentService(PaymentService()),
        BulkheadNotificationService(NotificationService()),
    )

    // Simulate multiple concurrent request
    coroutineScope {
        repeat(10) { userId ->
            launch {
                resilientWebService.processUserRequest("user$userId")
            }
        }
    }
}