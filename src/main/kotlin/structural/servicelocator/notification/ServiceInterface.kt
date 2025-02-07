package structural.servicelocator.notification

// 서비스 인터페이스
interface MessageService {
    fun sendMessage(msg: String)
}

interface EmailService {
    fun sendEmail(email: String, content: String)
}

interface LogService {
    fun log(message: String)
}

// 서비스 구현체
class SMSService : MessageService {
    override fun sendMessage(msg: String) {
        println("Sending SMS: $msg")
    }
}

class GmailService : EmailService {
    override fun sendEmail(email: String, content: String) {
        println("Sending email to $email: $content")
    }
}

class FileLogger : LogService {
    override fun log(message: String) {
        println("Logging to file: $message")
    }
}