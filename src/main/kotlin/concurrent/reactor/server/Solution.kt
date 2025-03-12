package concurrent.reactor.server

import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reactor 패턴을 이용한 논블로킹 서버 구현
 */
class Solution {
    class ReactorServer(private val port: Int, private val workerPoolSize: Int = 4) {
        private val serverChannel: ServerSocketChannel = ServerSocketChannel.open()
        private val selector: Selector = Selector.open()
        private val clientCounter = AtomicInteger(0)
        private val clientBuffers = ConcurrentHashMap<SocketChannel, ByteBuffer>()
        private val dispatcher = CoroutineScope(Dispatchers.Default)
        private val workerPool = Executors.newFixedThreadPool(workerPoolSize).asCoroutineDispatcher()
        private var isRunning = false

        // 서버 초기화 및 시작
        fun start() {
            try {
                // 논블로킹 모드 설정
                serverChannel.configureBlocking(false)
                serverChannel.socket().bind(InetSocketAddress("localhost", port))

                // accept 이벤트 등록
                serverChannel.register(selector, SelectionKey.OP_ACCEPT)

                isRunning = true
                println("Reactor 서버가 포트 ${port}에서 시작되었습니다.")

                // 이벤트 루프 시작
                dispatcher.launch {
                    runEventLoop()
                }
            } catch (e: IOException) {
                println("서버 시작 중 오류 발생: ${e.message}")
                stop()
            }
        }

        private suspend fun runEventLoop() = withContext(Dispatchers.IO) {
            while(isRunning) {
                try {
                    // 이벤트 발생 대기 (논블로킹)
                    if (selector.select() > 0) {
                        val selectedKeys = selector.selectedKeys().iterator()

                        while (selectedKeys.hasNext()) {
                            val key = selectedKeys.next()
                            selectedKeys.remove()

                            if (!key.isValid) continue

                            // 이벤트 타입에 따른 처리
                            when {
                                key.isAcceptable -> handleAccept()
                                key.isReadable -> handleRead(key)
                                key.isWritable -> handleWrite(key)
                            }
                        }
                    }

                } catch (e: IOException) {
                    if (isRunning) {
                        println("이벤트 루프 실행 중 오류 발생: ${e.message}")
                    }
                } catch (e: CancellationException) {
                    // 코루틴 취소 처리
                    break
                }
            }
        }

        // 클라이언트 연결 수락 처리
        private fun handleAccept() {
            try {
                val clientChannel = serverChannel.accept()
                val clientId = clientCounter.incrementAndGet()

                if (clientChannel != null) {
                    // 논블로킹 모드 설정
                    clientChannel.configureBlocking(false)

                    // 읽기 이벤트 등록
                    clientChannel.register(selector, SelectionKey.OP_READ)

                    // 버퍼 할당
                    clientBuffers[clientChannel] = ByteBuffer.allocate(1024)

                    println("클라이언트 #${clientId}가 연결되었습니다.")
                }
            } catch (e: IOException) {
                println("클라이언트 연결 수락 중 오류 발생: ${e.message}")
            }
        }

        // 읽기 이벤트 처리
        private fun handleRead(key: SelectionKey) {
            val clientChannel = key.channel() as SocketChannel
            val buffer = clientBuffers[clientChannel] ?: return

            try {
                buffer.clear()
                val bytesRead = clientChannel.read(buffer)

                if (bytesRead == -1) {
                    // 클라이언트 연결 종료
                    closeClientConnection(clientChannel)
                    return
                }

                // 비즈니스 로직 처리를 별도 워커 풀에서 수행
                dispatcher.launch(workerPool) {
                    // 수신 데이터 처리
                    buffer.flip()
                    val receivedData = ByteArray(buffer.remaining())
                    buffer.get(receivedData)
                    val message = String(receivedData).trim()

                    println("클라이언트로부터 메시지 수신: $message")

                    // 실제 업무 로직 처리 시뮬레이션
                    simulateProcessing()

                    // 응답 전송
                    val response = "응답: $message"
                    val responseBuffer = ByteBuffer.wrap(response.toByteArray())

                    // 응답 전송을 위한 쓰기 이벤트 등록
                    withContext(Dispatchers.IO) {
                        synchronized(clientChannel) {
                            clientBuffers[clientChannel] = responseBuffer
                            clientChannel.register(selector, SelectionKey.OP_WRITE)
                            selector.wakeup() // 셀렉터 깨우기
                        }
                    }
                }

            } catch (e: IOException) {
                println("클라이언트로부터 데이터 읽기 중 오류 발생: ${e.message}")
                closeClientConnection(clientChannel)
            }
        }

        // 쓰기 이벤트 처리
        private fun handleWrite(key: SelectionKey) {
            val clientChannel = key.channel() as SocketChannel
            val buffer = clientBuffers[clientChannel] ?: return

            try {
                buffer.flip()
                clientChannel.write(buffer)

                // 모든 데이터를 전송했는지 확인
                if (!buffer.hasRemaining()) {
                    // 다시 읽기 모드로 전환
                    clientChannel.register(selector, SelectionKey.OP_READ)
                }
            } catch (e: IOException) {
                println("클라이언트로 데이터 쓰기 중 오류 발생: ${e.message}")
                closeClientConnection(clientChannel)
            }
        }

        // 클라이언트 연결 종료
        private fun closeClientConnection(clientChannel: SocketChannel) {
            try {
                clientBuffers.remove(clientChannel)
                clientChannel.close()
                println("클라이언트 연결이 종료되었습니다.")
            } catch (e: IOException) {
                println("클라이언트 연결 종료 중 오류 발생: ${e.message}")
            }
        }

        // 서버 종료
        fun stop() {
            isRunning = false

            // 모든 코루틴 취소
            dispatcher.cancel()

            // 워커 풀 종료
            workerPool.close()

            // 모든 클라이언트 연결 종료
            clientBuffers.keys.forEach { clientChannel ->
                try {
                    clientChannel.close()
                } catch (e: IOException) {
                    // 무시
                }
            }
            clientBuffers.clear()

            // 셀렉터 및 서버 채널 닫기
            try {
                selector.close()
                serverChannel.close()
                println("서버가 정상적으로 종료되었습니다.")
            } catch (e: IOException) {
                println("서버 종료 중 오류 발생: ${e.message}")
            }
        }

        // 업무 로직 처리를 시뮬레이션하는 메서드
        private suspend fun simulateProcessing() {
            // 실제 업무 로직 (예: DB 조회, 계산, 외부 API 호출 등)을 시뮬레이션
            delay(100) // 100ms 처리 시간 가정
        }

        // 활성 연결 수 반환
        fun getActiveConnectionCount(): Int {
            return clientBuffers.size
        }
    }
}

/**
 * 메인 함수: Reactor 패턴을 이용한 서버 시연
 *
 * 실행 방법:
 * 1. 터미널 접속
 * 2. nc localhost 8080
 * 3. 문자 입력
 */
fun main(): Unit = runBlocking {
    // 1. 서버 인스턴스 생성
    val server = Solution.ReactorServer(8080)

    // 2. 서버 시작
    server.start()

    // 3. 서버가 시작될 때까지 잠시 대기
    delay(1000)

    println("\n========== Reactor 패턴 서버의 장점 ==========")
    println("1. 소수의 스레드로 다수의 클라이언트 연결을 처리할 수 있습니다.")
    println("2. 논블로킹 I/O를 활용하여 I/O 대기 시간 동안 다른 작업을 처리할 수 있습니다.")
    println("3. 스레드 생성 및 컨텍스트 스위칭 오버헤드가 크게 감소합니다.")
    println("4. CPU 자원을 효율적으로 활용할 수 있습니다.")
    println("5. 수천, 수만 개의 동시 연결을 효율적으로 처리할 수 있는 확장성을 제공합니다.")
    println("6. 워커 풀을 활용하여 CPU 집약적인 작업을 별도로 처리할 수 있습니다.")
    println("=============================================\n")

    delay(10_000)
    // 4. 서버 종료
    println("서버를 종료합니다...")
    server.stop()
}