package messaging.deadletter.processing

import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.random.Random

class Problem {
    data class Message(
        val id: String,
        val content: String,
        val createdAt: Instant = Instant.now()
    )

    class MessageProcessor {
        suspend fun processMessage(message: Message) {
            delay(100) // 처리 시간 시뮬레이션

            if (Random.nextInt(100) < 60) { // 60% 확률로 실패
                throw ProcessingException("메시지 처리 실패: ${message.id}")
            }

            println("메시지 처리 성공: ${message.id}")
        }
    }

    class SimpleMessageSystem(private val processor: MessageProcessor) {
        suspend fun handleMessage(message: Message) {
            try {
                processor.processMessage(message)
            } catch (e: ProcessingException) {
                println("메시지 처리 실패 (폐기됨): ${e.message}")
            }
        }
    }

    class ProcessingException(message: String) : Exception(message)
}

suspend fun main() {
    val system = Problem.SimpleMessageSystem(Problem.MessageProcessor())

    repeat(10) { index ->
        val message = Problem.Message(
            id = "msg-$index",
            content = "Message content $index"
        )
        system.handleMessage(message)
        delay(200)
    }
}