package behavioral.pipeline.image

/**
 * 이미지 처리 시스템 - Pipeline 패턴 적용 전
 *
 * 문제점:
 * - 모든 처리 로직이 하나의 클래스에 집중되어 있음
 * - 처리 단계 추가/제거/순서 변경이 어려움
 * - 각 처리 단계의 재사용이 불가능
 * - 조건부 처리 로직이 복잡해짐
 * - 단일 책임 원칙(SRP) 위반
 * - 테스트하기 어려움
 */
class Problem {

    // 이미지 데이터 클래스
    data class Image(
        val name: String,
        val width: Int,
        val height: Int,
        val format: String,
        val data: ByteArray = ByteArray(0),
        val metadata: MutableMap<String, String> = mutableMapOf()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return name == other.name && width == other.width && height == other.height && format == other.format
        }

        override fun hashCode(): Int {
            return name.hashCode() + width * 31 + height * 17
        }

        override fun toString(): String {
            return "Image(name='$name', size=${width}x${height}, format='$format', metadata=$metadata)"
        }
    }

    // 문제가 있는 코드: 모든 처리 로직이 하나의 클래스에 집중
    class ImageProcessor {
        fun processImage(
            image: Image,
            resize: Boolean = false,
            targetWidth: Int = 0,
            targetHeight: Int = 0,
            applyGrayscale: Boolean = false,
            applyBlur: Boolean = false,
            blurRadius: Int = 5,
            addWatermark: Boolean = false,
            watermarkText: String = "",
            compress: Boolean = false,
            compressionQuality: Int = 80,
            convertFormat: Boolean = false,
            targetFormat: String = "jpg"
        ): Image {
            var result = image.copy(metadata = image.metadata.toMutableMap())
            println("원본 이미지: $result")

            // 리사이즈 처리
            if (resize) {
                if (targetWidth <= 0 || targetHeight <= 0) {
                    throw IllegalArgumentException("유효하지 않은 크기: ${targetWidth}x${targetHeight}")
                }
                result = result.copy(
                    width = targetWidth,
                    height = targetHeight,
                    metadata = result.metadata.apply { put("resized", "true") }
                )
                println("리사이즈 완료: ${result.width}x${result.height}")
            }

            // 그레이스케일 필터 적용
            if (applyGrayscale) {
                result = result.copy(
                    metadata = result.metadata.apply { put("filter", "grayscale") }
                )
                println("그레이스케일 필터 적용 완료")
            }

            // 블러 필터 적용
            if (applyBlur) {
                if (blurRadius < 1 || blurRadius > 100) {
                    throw IllegalArgumentException("블러 반경은 1-100 사이여야 합니다: $blurRadius")
                }
                result = result.copy(
                    metadata = result.metadata.apply {
                        put("filter", get("filter")?.let { "$it,blur" } ?: "blur")
                        put("blurRadius", blurRadius.toString())
                    }
                )
                println("블러 필터 적용 완료 (반경: $blurRadius)")
            }

            // 워터마크 추가
            if (addWatermark) {
                if (watermarkText.isBlank()) {
                    throw IllegalArgumentException("워터마크 텍스트가 비어있습니다")
                }
                result = result.copy(
                    metadata = result.metadata.apply {
                        put("watermark", watermarkText)
                    }
                )
                println("워터마크 추가 완료: '$watermarkText'")
            }

            // 압축
            if (compress) {
                if (compressionQuality < 1 || compressionQuality > 100) {
                    throw IllegalArgumentException("압축 품질은 1-100 사이여야 합니다: $compressionQuality")
                }
                result = result.copy(
                    metadata = result.metadata.apply {
                        put("compressed", "true")
                        put("quality", compressionQuality.toString())
                    }
                )
                println("압축 완료 (품질: $compressionQuality%)")
            }

            // 포맷 변환
            if (convertFormat) {
                val supportedFormats = listOf("jpg", "png", "gif", "webp", "bmp")
                if (targetFormat !in supportedFormats) {
                    throw IllegalArgumentException("지원하지 않는 포맷: $targetFormat")
                }
                result = result.copy(
                    format = targetFormat,
                    metadata = result.metadata.apply {
                        put("converted", "true")
                        put("originalFormat", image.format)
                    }
                )
                println("포맷 변환 완료: ${image.format} -> $targetFormat")
            }

            println("최종 이미지: $result")
            return result
        }

        // 특정 처리 조합을 위한 메서드들 - 중복 코드 발생
        fun createThumbnail(image: Image, size: Int): Image {
            return processImage(
                image,
                resize = true,
                targetWidth = size,
                targetHeight = size,
                compress = true,
                compressionQuality = 60
            )
        }

        fun applyWatermarkAndCompress(image: Image, watermark: String): Image {
            return processImage(
                image,
                addWatermark = true,
                watermarkText = watermark,
                compress = true,
                compressionQuality = 85
            )
        }

        fun convertToWebOptimized(image: Image): Image {
            return processImage(
                image,
                resize = true,
                targetWidth = 1200,
                targetHeight = 800,
                compress = true,
                compressionQuality = 75,
                convertFormat = true,
                targetFormat = "webp"
            )
        }
    }
}

fun main() {
    val processor = Problem.ImageProcessor()

    val originalImage = Problem.Image(
        name = "photo.png",
        width = 4000,
        height = 3000,
        format = "png"
    )

    println("=== 복잡한 이미지 처리 ===")
    println()

    // 문제점: 많은 파라미터와 플래그로 인해 코드가 복잡해짐
    try {
        val processed = processor.processImage(
            image = originalImage,
            resize = true,
            targetWidth = 1920,
            targetHeight = 1080,
            applyGrayscale = true,
            applyBlur = false,
            addWatermark = true,
            watermarkText = "© 2024 MyCompany",
            compress = true,
            compressionQuality = 80,
            convertFormat = true,
            targetFormat = "jpg"
        )
        println()
        println("처리 완료: $processed")
    } catch (e: Exception) {
        println("오류 발생: ${e.message}")
    }

    println()
    println("=== 썸네일 생성 ===")
    println()
    val thumbnail = processor.createThumbnail(originalImage, 150)
    println("썸네일: $thumbnail")

    println()
    println("=== 웹 최적화 변환 ===")
    println()
    val webOptimized = processor.convertToWebOptimized(originalImage)
    println("웹 최적화: $webOptimized")
}
