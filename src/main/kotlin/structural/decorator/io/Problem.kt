package structural.decorator.io

import java.io.File
import java.io.IOException

/**
 * 압축 기능이 있는 파일 스트림
 */
class CompressedFileStream(private val file: File) {
    @Throws(IOException::class)
    fun readCompressed(): ByteArray {
        println("압축된 파일 읽기: ${file.name}")
        val data = file.readBytes()
        return CompressionUtil.decompress(data)
    }

    @Throws(IOException::class)
    fun writeCompressed(data: ByteArray) {
        println("압축하여 파일 쓰기: ${file.name}")
        val compressedData = CompressionUtil.compress(data)
        file.writeBytes(compressedData)
    }
}

/**
 * 암호화 기능이 있는 파일 스트림
 */
class EncryptedFileStream(private val file: File) {
    @Throws(IOException::class)
    fun readEncrypted(): ByteArray {
        println("암호화된 파일 읽기: ${file.name}")
        val data = file.readBytes()
        return EncryptionUtil.decrypt(data)
    }

    @Throws(IOException::class)
    fun writeEncrypted(data: ByteArray) {
        println("암호화하여 파일 쓰기: ${file.name}")
        val encryptedData = EncryptionUtil.encrypt(data)
        file.writeBytes(encryptedData)
    }
}

/**
 * 버퍼링 기능이 있는 파일 스트림
 */
class BufferedFileStream(private val file: File) {
    private val bufferSize = 8192 // 8KB 버퍼

    @Throws(IOException::class)
    fun readBuffered(): ByteArray {
        println("버퍼를 사용하여 파일 읽기: ${file.name}")
        // 실제로는 여기서 버퍼를 사용하여 파일을 청크 단위로 읽음
        return file.readBytes()
    }

    @Throws(IOException::class)
    fun writeBuffered(data: ByteArray) {
        println("버퍼를 사용하여 파일 쓰기: ${file.name}")
        // 실제로는 여기서 버퍼를 사용하여 파일을 청크 단위로 씀
        file.writeBytes(data)
    }
}

/**
 * 압축 및 암호화 기능이 모두 필요한 경우를 위한 클래스
 */
class CompressedEncryptedFileStream(private val file: File) {
    @Throws(IOException::class)
    fun readCompressedEncrypted(): ByteArray {
        println("압축 및 암호화된 파일 읽기: ${file.name}")
        val data = file.readBytes()
        val decryptedData = EncryptionUtil.decrypt(data)
        return CompressionUtil.decompress(decryptedData)
    }

    @Throws(IOException::class)
    fun writeCompressedEncrypted(data: ByteArray) {
        println("압축 및 암호화하여 파일 쓰기: ${file.name}")
        val compressedData = CompressionUtil.compress(data)
        val encryptedData = EncryptionUtil.encrypt(compressedData)
        file.writeBytes(encryptedData)
    }
}

/**
 * 문제점을 보여주는 메인 함수
 */
fun main() {
    val testFile = File("test.txt")
    val testData = "Hello, World!".toByteArray()

    // 기본 파일 쓰기/읽기
    val fileStream = FileDataStream(testFile)
    fileStream.write(testData)
    val readData = fileStream.read()
    println("읽은 데이터: ${String(readData)}")
    println()

    // 압축된 파일 쓰기/읽기
    val compressedStream = CompressedFileStream(testFile)
    compressedStream.writeCompressed(testData)
    val compressedData = compressedStream.readCompressed()
    println("압축 해제된 데이터: ${String(compressedData)}")
    println()

    // 암호화된 파일 쓰기/읽기
    val encryptedStream = EncryptedFileStream(testFile)
    encryptedStream.writeEncrypted(testData)
    val decryptedData = encryptedStream.readEncrypted()
    println("복호화된 데이터: ${String(decryptedData)}")
    println()

    // 압축 및 암호화된 파일 쓰기/읽기
    val compressedEncryptedStream = CompressedEncryptedFileStream(testFile)
    compressedEncryptedStream.writeCompressedEncrypted(testData)
    val decompressedDecryptedData = compressedEncryptedStream.readCompressedEncrypted()
    println("압축 해제 및 복호화된 데이터: ${String(decompressedDecryptedData)}")
    println()

    println("문제점:")
    println("1. 기능이 추가될 때마다 새로운 클래스를 생성해야 함")
    println("2. 기능 조합마다 중복된 코드가 발생 (DRY 원칙 위반)")
    println("3. 새로운 기능 (예: 로깅, 체크섬 계산)이 필요할 때마다 조합 가능한 모든 클래스를 추가해야 함")
    println("4. 인터페이스가 일관되지 않아 사용하기 어려움")

    // 테스트 파일 삭제
    testFile.delete()
}