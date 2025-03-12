package concurrent.reactor.server

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 전통적인 스레드-기반 서버 구현
 * 각 클라이언트 연결마다 새로운 스레드를 생성하여 처리하는 방식
 */
class Problem {
    class ThreadPerClientServer(private val port: Int) {
        private val serverSocket: ServerSocket = ServerSocket()
        private val threadPool: ExecutorService = Executors.newCachedThreadPool()
        private val clientCounter = AtomicInteger(0)
        private val activeConnections = ConcurrentHashMap<Int, ClientHandler>()
        private var isRunning = false

        fun start() {
            try {
                serverSocket.bind(InetSocketAddress("localhost", port))
                isRunning = true
                println("서버가 포트 ${port}에서 시작되었습니다.")

                while (isRunning) {
                    try {
                        // 클라이언트 연결 수락 (블로킹 호출)
                        val clientSocket = serverSocket.accept()
                        val clientId = clientCounter.incrementAndGet()

                        println("클라이언트 #${clientId}가 연결되었습니다.")

                        // 각 클라이언트마다 새로운 스레드 할당
                        val clientHandler = ClientHandler(clientId, clientSocket)
                        activeConnections[clientId] = clientHandler

                        // 스레드 풀에서 클라이언트 처리 작업 실행
                        threadPool.execute(clientHandler)

                    } catch (e: IOException) {
                        if (isRunning) {
                            println("클라이언트 연결 중 오류 발생: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("서버 시작 중 오류 발생: ${e.message}")
            } finally {
                stop()
            }
        }

        fun stop() {
            isRunning = false

            // 모든 클라이언트 연결 종료
            activeConnections.forEach { (_, handler) ->
                handler.stop()
            }
            activeConnections.clear()

            // 스레드 풀 종료
            threadPool.shutdown()

            // 서버 소켓 닫기
            try {
                if (!serverSocket.isClosed) {
                    serverSocket.close()
                }
                println("서버가 정상적으로 종료되었습니다.")
            } catch (e: IOException) {
                println("서버 종료 중 오류 발생: ${e.message}")
            }
        }

        // 클라이언트 연결 처리를 위한 내부 클래스
        inner class ClientHandler(
            private val clientId: Int,
            private val clientSocket: java.net.Socket
        ) : Runnable {
            private var isRunning = false

            override fun run() {
                try {
                    val input = clientSocket.getInputStream()
                    val output = clientSocket.getOutputStream()
                    val buffer = ByteArray(1024)

                    while (isRunning) {
                        try {
                            // 클라이언트로부터 데이터 수신 (블로킹 호출)
                            val bytesRead = input.read(buffer)

                            if (bytesRead == -1) {
                                // 클라이언트 연결 종료
                                break
                            }

                            // 수신된 데이터 처리 (여기서는 간단히 에코 서버로 구현)
                            val message = String(buffer, 0, bytesRead).trim()
                            println("클라이언트 #${clientId}로부터 메시지 수신: $message")

                            // 실제 업무 로직 처리 시뮬레이션 (예: DB 조회, 외부 API 호출 등)
                            simulateProcessing()

                            // 응답 전송
                            val response = "응답: $message"
                            output.write(response.toByteArray())
                            output.flush()

                        } catch (e: IOException) {
                            if (isRunning) {
                                println("클라이언트 #$clientId 통신 중 오류 발생: ${e.message}")
                            }
                            break
                        }
                    }
                } catch (e: Exception) {

                }
            }

            fun stop() {
                isRunning = false
                try {
                    if (!clientSocket.isClosed) {
                        clientSocket.close()
                    }
                } catch (e: IOException) {
                    println("클라이언트 #$clientId 소켓 종료 중 오류 발생: ${e.message}")
                }
            }

            // 업무 로직 처리를 시뮬레이션하는 메서드
            private fun simulateProcessing() {
                // 실제 업무 로직 (예: DB 조회, 계산, 외부 API 호출 등)을 시뮬레이션
                Thread.sleep(100) // 100ms 처리 시간 가정
            }
        }
    }
}

/**
 * 메인 함수: 전통적인 스레드-기반 서버의 문제점 시연
 */
fun main() {
    // 1. 서버 인스턴스 생성
    val server = Problem.ThreadPerClientServer(8080)

    // 2. 별도 스레드에서 서버 시작
    val serverThread = Thread { server.start() }
    serverThread.start()

    // 3. 서버가 시작될 때까지 잠시 대기
    Thread.sleep(1000)

    println("\n========== 스레드 기반 서버의 문제점 ==========")
    println("1. 각 클라이언트 연결마다 하나의 스레드가 할당됩니다.")
    println("2. 동시 연결 수가 증가하면 스레드 수도 함께 증가합니다.")
    println("3. 너무 많은 스레드가 생성되면 메모리 사용량이 증가하고 컨텍스트 스위칭 오버헤드가 발생합니다.")
    println("4. 블로킹 I/O로 인해 스레드가 idle 상태로 대기하는 시간이 많아 리소스 활용도가 낮습니다.")
    println("5. 수천 개 이상의 동시 연결을 처리하기에는 확장성이 부족합니다.")
    println("=============================================\n")

    // 4. 서버 종료
    println("서버를 종료합니다...")
    server.stop()
    serverThread.join()
}