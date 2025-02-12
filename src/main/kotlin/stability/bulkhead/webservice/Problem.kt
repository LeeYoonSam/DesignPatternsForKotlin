package stability.bulkhead.webservice

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 모든 서비스가 공유 리소스를 사용하여 한 서비스의 문제가 다른 서비스에 영향을 미칠 수 있는 상황
 * PaymentService의 실패가 다른 서비스에 영향을 미칠 수 있음
 */
class UserService {
    suspend fun getUserProfile(userId: String): String {
        delay(Random.nextLong(100, 500)) // Simulating network call
        return "User Profile for $userId"
    }
}

class PaymentService {
    suspend fun processPayment(userId: String): String {
        delay(Random.nextLong(1000, 3000)) // Simulating slow payment processing
        if (Random.nextBoolean()) throw RuntimeException("Payment processing failed")
        return "Payment processed for $userId"
    }
}

class NotificationService {
    suspend fun sendNotification(userId: String): String {
        delay(Random.nextLong(200, 800)) // Simulating notification sending
        return "Notification sent to $userId"
    }
}

class WebService(
    private val userService: UserService,
    private val paymentService: PaymentService,
    private val notificationService: NotificationService
) {
    suspend fun processUserRequest(userId: String) {
        // All services share the same thread pool
        coroutineScope {
            val profile = async { userService.getUserProfile(userId) }
            val payment = async { paymentService.processPayment(userId) }
            val notification = async { notificationService.sendNotification(userId) }

            try {
                println("Profile: ${profile.await()}")
                println("Payment: ${payment.await()}")
                println("Notification: ${notification.await()}")
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }
}

suspend fun main() {
    val webService = WebService(UserService(), PaymentService(), NotificationService())

    // Simulate multiple concurrent request
    coroutineScope {
        repeat(10) { userId ->
            launch {
                webService.processUserRequest("user$userId")
            }
        }
    }
}