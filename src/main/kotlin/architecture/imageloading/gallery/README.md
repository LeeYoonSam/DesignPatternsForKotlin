# Image Loading Pattern

## 개요

Image Loading Pattern은 네트워크 이미지를 효율적으로 로드, 캐싱, 표시하는 패턴입니다. **3단계 캐시**(Memory → Disk → Network), **다운샘플링**, **Bitmap Pool**, **요청 취소**, **Transformation** 등을 통합하여 메모리 효율성과 사용자 경험을 최적화합니다.

## 핵심 구성 요소

| 구성 요소 | 설명 |
|-----------|------|
| **ImageLoader** | 이미지 로딩 엔트리 포인트, 캐시/디코딩/변환 통합 |
| **MemoryCache** | LRU 기반 메모리 캐시 (빠른 접근) |
| **DiskCache** | 파일 기반 디스크 캐시 (영속성) |
| **Fetcher** | 네트워크에서 이미지 다운로드 |
| **Decoder** | 바이트 → Bitmap 디코딩 (다운샘플링 포함) |
| **BitmapPool** | Bitmap 재활용으로 GC 감소 |
| **Transformation** | 이미지 변환 (CircleCrop, Blur 등) |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                      ImageView.load(url)                         │
└─────────────────────────────┬───────────────────────────────────┘
                              │ ImageRequest
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ImageLoader                               │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   Cache Lookup                            │   │
│  │                                                           │   │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │   │
│  │   │   Memory    │───►│    Disk     │───►│   Network   │  │   │
│  │   │   Cache     │    │    Cache    │    │   Fetcher   │  │   │
│  │   │   (LRU)     │    │  (LruDisk)  │    │             │  │   │
│  │   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘  │   │
│  │          │ HIT              │ HIT              │         │   │
│  │          ▼                  ▼                  ▼         │   │
│  │   ┌─────────────────────────────────────────────────┐    │   │
│  │   │                    Decoder                       │    │   │
│  │   │  • inSampleSize 계산 (다운샘플링)               │    │   │
│  │   │  • BitmapPool에서 재활용                        │    │   │
│  │   └─────────────────────────┬───────────────────────┘    │   │
│  │                             │                             │   │
│  │   ┌─────────────────────────▼───────────────────────┐    │   │
│  │   │               Transformations                    │    │   │
│  │   │  CircleCrop → RoundedCorners → Blur → ...       │    │   │
│  │   └─────────────────────────┬───────────────────────┘    │   │
│  │                             │                             │   │
│  └─────────────────────────────┼────────────────────────────┘   │
│                                │                                 │
│   ┌────────────────────────────▼────────────────────────────┐   │
│   │                     BitmapPool                           │   │
│   │  • Bitmap 재활용으로 GC 감소                            │   │
│   │  • inBitmap 옵션 지원                                   │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────┬───────────────────────────────────┘
                              │ ImageResult
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ImageTarget                                 │
│  • onStart(placeholder)                                          │
│  • onSuccess(bitmap, source)                                     │
│  • onError(exception, errorBitmap)                               │
└─────────────────────────────────────────────────────────────────┘
```

## 3단계 캐시 흐름

```
이미지 요청
    │
    ▼
┌───────────────┐
│ Memory Cache  │ ─── HIT ──► 즉시 반환 (1ms 이하)
│   (LRU)       │
└───────┬───────┘
        │ MISS
        ▼
┌───────────────┐
│  Disk Cache   │ ─── HIT ──► 디코딩 → Memory에 저장 → 반환 (10~50ms)
│ (DiskLruCache)│
└───────┬───────┘
        │ MISS
        ▼
┌───────────────┐
│   Network     │ ─── 다운로드 → Disk에 저장 → 디코딩 → Memory에 저장
│   Fetcher     │                                          (100~1000ms)
└───────────────┘
```

## 다운샘플링 (inSampleSize)

```kotlin
// 원본: 4000x3000 (48MB)
// 표시: 400x300

fun calculateInSampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
    var inSampleSize = 1
    while (srcH / inSampleSize >= targetH * 2 &&
           srcW / inSampleSize >= targetW * 2) {
        inSampleSize *= 2
    }
    return inSampleSize  // 8
}

// 결과: 500x375 (750KB) - 64배 메모리 절약!
```

## 주요 구현

### ImageRequest (DSL)

```kotlin
val request = imageRequest("https://example.com/image.jpg") {
    size(400, 300)
    placeholder(placeholderBitmap)
    error(errorBitmap)
    circleCrop()
    roundedCorners(16)
    cachePolicy(CachePolicy.ALL)
    priority(Priority.HIGH)
}
```

### ImageLoader

```kotlin
class ImageLoader {
    suspend fun load(request: ImageRequest): ImageResult {
        // 1. Memory Cache 확인
        memoryCache.get(request.cacheKey)?.let { return Success(it, MEMORY) }

        // 2. Disk Cache 확인
        diskCache.get(request.cacheKey)?.let { data ->
            val bitmap = decoder.decode(data, request.targetWidth, request.targetHeight)
            memoryCache.put(request.cacheKey, bitmap)
            return Success(bitmap, DISK)
        }

        // 3. Network에서 로드
        val data = fetcher.fetch(request.url)
        diskCache.put(request.cacheKey, data)
        val bitmap = decoder.decode(data, request.targetWidth, request.targetHeight)
        val transformed = applyTransformations(bitmap, request.transformations)
        memoryCache.put(request.cacheKey, transformed)
        return Success(transformed, NETWORK)
    }
}
```

### Transformation Chain

```kotlin
interface Transformation {
    val key: String
    fun transform(bitmap: Bitmap): Bitmap
}

class CircleCropTransformation : Transformation {
    override val key = "circle_crop"
    override fun transform(bitmap: Bitmap): Bitmap {
        // 원형으로 크롭
    }
}

// 체인 적용
val transformations = listOf(CircleCrop(), RoundedCorners(16), Blur(25))
var result = bitmap
for (t in transformations) {
    result = t.transform(result)
}
```

### BitmapPool

```kotlin
class BitmapPool(private val maxSize: Int) {
    private val pool = mutableListOf<Bitmap>()

    fun get(width: Int, height: Int, config: Config): Bitmap {
        // 재활용 가능한 Bitmap 찾기
        val reusable = pool.find { it.width == width && it.height == height }
        return reusable ?: Bitmap.createBitmap(width, height, config)
    }

    fun put(bitmap: Bitmap) {
        if (currentSize + bitmap.byteCount <= maxSize) {
            pool.add(bitmap)
        }
    }
}
```

### 요청 취소 (View 재사용)

```kotlin
class ImageLoader {
    private val activeRequests = ConcurrentHashMap<String, Job>()

    fun enqueue(request: ImageRequest, target: ImageTarget): Job {
        val targetKey = System.identityHashCode(target).toString()

        // 이전 요청 취소
        activeRequests[targetKey]?.cancel()

        val job = scope.launch {
            val result = load(request)
            target.onSuccess(result.bitmap)
        }

        activeRequests[targetKey] = job
        return job
    }
}

// RecyclerView에서
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.imageView.load(items[position].imageUrl)  // 자동 취소
}
```

## LRU Cache 구현

```kotlin
class MemoryCache(private val maxSize: Int) {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Entry<String, Bitmap>?): Boolean {
            return currentSize > maxSize  // 오래된 항목 자동 제거
        }
    }
}
```

## 장점

1. **메모리 효율**: 다운샘플링 + LRU 캐시 + BitmapPool
2. **네트워크 절약**: 3단계 캐시로 불필요한 요청 방지
3. **빠른 응답**: Memory Cache에서 즉시 반환
4. **오프라인 지원**: Disk Cache로 오프라인에서도 이미지 표시
5. **부드러운 스크롤**: 요청 취소 + GC 감소
6. **Placeholder/Error**: 로딩/에러 상태 시각적 피드백
7. **Transformation**: 다양한 이미지 변환 지원

## 단점

1. **복잡도**: 캐시/풀/디코더 등 많은 컴포넌트 관리
2. **디스크 공간**: 캐시가 저장 공간 사용
3. **캐시 무효화**: 서버 이미지 변경 시 캐시 갱신 전략 필요
4. **메모리 튜닝**: 적절한 캐시 크기 설정 필요

## 적용 시점

- 이미지 갤러리 앱
- SNS 피드 (Instagram, Facebook)
- E-commerce 상품 목록
- 뉴스/미디어 앱
- 채팅 앱 (프로필, 미디어)
- 지도 앱 (타일 이미지)

## 실제 라이브러리

| 플랫폼 | 라이브러리 |
|--------|------------|
| **Android** | Glide, Coil, Picasso, Fresco |
| **iOS** | Kingfisher, SDWebImage, Nuke |
| **Flutter** | cached_network_image |
| **Web** | react-lazy-load-image |
| **Kotlin Multiplatform** | Coil 3 (MP 지원) |

## Glide vs Coil 비교

| 특성 | Glide | Coil |
|------|-------|------|
| 언어 | Java | Kotlin |
| 코루틴 | 미지원 | 네이티브 |
| Compose | 별도 라이브러리 | 내장 지원 |
| 크기 | ~500KB | ~250KB |
| BitmapPool | 내장 | 내장 |
| Transformation | RequestBuilder | ImageRequest |

## 관련 패턴

- **Cache-Aside Pattern**: 캐시 조회 로직
- **Object Pool Pattern**: BitmapPool
- **Chain of Responsibility**: Transformation 체인
- **Decorator Pattern**: Transformation이 Bitmap을 감싸서 변환
- **Builder Pattern**: ImageRequest 빌더
- **Singleton Pattern**: ImageLoader 인스턴스 관리

## 참고 자료

- [Glide GitHub](https://github.com/bumptech/glide)
- [Coil GitHub](https://github.com/coil-kt/coil)
- [Android Bitmap 최적화](https://developer.android.com/topic/performance/graphics)
- [LruCache 문서](https://developer.android.com/reference/android/util/LruCache)
- [Fresco Architecture](https://frescolib.org/docs/architecture.html)
