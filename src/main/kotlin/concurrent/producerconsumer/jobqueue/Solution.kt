package concurrent.producerconsumer.jobqueue

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 해결책: 생산자-소비자 패턴을 사용한 스레드 안전한 작업 큐 시스템
 */
class Solution {
    // 스레드 안전한 작업 큐
    class JobQueue(private val capacity: Int) {
        private val queue: BlockingQueue<Job> = LinkedBlockingQueue(capacity)

        fun addJob(job: Job): Boolean {
            return queue.offer(job, 1, TimeUnit.SECONDS)
        }

        fun getJob(): Job? {
            return queue.poll(1, TimeUnit.SECONDS)
        }

        fun size(): Int = queue.size
    }

    class Producer(
        private val queue: JobQueue,
        private val name: String
    ) : Runnable {
        private var running = true

        override fun run() {
            try {
                while (running) {
                    val job = Job("Task-$name-${System.currentTimeMillis()}")
                    if (queue.addJob(job)) {
                        println("$name produced: ${job.id}")
                    } else {
                        println("$name failed to produce: Queue full")
                    }
                    Thread.sleep(100)  // 생산 시뮬레이션
                }
            } catch (e: InterruptedException) {
                println("$name was interrupted")
            }
        }

        fun stop() {
            running = false
        }
    }

    class Consumer(
        private val queue: JobQueue,
        private val name: String
    ) : Runnable {
        private var running = true

        override fun run() {
            try {
                while (running) {
                    val job = queue.getJob()
                    if (job != null) {
                        println("$name consumed: ${job.id}")
                        processJob(job)
                    }
                }
            } catch (e: InterruptedException) {
                println("$name was interrupted")
            }
        }

        private fun processJob(job: Job) {
            // 작업 처리 시뮬레이션
            Thread.sleep(200)
        }

        fun stop() {
            running = false
        }
    }

    data class Job(val id: String)

    // 작업 처리 시스템
    class JobProcessor(
        private val producerCount: Int,
        private val consumerCount: Int,
        private val queueCapacity: Int
    ) {
        private val queue = JobQueue(queueCapacity)
        private val producers = mutableListOf<Producer>()
        private val consumers = mutableListOf<Consumer>()
        private val executorService = Executors.newFixedThreadPool(producerCount + consumerCount)

        fun start() {
            // 생산자 시작
            repeat(producerCount) { index ->
                val producer = Producer(queue, "Producer-$index")
                producers.add(producer)
                executorService.submit(producer)
            }

            // 소비자 시작
            repeat(consumerCount) { index ->
                val consumer = Consumer(queue, "Consumer-$index")
                consumers.add(consumer)
                executorService.submit(consumer)
            }
        }

        fun stop() {
            producers.forEach { it.stop() }
            consumers.forEach { it.stop() }
            executorService.shutdown()
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        }
    }
}

fun main() {
    println("Starting Job Processing System...")

    val processor = Solution.JobProcessor(
        producerCount = 2,
        consumerCount = 3,
        queueCapacity = 10
    )

    processor.start()

    // 시스템 실행 시뮬레이션
    Thread.sleep(5000)

    println("\nStopping Job Processing System...")
    processor.stop()
}