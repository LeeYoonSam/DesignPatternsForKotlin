package architecture.gracefulshutdown.server

import java.util.concurrent.CountDownLatch

/**
 * Graceful Shutdown Pattern - 문제점
 *
 * 애플리케이션 종료를 적절히 처리하지 않을 때 발생하는 문제들:
 * 1. 즉시 종료: 진행 중인 요청이 중단됨
 * 2. 리소스 누수: DB 연결, 파일 핸들 정리 안됨
 * 3. 데이터 손실: 버퍼의 데이터가 플러시되지 않음
 * 4. 트랜잭션 실패: 진행 중인 트랜잭션 롤백 불가
 * 5. 메시지 유실: 큐에서 가져온 메시지 ACK 안됨
 * 6. 세션 손실: 사용자 세션 저장 안됨
 * 7. 연쇄 장애: 종속 서비스에 영향
 * 8. 배포 중단: 롤링 배포 시 에러 발생
 */

// ============================================================
// 문제 1: 즉시 종료 - 진행 중 요청 중단
// ============================================================

class ImmediateShutdownProblem {
    // kill -9 또는 System.exit(0)으로 즉시 종료
    class UnsafeServer {
        private var running = true
        private var activeRequests = 0

        fun start() {
            println("[Server] Started")
            running = true
        }

        fun handleRequest(requestId: String) {
            activeRequests++
            println("[Server] Processing request: $requestId")

            // 긴 작업 중...
            Thread.sleep(100) // 시뮬레이션

            // 여기서 kill되면? 요청이 반쪽짜리로 완료!
            // - DB에 절반만 저장
            // - 결제는 됐는데 주문 기록은 안됨
            // - 이메일은 보냈는데 상태 업데이트 안됨

            activeRequests--
            println("[Server] Completed request: $requestId")
        }

        fun shutdown() {
            // 즉시 종료! 진행 중 요청 무시!
            println("[Server] Shutting down immediately!")
            println("[Server] WARNING: $activeRequests requests were in progress!")
            running = false
            // activeRequests가 0이 아닌데 그냥 종료...
        }
    }
}

// ============================================================
// 문제 2: 리소스 누수
// ============================================================

class ResourceLeakProblem {
    // 리소스 정리 없이 종료
    class LeakyApplication {
        private var dbConnection: FakeConnection? = null
        private var fileHandle: FakeFileHandle? = null
        private var cacheConnection: FakeConnection? = null

        fun start() {
            dbConnection = FakeConnection("PostgreSQL")
            fileHandle = FakeFileHandle("/var/log/app.log")
            cacheConnection = FakeConnection("Redis")
            println("[App] All resources acquired")
        }

        fun shutdown() {
            // 리소스 정리 코드가 없음!
            // DB 연결: 서버 측에서 타임아웃 후 정리 (리소스 낭비)
            // 파일 핸들: 버퍼 플러시 없이 닫힘 (데이터 손실)
            // 캐시 연결: 끊김 (다른 클라이언트 영향)
            println("[App] Shutdown - resources NOT cleaned up!")
        }
    }

    // 정리 순서 문제
    class WrongOrderCleanup {
        fun shutdown() {
            // 잘못된 순서로 정리
            // DB를 먼저 닫으면 캐시 정리 중 DB 쓰기 실패!
            closeDatabase()   // 먼저 닫음
            flushCache()      // DB에 쓰려는데 이미 닫혀있음! 에러!
            closeMessageQueue()
        }

        private fun closeDatabase() = println("DB closed")
        private fun flushCache() = println("Cache flush - DB write needed but DB is closed!")
        private fun closeMessageQueue() = println("MQ closed")
    }

    class FakeConnection(val name: String)
    class FakeFileHandle(val path: String)
}

// ============================================================
// 문제 3: 데이터 손실
// ============================================================

class DataLossProblem {
    // 버퍼링된 데이터가 플러시되지 않음
    class BufferedWriter {
        private val buffer = mutableListOf<String>()
        private val batchSize = 100

        fun write(data: String) {
            buffer.add(data)
            if (buffer.size >= batchSize) {
                flush()
            }
        }

        private fun flush() {
            println("[Writer] Flushing ${buffer.size} items to disk")
            buffer.clear()
        }

        fun shutdown() {
            // 버퍼에 남은 데이터 무시!
            println("[Writer] Shutdown - ${buffer.size} items LOST in buffer!")
            // buffer.size가 batchSize 미만이면 영원히 기록 안됨
        }
    }

    // 메트릭 데이터 손실
    class MetricsCollector {
        private val pendingMetrics = mutableListOf<String>()

        fun record(metric: String) {
            pendingMetrics.add(metric)
        }

        fun shutdown() {
            // 수집된 메트릭 전송 안됨
            println("[Metrics] ${pendingMetrics.size} metrics NOT sent!")
        }
    }
}

// ============================================================
// 문제 4: 메시지 큐 문제
// ============================================================

class MessageQueueProblem {
    // 메시지를 가져왔지만 ACK 하지 않음
    class UnsafeConsumer {
        private val processingMessages = mutableListOf<String>()

        fun consume(messageId: String) {
            processingMessages.add(messageId)
            // 메시지 처리 중...
            Thread.sleep(100)
            // ACK 전에 종료되면?
            // → 메시지가 다시 큐에 돌아감 (중복 처리)
            // → 또는 영원히 사라짐 (auto-ack인 경우)
        }

        fun shutdown() {
            println("[Consumer] ${processingMessages.size} messages in progress - NOT ACKed!")
            // 정상적이라면 처리 완료 후 ACK, 또는 NACK해서 큐로 반환해야 함
        }
    }
}

// ============================================================
// 문제 5: 스케줄러/백그라운드 작업 문제
// ============================================================

class BackgroundTaskProblem {
    // 백그라운드 작업이 중단됨
    class UnsafeScheduler {
        private val tasks = mutableListOf<Thread>()

        fun scheduleTask(name: String, intervalMs: Long) {
            val thread = Thread {
                while (true) {  // 종료 조건이 없음!
                    println("[Scheduler] Running: $name")
                    try {
                        Thread.sleep(intervalMs)
                    } catch (e: InterruptedException) {
                        // InterruptedException 무시!
                        // 스레드가 종료되지 않음
                    }
                }
            }
            thread.isDaemon = true  // 데몬이라 메인 종료 시 강제 종료됨
            thread.start()
            tasks.add(thread)
        }

        fun shutdown() {
            // 데몬 스레드라 그냥 죽음
            // 진행 중인 작업? 무시!
            println("[Scheduler] Tasks killed without cleanup!")
        }
    }
}

// ============================================================
// 문제 6: 배포 시 문제
// ============================================================

class DeploymentProblem {
    // 롤링 배포 중 에러 발생
    class NoGracefulServer {
        fun handleDeployment() {
            println("=== 롤링 배포 시나리오 ===")
            println("1. 새 버전 인스턴스 시작")
            println("2. 로드밸런서에서 이전 인스턴스 제거")
            println("3. 이전 인스턴스 즉시 종료 ← 문제!")
            println("   → 진행 중 요청 500 에러")
            println("   → 사용자 경험 저하")
            println("   → WebSocket 연결 끊김")
            println("   → 재시도 폭풍 (Retry Storm)")
        }
    }

    // Health Check 없이 종료
    class NoHealthCheckShutdown {
        fun handleShutdown() {
            println("=== Health Check 없는 종료 ===")
            println("1. SIGTERM 수신")
            println("2. 즉시 종료 시작")
            println("3. 로드밸런서는 아직 트래픽 보냄 ← 문제!")
            println("   → 새 요청이 종료 중인 서버로 전달")
            println("   → Connection Refused 에러")
        }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== Graceful Shutdown 없이 종료 시 문제점 ===\n")

    println("1. 즉시 종료")
    val server = ImmediateShutdownProblem.UnsafeServer()
    server.start()
    // 요청 처리 중 종료
    val requestThread = Thread { server.handleRequest("REQ-001") }
    requestThread.start()
    Thread.sleep(50)
    server.shutdown() // 요청 처리 중인데 종료!
    requestThread.join()

    println("\n2. 리소스 누수")
    val app = ResourceLeakProblem.LeakyApplication()
    app.start()
    app.shutdown()

    println("\n3. 데이터 손실")
    val writer = DataLossProblem.BufferedWriter()
    repeat(42) { writer.write("data-$it") }
    writer.shutdown()

    println("\n4. 메시지 큐 문제")
    val consumer = MessageQueueProblem.UnsafeConsumer()
    consumer.shutdown()

    println("\n5. 배포 시 문제")
    DeploymentProblem.NoGracefulServer().handleDeployment()

    println("\n→ 해결책: Graceful Shutdown Pattern으로 안전한 종료!")
}
