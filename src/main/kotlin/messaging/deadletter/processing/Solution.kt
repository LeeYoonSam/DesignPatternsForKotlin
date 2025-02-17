package messaging.deadletter.processing

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Dead Letter Queue를 사용하여 실패한 메시지를 보관하고 재처리하는 방법
 */
class Solution {
    data class DeadLetter(
        val message: Problem.Message,
        val error: Throwable,
        val attempts: Int,
        val lastAttempt: Instant = Instant.now()
    )

    interface MessageQueue {
        suspend fun enqueue(message: Problem.Message)
        suspend fun dequeue(): Problem.Message?
    }

    interface DeadLetterQueue {
        suspend fun enqueue(deadLetter: DeadLetter)
        suspend fun dequeue(): DeadLetter?
        suspend fun getAllDeadLetters(): List<DeadLetter>
    }

    class InMemoryMessageQueue : MessageQueue {
        private val queue = Channel<Problem.Message>(Channel.UNLIMITED)

        override suspend fun enqueue(message: Problem.Message) {
            queue.send(message)
        }

        override suspend fun dequeue(): Problem.Message? {
            return queue.tryReceive().getOrNull()
        }
    }

    class InMemoryDeadLetterQueue : DeadLetterQueue {
        private val queue = mutableListOf<DeadLetter>()
        private val mutex = Mutex()

        override suspend fun enqueue(deadLetter: DeadLetter) {
            mutex.withLock {
                queue.add(deadLetter)
            }
        }

        override suspend fun dequeue(): DeadLetter? {
            return mutex.withLock {
                if (queue.isNotEmpty()) queue.removeAt(0) else null
            }
        }

        override suspend fun getAllDeadLetters(): List<DeadLetter> {
            return mutex.withLock { queue.toList() }
        }
    }

    class MessageProcessingSystem(
        private val processor: Problem.MessageProcessor,
        private val messageQueue: MessageQueue,
        private val deadLetterQueue: DeadLetterQueue,
        private val maxRetries: Int = 3,
        private val retryDelay: Duration = 1.seconds
    ) {
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        fun start() {
            scope.launch {
                processMessages()
            }

            scope.launch {
                retryDeadLetters()
            }
        }

        private suspend fun processMessages() {
            while (true) {
                val message = messageQueue.dequeue()
                if (message != null) {
                    processWithRetry(message)
                }
                delay(100)
            }
        }

        private suspend fun processWithRetry(message: Problem.Message, attempts: Int = 0) {
            try {
                processor.processMessage(message)
            } catch (e: Problem.ProcessingException) {
                handleProcessingFailure(message, e, attempts)
            }
        }

        private suspend fun handleProcessingFailure(
            message: Problem.Message,
            error: Throwable,
            attempts: Int
        ) {
            val nextAttempt = attempts + 1
            if (nextAttempt < maxRetries) {
                println("메시지 ${message.id} 재시도 $nextAttempt/$maxRetries")
                delay(retryDelay)
                processWithRetry(message, nextAttempt)
            } else {
                moveToDeadLetter(message, error, nextAttempt)
            }
        }

        private suspend fun moveToDeadLetter(
            message: Problem.Message,
            error: Throwable,
            attempts: Int
        ) {
            val deadLetter = DeadLetter(message, error, attempts)
            deadLetterQueue.enqueue(deadLetter)
            println("메시지 ${message.id}가 Dead Letter Queue로 이동됨 (시도 횟수: $attempts)")
        }

        private suspend fun retryDeadLetters() {
            while (true) {
                delay(5000) // 5초마다 재시도
                val deadLetters = deadLetterQueue.getAllDeadLetters()

                for (deadLetter in deadLetters) {
                    val age = (Instant.now().epochSecond - deadLetter.lastAttempt.epochSecond).seconds

                    if (age > 10.seconds) { // 10 초 이상 된 메시지만 재시도
                        println("Dead Letter 재처리 시도: ${deadLetter.message.id}")
                        messageQueue.enqueue(deadLetter.message)
                    }
                }
            }
        }

        fun stop() {
            scope.cancel()
        }
    }
}

suspend fun main () {
    val messageQueue = Solution.InMemoryMessageQueue()

    val system = Solution.MessageProcessingSystem(
        processor = Problem.MessageProcessor(),
        messageQueue = messageQueue,
        deadLetterQueue = Solution.InMemoryDeadLetterQueue()
    )

    system.start()

    // 테스트 메시지 전송
    repeat(10) { index ->
        val message = Problem.Message(
            id = "msg-$index",
            content = "Message content $index"
        )
        messageQueue.enqueue(message)
        delay(500)
    }

    delay(20000) // 시스템 실행 유지
    system.stop()
}