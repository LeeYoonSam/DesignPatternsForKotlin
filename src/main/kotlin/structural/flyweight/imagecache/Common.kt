package structural.flyweight.imagecache

import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 이미지 인터페이스: 플라이웨이트 객체의 인터페이스를 정의
 */
interface Image {
    val url: String
    val width: Int
    val height: Int
    fun display()
    fun getMetadata(): String
}

/**
 * 로그 출력을 위한 유틸리티 함수
 */
fun log(message: String) {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
    println("[$timestamp] $message")
}

/**
 * 이미지 로드를 시뮬레이션하기 위한 함수
 */
suspend fun loadImageFromUrl(url: String): ByteArray {
    log("네트워크에서 이미지 로드 중: $url")
    // 네트워크 지연 시뮬레이션
    delay(1000)
    // 실제로는 여기서 네트워크 요청을 통해 이미지를 로드하고 바이트 배열로 반환
    return "이미지 데이터: $url".toByteArray()
}

/**
 * 메모리 사용량을 표시하기 위한 유틸리티 함수
 */
fun displayMemoryUsage() {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    log("현재 사용 중인 메모리: ${usedMemory}MB")
}