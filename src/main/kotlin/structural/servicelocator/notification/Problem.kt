package structural.servicelocator.notification

// 비지니스 로직 클래스
class NotificationService {
    // 하드코딩 의존성
    private val messageService = SMSService()
    private val emailService = GmailService()
    private val logService = FileLogger()

    fun notify(user: String, message: String) {
        messageService.sendMessage(message)
        emailService.sendEmail(user, message)
        logService.log("Notification sent to $user")
    }
}

fun main() {
    val notificationService = NotificationService()
    notificationService.notify("user@example.com", "Hello, this is a test notification!")

    // 문제점:
    // 1. 서비스 구현체를 변경하기 어려움
    // 2. 테스트하기 어려움
    // 3. 런타임에 서비스를 변경할 수 없음
}