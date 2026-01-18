package behavioral.pipeline.image

/**
 * 이미지 처리 시스템 - Pipeline 패턴 적용
 *
 * Pipeline 패턴의 장점:
 * - 각 처리 단계가 독립적인 클래스로 분리됨 (단일 책임 원칙)
 * - 처리 단계의 추가/제거/순서 변경이 용이함
 * - 각 단계를 재사용할 수 있음
 * - 조건부 처리가 명확하고 유연함
 * - 테스트가 용이함
 * - Kotlin의 함수형 프로그래밍 특성과 잘 어울림
 */
class Solution {

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

    // Pipeline 단계 인터페이스
    fun interface PipelineStage<T> {
        fun process(input: T): T
    }

    // Pipeline 빌더 및 실행기
    class Pipeline<T> private constructor(
        private val stages: List<PipelineStage<T>>
    ) {
        fun execute(input: T): T {
            return stages.fold(input) { current, stage ->
                stage.process(current)
            }
        }

        // Pipeline 빌더
        class Builder<T> {
            private val stages = mutableListOf<PipelineStage<T>>()

            fun addStage(stage: PipelineStage<T>): Builder<T> {
                stages.add(stage)
                return this
            }

            fun addStageIf(condition: Boolean, stage: () -> PipelineStage<T>): Builder<T> {
                if (condition) {
                    stages.add(stage())
                }
                return this
            }

            fun build(): Pipeline<T> {
                return Pipeline(stages.toList())
            }
        }

        companion object {
            fun <T> builder(): Builder<T> = Builder()
        }
    }

    // === 이미지 처리 단계들 ===

    // 리사이즈 단계
    class ResizeStage(
        private val targetWidth: Int,
        private val targetHeight: Int
    ) : PipelineStage<Image> {
        init {
            require(targetWidth > 0 && targetHeight > 0) {
                "유효하지 않은 크기: ${targetWidth}x${targetHeight}"
            }
        }

        override fun process(input: Image): Image {
            println("  [ResizeStage] 리사이즈: ${input.width}x${input.height} -> ${targetWidth}x${targetHeight}")
            return input.copy(
                width = targetWidth,
                height = targetHeight,
                metadata = input.metadata.apply { put("resized", "true") }
            )
        }
    }

    // 그레이스케일 필터 단계
    class GrayscaleStage : PipelineStage<Image> {
        override fun process(input: Image): Image {
            println("  [GrayscaleStage] 그레이스케일 필터 적용")
            return input.copy(
                metadata = input.metadata.apply { put("filter", "grayscale") }
            )
        }
    }

    // 블러 필터 단계
    class BlurStage(private val radius: Int = 5) : PipelineStage<Image> {
        init {
            require(radius in 1..100) {
                "블러 반경은 1-100 사이여야 합니다: $radius"
            }
        }

        override fun process(input: Image): Image {
            println("  [BlurStage] 블러 필터 적용 (반경: $radius)")
            return input.copy(
                metadata = input.metadata.apply {
                    val currentFilter = get("filter")
                    put("filter", currentFilter?.let { "$it,blur" } ?: "blur")
                    put("blurRadius", radius.toString())
                }
            )
        }
    }

    // 세피아 필터 단계
    class SepiaStage(private val intensity: Double = 0.8) : PipelineStage<Image> {
        init {
            require(intensity in 0.0..1.0) {
                "세피아 강도는 0.0-1.0 사이여야 합니다: $intensity"
            }
        }

        override fun process(input: Image): Image {
            println("  [SepiaStage] 세피아 필터 적용 (강도: $intensity)")
            return input.copy(
                metadata = input.metadata.apply {
                    val currentFilter = get("filter")
                    put("filter", currentFilter?.let { "$it,sepia" } ?: "sepia")
                    put("sepiaIntensity", intensity.toString())
                }
            )
        }
    }

    // 워터마크 추가 단계
    class WatermarkStage(
        private val text: String,
        private val position: Position = Position.BOTTOM_RIGHT
    ) : PipelineStage<Image> {
        enum class Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

        init {
            require(text.isNotBlank()) { "워터마크 텍스트가 비어있습니다" }
        }

        override fun process(input: Image): Image {
            println("  [WatermarkStage] 워터마크 추가: '$text' at $position")
            return input.copy(
                metadata = input.metadata.apply {
                    put("watermark", text)
                    put("watermarkPosition", position.name)
                }
            )
        }
    }

    // 압축 단계
    class CompressionStage(private val quality: Int = 80) : PipelineStage<Image> {
        init {
            require(quality in 1..100) {
                "압축 품질은 1-100 사이여야 합니다: $quality"
            }
        }

        override fun process(input: Image): Image {
            println("  [CompressionStage] 압축 적용 (품질: $quality%)")
            return input.copy(
                metadata = input.metadata.apply {
                    put("compressed", "true")
                    put("quality", quality.toString())
                }
            )
        }
    }

    // 포맷 변환 단계
    class FormatConversionStage(private val targetFormat: String) : PipelineStage<Image> {
        companion object {
            private val SUPPORTED_FORMATS = listOf("jpg", "png", "gif", "webp", "bmp")
        }

        init {
            require(targetFormat in SUPPORTED_FORMATS) {
                "지원하지 않는 포맷: $targetFormat (지원: $SUPPORTED_FORMATS)"
            }
        }

        override fun process(input: Image): Image {
            println("  [FormatConversionStage] 포맷 변환: ${input.format} -> $targetFormat")
            return input.copy(
                format = targetFormat,
                metadata = input.metadata.apply {
                    put("converted", "true")
                    put("originalFormat", input.format)
                }
            )
        }
    }

    // 자르기(Crop) 단계
    class CropStage(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int
    ) : PipelineStage<Image> {
        override fun process(input: Image): Image {
            println("  [CropStage] 자르기: ($x, $y) - ${width}x${height}")
            return input.copy(
                width = width,
                height = height,
                metadata = input.metadata.apply {
                    put("cropped", "true")
                    put("cropRegion", "$x,$y,$width,$height")
                }
            )
        }
    }

    // 회전 단계
    class RotateStage(private val degrees: Int) : PipelineStage<Image> {
        override fun process(input: Image): Image {
            println("  [RotateStage] 회전: ${degrees}도")
            val (newWidth, newHeight) = if (degrees % 180 == 90) {
                input.height to input.width
            } else {
                input.width to input.height
            }
            return input.copy(
                width = newWidth,
                height = newHeight,
                metadata = input.metadata.apply {
                    put("rotated", degrees.toString())
                }
            )
        }
    }

    // 로깅 단계 (디버깅용)
    class LoggingStage(private val label: String) : PipelineStage<Image> {
        override fun process(input: Image): Image {
            println("  [LoggingStage][$label] 현재 상태: $input")
            return input
        }
    }

    // === 미리 정의된 파이프라인 팩토리 ===
    object ImagePipelines {
        // 썸네일 생성 파이프라인
        fun thumbnail(size: Int): Pipeline<Image> {
            return Pipeline.builder<Image>()
                .addStage(ResizeStage(size, size))
                .addStage(CompressionStage(60))
                .build()
        }

        // 웹 최적화 파이프라인
        fun webOptimized(): Pipeline<Image> {
            return Pipeline.builder<Image>()
                .addStage(ResizeStage(1200, 800))
                .addStage(CompressionStage(75))
                .addStage(FormatConversionStage("webp"))
                .build()
        }

        // SNS 공유용 파이프라인
        fun socialMedia(watermark: String): Pipeline<Image> {
            return Pipeline.builder<Image>()
                .addStage(ResizeStage(1080, 1080))
                .addStage(WatermarkStage(watermark, WatermarkStage.Position.BOTTOM_RIGHT))
                .addStage(CompressionStage(85))
                .addStage(FormatConversionStage("jpg"))
                .build()
        }

        // 프로필 이미지 파이프라인
        fun profileImage(): Pipeline<Image> {
            return Pipeline.builder<Image>()
                .addStage(CropStage(0, 0, 400, 400))
                .addStage(ResizeStage(200, 200))
                .addStage(CompressionStage(90))
                .addStage(FormatConversionStage("png"))
                .build()
        }

        // 빈티지 효과 파이프라인
        fun vintageEffect(): Pipeline<Image> {
            return Pipeline.builder<Image>()
                .addStage(SepiaStage(0.7))
                .addStage(BlurStage(2))
                .addStage(CompressionStage(80))
                .build()
        }
    }
}

fun main() {
    val originalImage = Solution.Image(
        name = "photo.png",
        width = 4000,
        height = 3000,
        format = "png"
    )

    println("=== Pipeline 패턴을 적용한 이미지 처리 ===")
    println()
    println("원본 이미지: $originalImage")
    println()

    // 1. 커스텀 파이프라인 구성
    println("--- 1. 커스텀 파이프라인 ---")
    val customPipeline = Solution.Pipeline.builder<Solution.Image>()
        .addStage(Solution.ResizeStage(1920, 1080))
        .addStage(Solution.GrayscaleStage())
        .addStage(Solution.WatermarkStage("© 2024 MyCompany"))
        .addStage(Solution.CompressionStage(80))
        .addStage(Solution.FormatConversionStage("jpg"))
        .build()

    val customResult = customPipeline.execute(originalImage)
    println("결과: $customResult")
    println()

    // 2. 조건부 파이프라인 구성
    println("--- 2. 조건부 파이프라인 ---")
    val applyWatermark = true
    val applyVintage = false

    val conditionalPipeline = Solution.Pipeline.builder<Solution.Image>()
        .addStage(Solution.ResizeStage(800, 600))
        .addStageIf(applyWatermark) { Solution.WatermarkStage("Conditional Watermark") }
        .addStageIf(applyVintage) { Solution.SepiaStage() }
        .addStage(Solution.CompressionStage(85))
        .build()

    val conditionalResult = conditionalPipeline.execute(originalImage)
    println("결과: $conditionalResult")
    println()

    // 3. 미리 정의된 파이프라인 사용
    println("--- 3. 썸네일 파이프라인 ---")
    val thumbnailResult = Solution.ImagePipelines.thumbnail(150).execute(originalImage)
    println("결과: $thumbnailResult")
    println()

    println("--- 4. 웹 최적화 파이프라인 ---")
    val webResult = Solution.ImagePipelines.webOptimized().execute(originalImage)
    println("결과: $webResult")
    println()

    println("--- 5. SNS 공유용 파이프라인 ---")
    val socialResult = Solution.ImagePipelines.socialMedia("@myaccount").execute(originalImage)
    println("결과: $socialResult")
    println()

    println("--- 6. 빈티지 효과 파이프라인 ---")
    val vintageResult = Solution.ImagePipelines.vintageEffect().execute(originalImage)
    println("결과: $vintageResult")
    println()

    // 4. 파이프라인 조합 (여러 이미지에 동일한 파이프라인 적용)
    println("--- 7. 배치 처리 ---")
    val images = listOf(
        Solution.Image("img1.png", 3000, 2000, "png"),
        Solution.Image("img2.jpg", 2500, 1800, "jpg"),
        Solution.Image("img3.bmp", 4000, 3000, "bmp")
    )

    val batchPipeline = Solution.ImagePipelines.webOptimized()
    val batchResults = images.map { image ->
        println("처리 중: ${image.name}")
        batchPipeline.execute(image)
    }

    println()
    println("배치 처리 결과:")
    batchResults.forEach { println("  - $it") }
}
