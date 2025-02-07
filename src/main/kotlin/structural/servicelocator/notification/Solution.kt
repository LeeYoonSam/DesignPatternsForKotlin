package structural.servicelocator.notification

class Solution {
    // Service Locator
    object ServiceLocator {
        val services = mutableMapOf<Class<*>, Any>()

        inline fun <reified T : Any> register(service: T) {
            services[T::class.java] = service
        }

        inline fun <reified T> get(): T {
            return services[T::class.java] as? T
                ?: throw IllegalStateException("Service ${T::class.java.simpleName} not found")
        }

        fun clear() {
            services.clear()
        }
    }

    // 추가 서비스 구현체들 (다른 구현 옵션)
    class PushNotificationService : MessageService {
        override fun sendMessage(msg: String) {
            println("Sending Push Notification: $msg")
        }
    }

    class OutlookService : EmailService {
        override fun sendEmail(email: String, content: String) {
            println("Sending email via Outlook to $email: $content")
        }
    }

    class DatabaseLogger : LogService {
        override fun log(message: String) {
            println("Logging to database: $message")
        }
    }

    // 개선된 비즈니스 로직 클래스
    class NotificationService {
        private val messageService: MessageService by lazy { ServiceLocator.get() }
        private val emailService: EmailService by lazy { ServiceLocator.get() }
        private val logService: LogService by lazy { ServiceLocator.get() }

        fun notify(user: String, message: String) {
            messageService.sendMessage(message)
            emailService.sendEmail(user, message)
            logService.log("Notification sent to $user")
        }
    }
}

fun main() {
    // 서비스 등록
    Solution.ServiceLocator.register<MessageService>(SMSService())
    Solution.ServiceLocator.register<EmailService>(GmailService())
    Solution.ServiceLocator.register<LogService>(FileLogger())

    val notificationService = Solution.NotificationService()
    notificationService.notify("user@example.com", "Hello with default services!")

    // 런타임에 서비스 구현체 변경
    Solution.ServiceLocator.register<MessageService>(Solution.PushNotificationService())
    Solution.ServiceLocator.register<EmailService>(Solution.OutlookService())
    Solution.ServiceLocator.register<LogService>(Solution.DatabaseLogger())

    val newNotificationService = Solution.NotificationService()
    newNotificationService.notify("user@example.com", "Hello with changed services!")

    // 테스트를 위한 목(mock) 서비스
    class MockMessageService : MessageService {
        var messagesSent = mutableListOf<String>()
        override fun sendMessage(msg: String) {
            messagesSent.add(msg)
        }
    }

    // 테스트 설정
    val mockMessageService = MockMessageService()
    Solution.ServiceLocator.register<MessageService>(mockMessageService)

    val testNotificationService = Solution.NotificationService()
    testNotificationService.notify("test@example.com", "Test message")

    println("Messages sent: ${mockMessageService.messagesSent}")
}