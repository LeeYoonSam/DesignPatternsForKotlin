package architecture.imageloading.gallery

import java.net.URL
import java.util.concurrent.Executors

/**
 * Image Loading Pattern - 문제 상황
 *
 * 이미지 갤러리 앱을 개발하고 있습니다.
 * 네트워크에서 이미지를 로드하여 목록으로 표시하는데,
 * 단순한 구현은 다양한 성능 문제와 사용자 경험 저하를 유발합니다.
 */

// ============================================================
// 이미지 모델
// ============================================================

data class ImageData(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val bytes: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageData) return false
        return url == other.url
    }

    override fun hashCode(): Int = url.hashCode()
}

// ============================================================
// ❌ 문제 1: 메인 스레드에서 네트워크 요청
// ============================================================

class MainThreadImageLoader {
    /**
     * 메인 스레드에서 직접 이미지 로드
     * → UI가 완전히 멈춤 (ANR 발생)
     */
    fun loadImage(url: String): ImageData {
        println("  [Main Thread] 이미지 로드 시작: $url")

        // 네트워크 요청 시뮬레이션 (2초 소요)
        Thread.sleep(2000)

        val bytes = ByteArray(1024 * 100) // 100KB 이미지
        println("  [Main Thread] 이미지 로드 완료")

        return ImageData(url, 800, 600, bytes)
    }

    fun demonstrate() {
        println("--- 메인 스레드 이미지 로드 문제 ---")
        println("  이미지 10개 로드 시:")
        println("  → 10 x 2초 = 20초 동안 UI 완전 멈춤!")
        println("  → Android에서 ANR (Application Not Responding)")
        println("  → iOS에서 워치독 킬")
        println()
        println("  ❌ 문제점:")
        println("    • 사용자가 아무 것도 할 수 없음")
        println("    • 스크롤, 터치 입력 불가")
        println("    • 앱이 멈춘 것처럼 보임")
    }
}

// ============================================================
// ❌ 문제 2: 캐싱 없이 매번 네트워크 요청
// ============================================================

class NoCacheImageLoader {
    private val executor = Executors.newFixedThreadPool(4)

    /**
     * 같은 이미지를 반복 요청해도 매번 네트워크 호출
     */
    fun loadImage(url: String, onComplete: (ImageData) -> Unit) {
        executor.submit {
            println("  [Network] 이미지 요청: $url")
            Thread.sleep(500) // 네트워크 지연
            val bytes = ByteArray(1024 * 100)
            onComplete(ImageData(url, 800, 600, bytes))
        }
    }

    fun demonstrate() {
        println("--- 캐싱 없는 이미지 로드 문제 ---")
        println()
        println("  사용자가 스크롤 → 이전 이미지 다시 보임")
        println("    → 같은 이미지를 다시 네트워크에서 로드!")
        println()
        println("  시나리오:")
        println("    1. 이미지 A 로드 (500ms, 100KB 네트워크)")
        println("    2. 스크롤 다운 → 이미지 A 화면에서 사라짐")
        println("    3. 스크롤 업 → 이미지 A 다시 보임")
        println("    4. 이미지 A 다시 로드! (500ms, 100KB 네트워크)")
        println()
        println("  ❌ 문제점:")
        println("    • 불필요한 네트워크 트래픽")
        println("    • 불필요한 배터리 소모")
        println("    • 느린 반응 속도")
        println("    • 데이터 요금 낭비")
    }
}

// ============================================================
// ❌ 문제 3: 메모리 관리 없이 이미지 저장
// ============================================================

class UnlimitedMemoryCacheLoader {
    // 이미지를 무한정 메모리에 저장
    private val memoryCache = mutableMapOf<String, ImageData>()

    fun loadImage(url: String): ImageData {
        return memoryCache.getOrPut(url) {
            // 네트워크에서 로드
            ImageData(url, 4000, 3000, ByteArray(1024 * 1024 * 10)) // 10MB 고해상도 이미지
        }
    }

    fun demonstrate() {
        println("--- 무제한 메모리 캐시 문제 ---")
        println()
        println("  고해상도 이미지 100장 로드:")
        println("    100 x 10MB = 1GB 메모리 사용!")
        println()
        println("  ❌ 문제점:")
        println("    • OutOfMemoryError (OOM)")
        println("    • 앱 강제 종료")
        println("    • 다른 앱/시스템에 영향")
        println("    • 사용하지 않는 이미지도 메모리 점유")
        println()
        println("  필요한 것:")
        println("    • LRU (Least Recently Used) 캐시")
        println("    • 메모리 제한 (예: 앱 힙의 1/8)")
        println("    • 약한 참조 (WeakReference)")
    }
}

// ============================================================
// ❌ 문제 4: 원본 크기 그대로 로드
// ============================================================

class NoDownsamplingLoader {
    fun loadImage(url: String, targetWidth: Int, targetHeight: Int): ImageData {
        // 4000x3000 원본 이미지를 그대로 로드
        val originalBytes = ByteArray(4000 * 3000 * 4) // 48MB ARGB

        // 100x100 썸네일로 표시하는데 48MB 메모리 사용!
        return ImageData(url, 4000, 3000, originalBytes)
    }

    fun demonstrate() {
        println("--- 다운샘플링 없는 로드 문제 ---")
        println()
        println("  원본: 4000 x 3000 (48MB)")
        println("  표시: 100 x 100 썸네일")
        println("  → 실제 필요: 100 x 100 x 4 = 40KB")
        println("  → 실제 사용: 48MB (1200배 낭비!)")
        println()
        println("  ❌ 문제점:")
        println("    • 메모리 극심한 낭비")
        println("    • 디코딩 시간 증가")
        println("    • GPU 렌더링 부하")
        println("    • 빠른 OOM 발생")
        println()
        println("  필요한 것:")
        println("    • inSampleSize 계산 (BitmapFactory.Options)")
        println("    • 표시 크기에 맞는 다운샘플링")
    }
}

// ============================================================
// ❌ 문제 5: 요청 취소 없이 로드
// ============================================================

class NoCancellationLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private var loadCount = 0

    fun loadImage(url: String, onComplete: (ImageData) -> Unit) {
        loadCount++
        val requestId = loadCount

        executor.submit {
            println("  [Request $requestId] 로드 시작: ${url.takeLast(20)}")
            Thread.sleep(1000) // 1초 소요

            // 사용자가 이미 스크롤해서 이 이미지가 필요 없어졌는데도
            // 로드 완료 후 콜백 호출!
            println("  [Request $requestId] 로드 완료 (필요 없을 수 있음)")
            onComplete(ImageData(url, 800, 600))
        }
    }

    fun demonstrate() {
        println("--- 취소 없는 이미지 로드 문제 ---")
        println()
        println("  시나리오:")
        println("    1. 화면에 이미지 1~10 표시 → 10개 로드 시작")
        println("    2. 빠르게 스크롤 → 이미지 11~20 표시")
        println("    3. 1~10 로드 완료되어도 이미 화면에 없음!")
        println("    4. 불필요한 네트워크/메모리/CPU 낭비")
        println()
        println("  ❌ 문제점:")
        println("    • 화면에 없는 이미지 계속 로드")
        println("    • 네트워크 대역폭 낭비")
        println("    • 현재 보이는 이미지 로드 지연")
        println("    • 배터리 낭비")
        println()
        println("  필요한 것:")
        println("    • 요청 취소 메커니즘")
        println("    • View 재사용 시 이전 요청 취소")
        println("    • 우선순위 기반 로드")
    }
}

// ============================================================
// ❌ 문제 6: Placeholder/Error 처리 없음
// ============================================================

class NoPlaceholderLoader {
    fun loadImage(url: String, onComplete: (ImageData?) -> Unit) {
        // 로드 시작 시 아무것도 표시 안 함
        // 로드 실패 시 아무것도 표시 안 함

        Thread {
            Thread.sleep(500)
            if (Math.random() > 0.3) {
                onComplete(ImageData(url, 800, 600))
            } else {
                // 에러 발생 - UI에 빈 공간만 남음
                onComplete(null)
            }
        }.start()
    }

    fun demonstrate() {
        println("--- Placeholder/Error 처리 없음 ---")
        println()
        println("  로딩 중: 빈 공간 (사용자가 깨진 건지 로딩 중인지 모름)")
        println("  에러 시: 빈 공간 (사용자가 재시도할 방법 없음)")
        println()
        println("  ❌ 문제점:")
        println("    • 로딩 중인지 알 수 없음")
        println("    • 실패했는지 알 수 없음")
        println("    • 레이아웃 깜빡임 (이미지 크기 변경)")
        println("    • 나쁜 사용자 경험")
        println()
        println("  필요한 것:")
        println("    • Placeholder 이미지 (로딩 중)")
        println("    • Error 이미지 (실패 시)")
        println("    • Crossfade 애니메이션")
        println("    • 재시도 버튼")
    }
}

// ============================================================
// ❌ 문제 7: 디스크 캐시 없음
// ============================================================

class NoDiskCacheLoader {
    private val memoryCache = object : LinkedHashMap<String, ImageData>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageData>?): Boolean {
            return size > 50 // 50개만 유지
        }
    }

    fun demonstrate() {
        println("--- 디스크 캐시 없음 ---")
        println()
        println("  시나리오:")
        println("    1. 앱 실행 → 이미지 50개 로드 (네트워크)")
        println("    2. 앱 종료")
        println("    3. 앱 재실행 → 같은 이미지 50개 다시 로드! (네트워크)")
        println()
        println("  ❌ 문제점:")
        println("    • 앱 재시작마다 네트워크 사용")
        println("    • 오프라인에서 이미지 표시 불가")
        println("    • 느린 콜드 스타트")
        println()
        println("  필요한 것:")
        println("    • 디스크 캐시 (DiskLruCache)")
        println("    • Memory → Disk → Network 3단계 조회")
        println("    • 캐시 만료 정책")
    }
}

// ============================================================
// ❌ 문제 8: Bitmap 재활용 없음
// ============================================================

class NoBitmapPoolLoader {
    fun loadImages(urls: List<String>): List<ImageData> {
        // 매 이미지마다 새 ByteArray 할당
        return urls.map { url ->
            ImageData(url, 800, 600, ByteArray(800 * 600 * 4)) // 1.9MB
        }
        // 100개 로드 → 190MB 할당 + 이전 100개 GC 대상
        // → GC 폭풍으로 UI 버벅임
    }

    fun demonstrate() {
        println("--- Bitmap Pool 없음 ---")
        println()
        println("  스크롤 시:")
        println("    새 이미지 로드 → 새 ByteArray 할당")
        println("    이전 이미지 → GC 대상")
        println("    → 빈번한 GC로 UI 버벅임 (Jank)")
        println()
        println("  ❌ 문제점:")
        println("    • 빈번한 메모리 할당/해제")
        println("    • GC 스파이크")
        println("    • 스크롤 끊김")
        println("    • 메모리 단편화")
        println()
        println("  필요한 것:")
        println("    • Bitmap Pool (재활용)")
        println("    • inBitmap 옵션 사용")
        println("    • 객체 풀링")
    }
}

fun main() {
    println("=== Image Loading Pattern - 문제 상황 ===\n")

    // 문제 1: 메인 스레드
    MainThreadImageLoader().demonstrate()
    println()

    // 문제 2: 캐싱 없음
    NoCacheImageLoader().demonstrate()
    println()

    // 문제 3: 무제한 메모리
    UnlimitedMemoryCacheLoader().demonstrate()
    println()

    // 문제 4: 다운샘플링 없음
    NoDownsamplingLoader().demonstrate()
    println()

    // 문제 5: 취소 없음
    NoCancellationLoader().demonstrate()
    println()

    // 문제 6: Placeholder 없음
    NoPlaceholderLoader().demonstrate()
    println()

    // 문제 7: 디스크 캐시 없음
    NoDiskCacheLoader().demonstrate()
    println()

    // 문제 8: Bitmap Pool 없음
    NoBitmapPoolLoader().demonstrate()

    println("\n핵심 문제:")
    println("• 메인 스레드 블로킹 → ANR")
    println("• 캐싱 없음 → 네트워크 낭비, 느린 반응")
    println("• 메모리 관리 없음 → OOM")
    println("• 다운샘플링 없음 → 메모리 낭비")
    println("• 취소 없음 → 불필요한 로드")
    println("• 디스크 캐시 없음 → 오프라인 불가")
    println("• Bitmap Pool 없음 → GC 스파이크")
}
