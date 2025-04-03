package structural.decorator.io

import java.io.File

class Solution {
    /**
     * 데코레이터의 기본 추상 클래스
     */
    abstract class DataStreamDecorator(protected val wrappedStream: DataStream) : DataStream {
        override fun read(): ByteArray {
            return wrappedStream.read()
        }

        override fun write(data: ByteArray) {
            wrappedStream.write(data)
        }
    }

    /**
     * 압축 기능을 추가하는 데코레이터
     */
    class CompressedDataStream(wrappedStream: DataStream) : DataStreamDecorator(wrappedStream) {
        override fun read(): ByteArray {
            println("압축 해제 데코레이터 적용")
            val compressedData = super.read()
            return CompressionUtil.decompress(compressedData)
        }

        override fun write(data: ByteArray) {
            println("압축 데코레이터 적용")
            val compressedData = CompressionUtil.compress(data)
            super.write(compressedData)
        }
    }

    /**
     * 암호화 기능을 추가하는 데코레이터
     */
    class EncryptedDataStream(wrappedStream: DataStream) : DataStreamDecorator(wrappedStream) {
        override fun read(): ByteArray {
            println("암호화 데코레이터 적용")
            val encryptedData = super.read()
            return EncryptionUtil.decrypt(encryptedData)
        }

        override fun write(data: ByteArray) {
            println("복호화 데코레이터 적용")
            val encryptedData = EncryptionUtil.encrypt(data)
            super.write(encryptedData)
        }
    }

    /**
     * 버퍼링 기능을 추가하는 데코레이터
     */
    class BufferedDataStream(wrappedStream: DataStream) : DataStreamDecorator(wrappedStream) {
        private val bufferSize = 8192 // 8KB 버퍼

        override fun read(): ByteArray {
            println("버퍼링 읽기 데코레이터 적용")
            // 실제로는 여기서 버퍼를 사용하여 파일을 청크 단위로 읽음
            return super.read()
        }

        override fun write(data: ByteArray) {
            println("버퍼링 쓰기 데코레이터 적용")
            // 실제로는 여기서 버퍼를 사용하여 파일을 청크 단위로 씀
            super.write(data)
        }
    }

    /**
     * 로깅 기능을 추가하는 데코레이터 (새로운 기능)
     */
    class LoggingDataStream(wrappedStream: DataStream) : DataStreamDecorator(wrappedStream) {
        override fun read(): ByteArray {
            val startTime = System.currentTimeMillis()
            println("읽기 작업 시작...")
            val result = super.read()
            val endTime = System.currentTimeMillis()
            println("읽기 작업 완료: ${endTime - startTime}ms 소요, ${result.size} 바이트 읽음")
            return result
        }

        override fun write(data: ByteArray) {
            val startTime = System.currentTimeMillis()
            println("쓰기 작업 시작: ${data.size} 바이트...")
            super.write(data)
            val endTime = System.currentTimeMillis()
            println("쓰기 작업 완료: ${endTime - startTime}ms 소요")
        }
    }

    /**
     * 체크섬 계산 기능을 추가하는 데코레이터 (새로운 기능)
     */
    class ChecksumDataStream(wrappedStream: DataStream) : DataStreamDecorator(wrappedStream) {
        private var lastChecksum: Int = 0

        override fun read(): ByteArray {
            val data = super.read()
            val checksum = calculateChecksum(data)
            println("읽은 데이터의 체크섬: $checksum")
            lastChecksum = checksum
            return data
        }

        override fun write(data: ByteArray) {
            val checksum = calculateChecksum(data)
            println("쓰는 데이터의 체크섬: $checksum")
            lastChecksum = checksum
            super.write(data)
        }

        private fun calculateChecksum(data: ByteArray): Int {
            return data.sum() % 256  // 간단한 체크섬 계산 예제
        }

        fun getLastChecksum(): Int = lastChecksum
    }
}

/**
 * 데코레이터 패턴을 사용한 해결책을 보여주는 메인 함수
 */
fun main() {
    val testFile = File("test_decorated.txt")
    val testData = "Hello, World!".toByteArray()

    println("=== 기본 파일 스트림 ===")
    val basicStream = FileDataStream(testFile)
    basicStream.write(testData)
    val readData = basicStream.read()
    println("읽은 데이터: ${String(readData)}")
    println()

    println("=== 압축 데코레이터 적용 ===")
    val compressedStream = Solution.CompressedDataStream(FileDataStream(testFile))
    compressedStream.write(testData)
    val compressedData = compressedStream.read()
    println("읽은 데이터: ${String(compressedData)}")
    println()

    println("=== 암호화 데코레이터 적용 ===")
    val encryptedStream = Solution.EncryptedDataStream(FileDataStream(testFile))
    encryptedStream.write(testData)
    val encryptedData = encryptedStream.read()
    println("읽은 데이터: ${String(encryptedData)}")
    println()

    println("=== 압축 + 암호화 데코레이터 적용 ===")
    // 순서 주의: 쓰기 시 먼저 압축 후 암호화, 읽기 시 먼저 복호화 후 압축 해제
    val compressedEncryptedStream = Solution.EncryptedDataStream(Solution.CompressedDataStream(FileDataStream(testFile)))
    compressedEncryptedStream.write(testData)
    val compressedEncryptedData = compressedEncryptedStream.read()
    println("읽은 데이터: ${String(compressedEncryptedData)}")
    println()

    println("=== 버퍼링 + 로깅 + 체크섬 데코레이터 적용 ===")
    val complexStream = Solution.LoggingDataStream(
        Solution.ChecksumDataStream(
            Solution.BufferedDataStream(
                FileDataStream(testFile)
            )
        )
    )
    complexStream.write(testData)
    val complexData = complexStream.read()
    println("읽은 데이터: ${String(complexData)}")
    println()

    println("=== 모든 데코레이터 적용 (버퍼링 + 압축 + 암호화 + 로깅 + 체크섬) ===")
    val allDecoratorsStream = Solution.LoggingDataStream(
        Solution.ChecksumDataStream(
            Solution.EncryptedDataStream(
                Solution.CompressedDataStream(
                    Solution.BufferedDataStream(
                        FileDataStream(testFile)
                    )
                )
            )
        )
    )
    allDecoratorsStream.write(testData)
    val allDecoratorsData = allDecoratorsStream.read()
    println("읽은 데이터: ${String(allDecoratorsData)}")
    println()

    println("장점:")
    println("1. 각 기능이 독립적인 클래스로 분리되어 단일 책임 원칙을 지킴")
    println("2. 기존 코드를 수정하지 않고 새로운 기능을 추가할 수 있음 (개방-폐쇄 원칙)")
    println("3. 런타임에 동적으로 기능을 조합할 수 있음")
    println("4. 클라이언트 코드는 항상 동일한 DataStream 인터페이스만 사용하면 됨")
    println("5. 데코레이터 순서를 조정하여 다양한 동작을 구현할 수 있음")

    // 테스트 파일 삭제
    testFile.delete()
}