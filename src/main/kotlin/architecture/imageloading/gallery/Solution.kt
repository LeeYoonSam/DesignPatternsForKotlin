package architecture.imageloading.gallery

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Image Loading Pattern - 해결책
 *
 * 이미지 갤러리 앱에 효율적인 이미지 로딩 시스템을 구현:
 * - 3단계 캐시: Memory → Disk → Network
 * - 다운샘플링: 표시 크기에 맞게 리사이즈
 * - 요청 취소: View 재사용 시 이전 요청 취소
 * - Bitmap Pool: 메모리 재활용으로 GC 감소
 * - Transformation: 이미지 변환 체인
 *
 * 핵심 구성:
 * - ImageLoader: 이미지 로딩 엔트리 포인트
 * - MemoryCache: LRU 기반 메모리 캐시
 * - DiskCache: 디스크 캐시
 * - Fetcher: 네트워크에서 이미지 다운로드
 * - Decoder: 바이트 → Bitmap 디코딩
 * - Transformation: 이미지 변환 (Crop, Blur 등)
 * - BitmapPool: Bitmap 재활용
 */

// ============================================================
// 1. Bitmap 모델 (실제로는 android.graphics.Bitmap)
// ============================================================

/**
 * Bitmap 시뮬레이션
 * 실제 Android에서는 android.graphics.Bitmap 사용
 */
data class Bitmap(
    val width: Int,
    val height: Int,
    val config: Config = Config.ARGB_8888,
    val pixels: IntArray = IntArray(width * height)
) {
    enum class Config(val bytesPerPixel: Int) {
        ARGB_8888(4),
        RGB_565(2),
        ALPHA_8(1)
    }

    val byteCount: Int get() = width * height * config.bytesPerPixel
    val allocationByteCount: Int get() = byteCount

    fun recycle() {
        // Bitmap 메모리 해제
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bitmap) return false
        return width == other.width && height == other.height
    }

    override fun hashCode(): Int = 31 * width + height
}

// ============================================================
// 2. 이미지 요청 모델
// ============================================================

/**
 * 이미지 로드 요청
 */
data class ImageRequest(
    val url: String,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val placeholder: Bitmap? = null,
    val errorImage: Bitmap? = null,
    val transformations: List<Transformation> = emptyList(),
    val scaleType: ScaleType = ScaleType.CENTER_CROP,
    val cachePolicy: CachePolicy = CachePolicy.ALL,
    val priority: Priority = Priority.NORMAL
) {
    val cacheKey: String by lazy {
        buildString {
            append(url)
            append("_${targetWidth}x$targetHeight")
            append("_$scaleType")
            transformations.forEach { append("_${it.key}") }
        }
    }
}

enum class ScaleType {
    CENTER_CROP,
    CENTER_INSIDE,
    FIT_CENTER,
    FIT_XY
}

enum class CachePolicy {
    ALL,           // Memory + Disk 캐시 사용
    MEMORY_ONLY,   // Memory 캐시만
    DISK_ONLY,     // Disk 캐시만
    NETWORK_ONLY,  // 캐시 무시
    NONE           // 캐시 안 함
}

enum class Priority {
    LOW, NORMAL, HIGH, IMMEDIATE
}

/**
 * 이미지 로드 결과
 */
sealed class ImageResult {
    data class Success(
        val bitmap: Bitmap,
        val source: DataSource
    ) : ImageResult()

    data class Error(
        val exception: Throwable,
        val errorBitmap: Bitmap? = null
    ) : ImageResult()
}

enum class DataSource {
    MEMORY_CACHE,
    DISK_CACHE,
    NETWORK
}

// ============================================================
// 3. Transformation - 이미지 변환
// ============================================================

/**
 * 이미지 변환 인터페이스
 */
interface Transformation {
    val key: String
    fun transform(bitmap: Bitmap): Bitmap
}

/**
 * 원형 크롭
 */
class CircleCropTransformation : Transformation {
    override val key = "circle_crop"

    override fun transform(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        println("    [Transform] CircleCrop: ${bitmap.width}x${bitmap.height} → ${size}x$size")
        return Bitmap(size, size, bitmap.config)
    }
}

/**
 * 라운드 코너
 */
class RoundedCornersTransformation(private val radius: Int) : Transformation {
    override val key = "rounded_$radius"

    override fun transform(bitmap: Bitmap): Bitmap {
        println("    [Transform] RoundedCorners: radius=$radius")
        return bitmap.copy()
    }

    private fun Bitmap.copy() = Bitmap(width, height, config)
}

/**
 * 블러
 */
class BlurTransformation(private val radius: Int = 25) : Transformation {
    override val key = "blur_$radius"

    override fun transform(bitmap: Bitmap): Bitmap {
        println("    [Transform] Blur: radius=$radius")
        return bitmap.copy()
    }

    private fun Bitmap.copy() = Bitmap(width, height, config)
}

/**
 * 그레이스케일
 */
class GrayscaleTransformation : Transformation {
    override val key = "grayscale"

    override fun transform(bitmap: Bitmap): Bitmap {
        println("    [Transform] Grayscale")
        return bitmap.copy()
    }

    private fun Bitmap.copy() = Bitmap(width, height, config)
}

// ============================================================
// 4. Memory Cache - LRU 기반
// ============================================================

/**
 * LRU 메모리 캐시
 * 최근 사용된 항목을 유지하고, 오래된 항목을 제거
 */
class MemoryCache(
    private val maxSize: Int = 1024 * 1024 * 50 // 50MB 기본값
) {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val shouldRemove = currentSize > maxSize
            if (shouldRemove && eldest != null) {
                currentSize -= eldest.value.byteCount
                println("    [MemoryCache] 제거: ${eldest.key.takeLast(30)}...")
            }
            return shouldRemove
        }
    }

    private var currentSize = 0
    private var hitCount = 0
    private var missCount = 0

    @Synchronized
    fun get(key: String): Bitmap? {
        val bitmap = cache[key]
        if (bitmap != null) {
            hitCount++
            println("    [MemoryCache] HIT: ${key.takeLast(30)}...")
        } else {
            missCount++
        }
        return bitmap
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        val size = bitmap.byteCount
        if (size > maxSize) {
            println("    [MemoryCache] 이미지가 캐시 최대 크기보다 큼, 스킵")
            return
        }

        cache[key] = bitmap
        currentSize += size
        println("    [MemoryCache] PUT: ${key.takeLast(30)}... (${size / 1024}KB)")
    }

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)?.let {
            currentSize -= it.byteCount
        }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        currentSize = 0
    }

    fun getStats(): String = "Hit: $hitCount, Miss: $missCount, Rate: ${
        if (hitCount + missCount > 0) hitCount * 100 / (hitCount + missCount) else 0
    }%"
}

// ============================================================
// 5. Disk Cache
// ============================================================

/**
 * 디스크 캐시 (DiskLruCache 시뮬레이션)
 */
class DiskCache(
    private val cacheDir: File = File(System.getProperty("java.io.tmpdir"), "image_cache"),
    private val maxSize: Long = 1024 * 1024 * 100 // 100MB
) {
    private val journal = mutableMapOf<String, Long>() // key → size

    init {
        cacheDir.mkdirs()
    }

    fun get(key: String): ByteArray? {
        val file = File(cacheDir, key.toMD5())
        return if (file.exists()) {
            println("    [DiskCache] HIT: ${key.takeLast(30)}...")
            // 실제로는 파일에서 읽기
            ByteArray(1024) // 시뮬레이션
        } else {
            null
        }
    }

    fun put(key: String, data: ByteArray) {
        val file = File(cacheDir, key.toMD5())
        // 실제로는 파일에 쓰기
        journal[key] = data.size.toLong()
        println("    [DiskCache] PUT: ${key.takeLast(30)}... (${data.size / 1024}KB)")

        // 캐시 크기 관리
        trimToSize()
    }

    private fun trimToSize() {
        var currentSize = journal.values.sum()
        while (currentSize > maxSize && journal.isNotEmpty()) {
            val oldest = journal.keys.first()
            journal.remove(oldest)
            File(cacheDir, oldest.toMD5()).delete()
            currentSize = journal.values.sum()
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
        journal.clear()
    }

    private fun String.toMD5(): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

// ============================================================
// 6. Bitmap Pool - 메모리 재활용
// ============================================================

/**
 * Bitmap Pool - Bitmap 객체 재활용으로 GC 감소
 */
class BitmapPool(
    private val maxSize: Int = 1024 * 1024 * 20 // 20MB
) {
    private val pool = mutableListOf<Bitmap>()
    private var currentSize = 0
    private var reuseCount = 0
    private var allocCount = 0

    @Synchronized
    fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        // 재활용 가능한 Bitmap 찾기
        val index = pool.indexOfFirst {
            it.width == width && it.height == height && it.config == config
        }

        return if (index >= 0) {
            reuseCount++
            val bitmap = pool.removeAt(index)
            currentSize -= bitmap.byteCount
            println("    [BitmapPool] 재활용: ${width}x$height")
            bitmap
        } else {
            allocCount++
            println("    [BitmapPool] 새 할당: ${width}x$height")
            Bitmap(width, height, config)
        }
    }

    @Synchronized
    fun put(bitmap: Bitmap) {
        if (currentSize + bitmap.byteCount > maxSize) {
            // 공간 부족 시 가장 오래된 것 제거
            while (pool.isNotEmpty() && currentSize + bitmap.byteCount > maxSize) {
                val removed = pool.removeAt(0)
                currentSize -= removed.byteCount
                removed.recycle()
            }
        }

        pool.add(bitmap)
        currentSize += bitmap.byteCount
    }

    fun getStats(): String = "Reuse: $reuseCount, Alloc: $allocCount, Rate: ${
        if (reuseCount + allocCount > 0) reuseCount * 100 / (reuseCount + allocCount) else 0
    }%"
}

// ============================================================
// 7. Fetcher & Decoder
// ============================================================

/**
 * 네트워크에서 이미지 다운로드
 */
class NetworkFetcher {
    suspend fun fetch(url: String): ByteArray {
        println("    [Network] 다운로드: ${url.takeLast(40)}...")
        delay(500) // 네트워크 지연 시뮬레이션

        // 가상의 이미지 데이터
        val size = (50..500).random() * 1024 // 50KB ~ 500KB
        println("    [Network] 완료: ${size / 1024}KB")
        return ByteArray(size)
    }
}

/**
 * 바이트 배열을 Bitmap으로 디코딩
 * 다운샘플링 포함
 */
class BitmapDecoder(
    private val bitmapPool: BitmapPool
) {
    /**
     * 다운샘플링하여 디코딩
     * @param data 원본 이미지 바이트
     * @param targetWidth 목표 너비 (0이면 원본 유지)
     * @param targetHeight 목표 높이 (0이면 원본 유지)
     */
    fun decode(
        data: ByteArray,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        // 1. 원본 크기 확인 (실제로는 BitmapFactory.Options.inJustDecodeBounds)
        val originalWidth = 2000
        val originalHeight = 1500

        // 2. inSampleSize 계산
        val sampleSize = calculateInSampleSize(
            originalWidth, originalHeight,
            targetWidth, targetHeight
        )

        // 3. 다운샘플링된 크기
        val decodedWidth = originalWidth / sampleSize
        val decodedHeight = originalHeight / sampleSize

        println("    [Decoder] ${originalWidth}x$originalHeight → ${decodedWidth}x$decodedHeight (sampleSize=$sampleSize)")

        // 4. BitmapPool에서 재활용 또는 새로 할당
        return bitmapPool.get(decodedWidth, decodedHeight, Bitmap.Config.ARGB_8888)
    }

    private fun calculateInSampleSize(
        srcWidth: Int, srcHeight: Int,
        targetWidth: Int, targetHeight: Int
    ): Int {
        if (targetWidth <= 0 || targetHeight <= 0) return 1

        var inSampleSize = 1
        if (srcHeight > targetHeight || srcWidth > targetWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            while (halfHeight / inSampleSize >= targetHeight &&
                halfWidth / inSampleSize >= targetWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

// ============================================================
// 8. Image Loader - 메인 엔트리 포인트
// ============================================================

/**
 * 이미지 로더
 * 3단계 캐시, 다운샘플링, 변환, 취소를 통합 관리
 */
class ImageLoader private constructor(
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val bitmapPool: BitmapPool,
    private val fetcher: NetworkFetcher,
    private val decoder: BitmapDecoder,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val activeRequests = ConcurrentHashMap<String, Job>()
    private val requestCounter = AtomicInteger(0)

    companion object {
        @Volatile
        private var instance: ImageLoader? = null

        fun getInstance(): ImageLoader {
            return instance ?: synchronized(this) {
                instance ?: create().also { instance = it }
            }
        }

        fun create(
            maxMemoryCacheSize: Int = 1024 * 1024 * 50,
            maxDiskCacheSize: Long = 1024 * 1024 * 100
        ): ImageLoader {
            val memoryCache = MemoryCache(maxMemoryCacheSize)
            val diskCache = DiskCache(maxSize = maxDiskCacheSize)
            val bitmapPool = BitmapPool()
            val fetcher = NetworkFetcher()
            val decoder = BitmapDecoder(bitmapPool)

            return ImageLoader(memoryCache, diskCache, bitmapPool, fetcher, decoder)
        }
    }

    /**
     * 이미지 로드 (suspend)
     */
    suspend fun load(request: ImageRequest): ImageResult {
        val requestId = requestCounter.incrementAndGet()
        println("\n  [Request #$requestId] 시작: ${request.url.takeLast(40)}...")

        return try {
            // 1. Memory Cache 확인
            if (request.cachePolicy != CachePolicy.NETWORK_ONLY &&
                request.cachePolicy != CachePolicy.DISK_ONLY
            ) {
                memoryCache.get(request.cacheKey)?.let { bitmap ->
                    return ImageResult.Success(bitmap, DataSource.MEMORY_CACHE)
                }
            }

            // 2. Disk Cache 확인
            if (request.cachePolicy != CachePolicy.NETWORK_ONLY &&
                request.cachePolicy != CachePolicy.MEMORY_ONLY
            ) {
                diskCache.get(request.cacheKey)?.let { data ->
                    val bitmap = decoder.decode(data, request.targetWidth, request.targetHeight)
                    val transformed = applyTransformations(bitmap, request.transformations)
                    memoryCache.put(request.cacheKey, transformed)
                    return ImageResult.Success(transformed, DataSource.DISK_CACHE)
                }
            }

            // 3. Network에서 로드
            val data = fetcher.fetch(request.url)

            // 4. 디스크 캐시에 저장
            if (request.cachePolicy == CachePolicy.ALL ||
                request.cachePolicy == CachePolicy.DISK_ONLY
            ) {
                diskCache.put(request.cacheKey, data)
            }

            // 5. 디코딩 (다운샘플링 포함)
            val bitmap = decoder.decode(data, request.targetWidth, request.targetHeight)

            // 6. Transformation 적용
            val transformed = applyTransformations(bitmap, request.transformations)

            // 7. 메모리 캐시에 저장
            if (request.cachePolicy == CachePolicy.ALL ||
                request.cachePolicy == CachePolicy.MEMORY_ONLY
            ) {
                memoryCache.put(request.cacheKey, transformed)
            }

            println("  [Request #$requestId] 완료: ${transformed.width}x${transformed.height}")
            ImageResult.Success(transformed, DataSource.NETWORK)

        } catch (e: CancellationException) {
            println("  [Request #$requestId] 취소됨")
            throw e
        } catch (e: Exception) {
            println("  [Request #$requestId] 에러: ${e.message}")
            ImageResult.Error(e, request.errorImage)
        }
    }

    /**
     * 이미지 로드 (Flow)
     * Placeholder → Loading → Success/Error 순서로 emit
     */
    fun loadAsFlow(request: ImageRequest): Flow<ImageResult> = flow {
        // Placeholder emit
        request.placeholder?.let {
            emit(ImageResult.Success(it, DataSource.MEMORY_CACHE))
        }

        // 실제 로드
        emit(load(request))
    }.flowOn(dispatcher)

    /**
     * 이미지 로드 (취소 가능)
     */
    fun enqueue(
        request: ImageRequest,
        target: ImageTarget
    ): Job {
        // 이전 요청 취소 (같은 타겟에 대해)
        val targetKey = System.identityHashCode(target).toString()
        activeRequests[targetKey]?.cancel()

        val job = scope.launch {
            target.onStart(request.placeholder)

            when (val result = load(request)) {
                is ImageResult.Success -> target.onSuccess(result.bitmap, result.source)
                is ImageResult.Error -> target.onError(result.exception, result.errorBitmap)
            }
        }

        activeRequests[targetKey] = job
        return job
    }

    /**
     * 요청 취소
     */
    fun cancel(target: ImageTarget) {
        val targetKey = System.identityHashCode(target).toString()
        activeRequests[targetKey]?.cancel()
        activeRequests.remove(targetKey)
    }

    private fun applyTransformations(bitmap: Bitmap, transformations: List<Transformation>): Bitmap {
        if (transformations.isEmpty()) return bitmap

        var current = bitmap
        for (transformation in transformations) {
            current = transformation.transform(current)
        }
        return current
    }

    /**
     * 캐시 통계
     */
    fun getStats(): String = buildString {
        appendLine("=== Image Loader Stats ===")
        appendLine("Memory Cache: ${memoryCache.getStats()}")
        appendLine("Bitmap Pool: ${bitmapPool.getStats()}")
    }

    /**
     * 캐시 클리어
     */
    fun clearCache() {
        memoryCache.clear()
        diskCache.clear()
    }
}

/**
 * 이미지 타겟 (ImageView 등)
 */
interface ImageTarget {
    fun onStart(placeholder: Bitmap?)
    fun onSuccess(bitmap: Bitmap, source: DataSource)
    fun onError(exception: Throwable, errorBitmap: Bitmap?)
}

// ============================================================
// 9. DSL Builder
// ============================================================

/**
 * ImageRequest 빌더 DSL
 */
class ImageRequestBuilder(private val url: String) {
    private var targetWidth: Int = 0
    private var targetHeight: Int = 0
    private var placeholder: Bitmap? = null
    private var errorImage: Bitmap? = null
    private var transformations: MutableList<Transformation> = mutableListOf()
    private var scaleType: ScaleType = ScaleType.CENTER_CROP
    private var cachePolicy: CachePolicy = CachePolicy.ALL
    private var priority: Priority = Priority.NORMAL

    fun size(width: Int, height: Int) = apply {
        targetWidth = width
        targetHeight = height
    }

    fun placeholder(bitmap: Bitmap) = apply { placeholder = bitmap }
    fun error(bitmap: Bitmap) = apply { errorImage = bitmap }

    fun transform(transformation: Transformation) = apply {
        transformations.add(transformation)
    }

    fun circleCrop() = transform(CircleCropTransformation())
    fun roundedCorners(radius: Int) = transform(RoundedCornersTransformation(radius))
    fun blur(radius: Int = 25) = transform(BlurTransformation(radius))
    fun grayscale() = transform(GrayscaleTransformation())

    fun scaleType(type: ScaleType) = apply { scaleType = type }
    fun cachePolicy(policy: CachePolicy) = apply { cachePolicy = policy }
    fun priority(p: Priority) = apply { priority = p }

    fun build() = ImageRequest(
        url = url,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        placeholder = placeholder,
        errorImage = errorImage,
        transformations = transformations,
        scaleType = scaleType,
        cachePolicy = cachePolicy,
        priority = priority
    )
}

fun imageRequest(url: String, block: ImageRequestBuilder.() -> Unit = {}): ImageRequest {
    return ImageRequestBuilder(url).apply(block).build()
}

// ============================================================
// 10. 사용 예시 - ImageView 확장
// ============================================================

/**
 * 가상의 ImageView
 */
class ImageView {
    var bitmap: Bitmap? = null
    var isLoading = false

    fun setImageBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        println("  [ImageView] 이미지 설정: ${bitmap?.width}x${bitmap?.height}")
    }

    fun showLoading() {
        isLoading = true
        println("  [ImageView] 로딩 표시")
    }

    fun hideLoading() {
        isLoading = false
    }
}

/**
 * ImageView용 Target
 */
class ImageViewTarget(private val imageView: ImageView) : ImageTarget {
    override fun onStart(placeholder: Bitmap?) {
        imageView.showLoading()
        placeholder?.let { imageView.setImageBitmap(it) }
    }

    override fun onSuccess(bitmap: Bitmap, source: DataSource) {
        imageView.hideLoading()
        imageView.setImageBitmap(bitmap)
        println("  [ImageViewTarget] 성공 (source: $source)")
    }

    override fun onError(exception: Throwable, errorBitmap: Bitmap?) {
        imageView.hideLoading()
        errorBitmap?.let { imageView.setImageBitmap(it) }
        println("  [ImageViewTarget] 에러: ${exception.message}")
    }
}

/**
 * ImageView 확장 함수
 */
fun ImageView.load(url: String, block: ImageRequestBuilder.() -> Unit = {}): Job {
    val request = imageRequest(url, block)
    return ImageLoader.getInstance().enqueue(request, ImageViewTarget(this))
}

// ============================================================
// 데모
// ============================================================

fun main() = runBlocking {
    println("=== Image Loading Pattern - 이미지 갤러리 앱 ===\n")

    val loader = ImageLoader.create()

    // --- 시나리오 1: 기본 이미지 로드 ---
    println("--- 1. 기본 이미지 로드 (3단계 캐시) ---")
    val request1 = imageRequest("https://example.com/image1.jpg") {
        size(400, 300)
    }

    // 첫 번째 로드 (Network)
    val result1 = loader.load(request1)
    println("결과: $result1")

    // 두 번째 로드 (Memory Cache)
    println("\n  같은 이미지 다시 로드:")
    val result2 = loader.load(request1)
    println("결과: $result2")

    // --- 시나리오 2: Transformation 적용 ---
    println("\n--- 2. Transformation 적용 ---")
    val request2 = imageRequest("https://example.com/profile.jpg") {
        size(200, 200)
        circleCrop()
    }
    loader.load(request2)

    val request3 = imageRequest("https://example.com/background.jpg") {
        size(800, 600)
        blur(25)
        grayscale()
    }
    loader.load(request3)

    // --- 시나리오 3: ImageView에 로드 ---
    println("\n--- 3. ImageView에 로드 ---")
    val imageView = ImageView()
    val placeholder = Bitmap(100, 100) // 로딩 중 placeholder

    val job = imageView.load("https://example.com/photo.jpg") {
        size(300, 300)
        placeholder(placeholder)
        roundedCorners(16)
    }

    // 로드 완료 대기
    job.join()

    // --- 시나리오 4: 요청 취소 시뮬레이션 ---
    println("\n--- 4. 요청 취소 (스크롤 시뮬레이션) ---")
    val imageView2 = ImageView()

    val job1 = imageView2.load("https://example.com/scroll1.jpg") {
        size(200, 200)
    }

    // 빠른 스크롤 시뮬레이션 - 새 이미지 로드 (이전 취소)
    delay(100)
    println("  → 스크롤! 새 이미지 요청")
    val job2 = imageView2.load("https://example.com/scroll2.jpg") {
        size(200, 200)
    }

    job2.join()
    println("  job1 취소됨: ${job1.isCancelled}")

    // --- 시나리오 5: 캐시 정책 ---
    println("\n--- 5. 캐시 정책 ---")
    val networkOnlyRequest = imageRequest("https://example.com/fresh.jpg") {
        size(300, 300)
        cachePolicy(CachePolicy.NETWORK_ONLY)
    }
    println("  NETWORK_ONLY 정책으로 로드:")
    loader.load(networkOnlyRequest)

    // --- 시나리오 6: Flow로 로드 ---
    println("\n--- 6. Flow로 로드 (Placeholder → Result) ---")
    val flowRequest = imageRequest("https://example.com/stream.jpg") {
        size(400, 400)
        placeholder(Bitmap(50, 50))
    }

    loader.loadAsFlow(flowRequest).collect { result ->
        when (result) {
            is ImageResult.Success -> {
                println("  Flow emit: ${result.bitmap.width}x${result.bitmap.height} from ${result.source}")
            }
            is ImageResult.Error -> {
                println("  Flow emit: Error - ${result.exception.message}")
            }
        }
    }

    // --- 통계 출력 ---
    println("\n--- 7. 캐시 통계 ---")
    println(loader.getStats())

    println("\n=== Image Loading 핵심 원칙 ===")
    println("1. 3단계 캐시: Memory → Disk → Network")
    println("2. 다운샘플링: 표시 크기에 맞게 메모리 절약")
    println("3. Bitmap Pool: 재활용으로 GC 감소")
    println("4. 요청 취소: View 재사용 시 이전 요청 취소")
    println("5. Transformation: 이미지 변환 체인")
    println("6. Placeholder/Error: 로딩/에러 상태 표시")
    println("7. 비동기 처리: 메인 스레드 블로킹 방지")
}
