package structural.decorator.io

import java.io.File
import java.io.IOException

/**
 * 입출력 작업의 기본 인터페이스
 */
interface DataStream {
    /**
     * 데이터를 읽는 메서드
     * @return 읽은 데이터의 바이트 배열
     */
    @Throws(IOException::class)
    fun read(): ByteArray

    /**
     * 데이터를 쓰는 메서드
     * @param data 쓸 데이터의 바이트 배열
     */
    @Throws(IOException::class)
    fun write(data: ByteArray)
}

/**
 * 파일 입출력 작업을 수행하는 기본 구현체
 */
class FileDataStream(private val file: File) : DataStream {
    override fun read(): ByteArray {
        println("기본 파일 읽기: ${file.name}")
        return file.readBytes()
    }

    override fun write(data: ByteArray) {
        println("기본 파일 쓰기: ${file.name}")
        file.writeBytes(data)
    }
}

/**
 * 데이터 압축을 위한 유틸리티 클래스 (실제 구현은 간소화)
 */
object CompressionUtil {
    fun compress(data: ByteArray): ByteArray {
        println("데이터 압축 중...")
        // 실제로는 여기서 압축 알고리즘을 사용하여 데이터를 압축
        // 간단한 예제를 위해 원래 데이터 반환
        return data
    }

    fun decompress(data: ByteArray): ByteArray {
        println("데이터 압축 해제 중...")
        // 압축 해제 로직
        return data
    }
}

/**
 * 데이터 암호화를 위한 유틸리티 클래스 (실제 구현은 간소화)
 */
object EncryptionUtil {
    fun encrypt(data: ByteArray): ByteArray {
        println("데이터 암호화 중...")
        // 실제로는 여기서 암호화 알고리즘을 사용
        return data
    }

    fun decrypt(data: ByteArray): ByteArray {
        println("데이터 복호화 중...")
        // 복호화 로직
        return data
    }
}