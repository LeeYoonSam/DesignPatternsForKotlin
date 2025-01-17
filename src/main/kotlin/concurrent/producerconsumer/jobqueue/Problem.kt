package concurrent.producerconsumer.jobqueue

/**
 * 문제점
 * - 스레드 간 데이터 공유의 어려움
 * - 경쟁 상태(Race Condition) 발생 위험
 * - 버퍼 오버플로우/언더플로우 위험
 * - 데드락 발생 가능성
 * - 처리 순서 보장의 어려움
 */
class Problem {
    // 동기화되지 않은 작업 큐
    class JobQueue {
        private val queue = mutableListOf<Job>()

        fun addJob(job: Job) {
            queue.add(job)
        }

        fun getJob(): Job? {
            return if (queue.isNotEmpty()) {
                queue.removeAt(0) // 스레드 안전하지 않음
            } else {
                null
            }
        }
    }

    class Producer(private val queue: JobQueue) {
        fun produce() {
            while (true) {
                val job = Job("Task ${System.currentTimeMillis()}")
                queue.addJob(job)
                println("Produced: ${job.id}")
                Thread.sleep(100)  // 생산 시뮬레이션
            }
        }
    }

    class Consumer(private val queue: JobQueue) {
        fun consume() {
            while (true) {
                val job = queue.getJob()
                if (job != null) {
                    println("Consumed: ${job.id}")
                }
                Thread.sleep(200)  // 소비 시뮬레이션
            }
        }
    }

    data class Job(val id: String)
}

fun main() {
    val jobQueue = Problem.JobQueue()
    val producer = Problem.Producer(jobQueue)
    producer.produce()
    val consumer = Problem.Consumer(jobQueue)
    consumer.consume()
}