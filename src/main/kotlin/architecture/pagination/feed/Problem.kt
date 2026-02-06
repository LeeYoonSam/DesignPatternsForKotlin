package architecture.pagination.feed

import java.time.LocalDateTime

/**
 * Pagination Pattern - 문제 상황
 *
 * SNS 피드 앱을 개발하고 있습니다.
 * 사용자의 게시물 목록을 서버에서 가져와 표시하는데,
 * 데이터가 많아질수록 다양한 문제가 발생합니다.
 */

// ============================================================
// 공통 모델
// ============================================================

data class Post(
    val id: Long,
    val authorId: String,
    val content: String,
    val createdAt: LocalDateTime,
    val likeCount: Int = 0,
    val commentCount: Int = 0
)

// ============================================================
// ❌ 문제 1: 전체 데이터 한 번에 로드
// ============================================================

/**
 * 모든 게시물을 한 번에 로드하는 방식
 * → 데이터가 많아지면 심각한 문제 발생
 */
class LoadAllRepository {
    private val posts = generatePosts(10_000) // 10,000개 게시물

    fun getAllPosts(): List<Post> {
        println("  [서버] 전체 게시물 로드: ${posts.size}개")
        // 실제로는 네트워크 요청
        Thread.sleep(3000) // 3초 지연 시뮬레이션
        return posts
    }

    private fun generatePosts(count: Int): List<Post> {
        return (1..count).map { i ->
            Post(
                id = i.toLong(),
                authorId = "user_${i % 100}",
                content = "게시물 내용 #$i " + "Lorem ipsum ".repeat(10),
                createdAt = LocalDateTime.now().minusHours(i.toLong()),
                likeCount = (0..1000).random(),
                commentCount = (0..100).random()
            )
        }
    }
}

class LoadAllProblem {
    fun demonstrate() {
        println("--- 전체 데이터 로드 문제 ---")
        val repo = LoadAllRepository()

        val startTime = System.currentTimeMillis()
        val posts = repo.getAllPosts()
        val elapsed = System.currentTimeMillis() - startTime

        println("  로드 시간: ${elapsed}ms")
        println("  메모리 사용 추정: ${estimateMemory(posts)} MB")
        println("  네트워크 전송량 추정: ${estimateNetworkSize(posts)} MB")
        println()
        println("  ❌ 문제점:")
        println("    • 초기 로딩 시간이 매우 김 (사용자 이탈)")
        println("    • 메모리 부족 (OOM 가능성)")
        println("    • 네트워크 대역폭 낭비")
        println("    • 배터리 소모")
        println("    • 사용자는 상위 20개만 볼 수도 있음")
    }

    private fun estimateMemory(posts: List<Post>): Int {
        // 대략적 추정: Post 당 약 500 bytes
        return (posts.size * 500) / (1024 * 1024)
    }

    private fun estimateNetworkSize(posts: List<Post>): Int {
        // JSON 직렬화 시 약 800 bytes/post 추정
        return (posts.size * 800) / (1024 * 1024)
    }
}

// ============================================================
// ❌ 문제 2: Offset 기반 페이지네이션의 한계
// ============================================================

/**
 * Offset(SKIP) 기반 페이지네이션
 * SQL: SELECT * FROM posts ORDER BY created_at DESC LIMIT 20 OFFSET 1000
 */
class OffsetPaginationRepository {
    private val allPosts = (1..10_000).map { i ->
        Post(
            id = i.toLong(),
            authorId = "user_${i % 100}",
            content = "게시물 #$i",
            createdAt = LocalDateTime.now().minusMinutes(i.toLong())
        )
    }.toMutableList()

    /**
     * Offset 기반 조회
     * 문제: OFFSET이 커질수록 성능이 급격히 저하됨
     */
    fun getPostsByOffset(page: Int, pageSize: Int = 20): List<Post> {
        val offset = page * pageSize

        // 실제 DB에서는 OFFSET만큼 스킵하기 위해 모든 행을 스캔해야 함
        // OFFSET 10000이면 10000개를 읽고 버린 후 20개를 반환
        println("  [DB] OFFSET $offset → ${offset}개 행 스캔 후 $pageSize개 반환")

        return allPosts.drop(offset).take(pageSize)
    }

    /**
     * 중간에 데이터가 추가/삭제되면 페이지 내용이 밀리거나 중복됨
     */
    fun demonstrateShiftProblem() {
        println("\n  --- Offset 기반의 데이터 변경 문제 ---")

        // 1페이지 조회
        val page1 = getPostsByOffset(0, 5)
        println("  1페이지: ${page1.map { it.id }}")

        // 중간에 새 게시물 추가 (맨 앞에)
        val newPost = Post(
            id = 10001,
            authorId = "new_user",
            content = "새 게시물!",
            createdAt = LocalDateTime.now()
        )
        allPosts.add(0, newPost)
        println("  → 새 게시물 추가 (ID: 10001)")

        // 2페이지 조회
        val page2 = getPostsByOffset(1, 5)
        println("  2페이지: ${page2.map { it.id }}")

        // 문제: 1페이지의 마지막 항목이 2페이지에 다시 나타남!
        val duplicates = page1.filter { p1 -> page2.any { p2 -> p1.id == p2.id } }
        if (duplicates.isNotEmpty()) {
            println("  ❌ 중복 발생! IDs: ${duplicates.map { it.id }}")
        }
    }
}

class OffsetProblem {
    fun demonstrate() {
        println("--- Offset 기반 페이지네이션 문제 ---")
        val repo = OffsetPaginationRepository()

        // 성능 문제: 페이지가 뒤로 갈수록 느려짐
        println("\n  페이지별 쿼리 비용:")
        listOf(0, 10, 100, 500).forEach { page ->
            repo.getPostsByOffset(page, 20)
        }
        println("  ❌ OFFSET이 클수록 더 많은 행을 스캔해야 함 (O(n) 복잡도)")

        // 데이터 변경 시 페이지 밀림 문제
        repo.demonstrateShiftProblem()
    }
}

// ============================================================
// ❌ 문제 3: 무한 스크롤 상태 관리 어려움
// ============================================================

class InfiniteScrollProblem {
    private var currentPage = 0
    private var isLoading = false
    private var hasMore = true
    private val loadedPosts = mutableListOf<Post>()
    private var error: String? = null

    fun demonstrate() {
        println("--- 무한 스크롤 상태 관리 문제 ---")
        println()
        println("  관리해야 할 상태들:")
        println("    • currentPage: $currentPage")
        println("    • isLoading: $isLoading")
        println("    • hasMore: $hasMore")
        println("    • loadedPosts: ${loadedPosts.size}개")
        println("    • error: $error")
        println()
        println("  ❌ 문제점:")
        println("    • 상태가 분산되어 있어 일관성 유지 어려움")
        println("    • 로딩 중 중복 요청 방지 로직 필요")
        println("    • 에러 발생 시 재시도 로직 필요")
        println("    • 새로고침 시 모든 상태 초기화 필요")
        println("    • 화면 회전/프로세스 종료 시 상태 복구")
        println("    • 캐시 무효화 시점 관리")
    }

    // 이런 코드가 여러 곳에 중복됨
    fun loadMore() {
        if (isLoading || !hasMore) return

        isLoading = true
        error = null

        try {
            // 네트워크 요청...
            val newPosts = emptyList<Post>() // API 호출

            loadedPosts.addAll(newPosts)
            currentPage++
            hasMore = newPosts.isNotEmpty()
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    fun refresh() {
        currentPage = 0
        isLoading = false
        hasMore = true
        loadedPosts.clear()
        error = null
        loadMore()
    }
}

// ============================================================
// ❌ 문제 4: 네트워크 + 로컬 캐시 동기화
// ============================================================

class CacheSyncProblem {
    fun demonstrate() {
        println("--- 네트워크 + 캐시 동기화 문제 ---")
        println()
        println("  시나리오:")
        println("    1. 로컬 캐시에서 1~20번 게시물 표시 (빠름)")
        println("    2. 서버에서 최신 데이터 요청")
        println("    3. 서버 응답: 15번 삭제됨, 21번 새로 추가")
        println("    4. 캐시와 UI를 어떻게 갱신?")
        println()
        println("  ❌ 문제점:")
        println("    • 캐시 데이터와 서버 데이터 병합 로직 복잡")
        println("    • 삭제된 항목 처리 (Tombstone? 전체 갱신?)")
        println("    • UI가 깜빡거리지 않게 부드럽게 갱신")
        println("    • 페이지 경계에서의 일관성")
        println("    • 캐시 만료 정책")
    }
}

// ============================================================
// ❌ 문제 5: RecyclerView/LazyColumn 통합 어려움
// ============================================================

class UiIntegrationProblem {
    fun demonstrate() {
        println("--- UI 컴포넌트 통합 문제 ---")
        println()
        println("  요구사항:")
        println("    • 스크롤이 끝에 도달하면 자동으로 다음 페이지 로드")
        println("    • 로딩 중일 때 하단에 프로그레스 표시")
        println("    • 에러 발생 시 재시도 버튼 표시")
        println("    • 당겨서 새로고침(Pull to Refresh)")
        println("    • 빈 상태일 때 안내 메시지")
        println()
        println("  ❌ 문제점:")
        println("    • 스크롤 위치 감지 + 로딩 트리거 로직 복잡")
        println("    • 로딩/에러/빈 상태를 리스트에 어떻게 표시?")
        println("    • 헤더/푸터 아이템 처리")
        println("    • DiffUtil 계산으로 부드러운 업데이트")
        println("    • ViewHolder 재활용 최적화")
    }
}

fun main() {
    println("=== Pagination Pattern - 문제 상황 ===\n")

    // 문제 1: 전체 로드
    LoadAllProblem().demonstrate()
    println()

    // 문제 2: Offset 기반
    OffsetProblem().demonstrate()
    println()

    // 문제 3: 무한 스크롤 상태
    InfiniteScrollProblem().demonstrate()
    println()

    // 문제 4: 캐시 동기화
    CacheSyncProblem().demonstrate()
    println()

    // 문제 5: UI 통합
    UiIntegrationProblem().demonstrate()

    println("\n핵심 문제:")
    println("• 대량 데이터를 한 번에 로드하면 성능/메모리/네트워크 문제")
    println("• Offset 기반은 대용량에서 느리고 데이터 변경 시 페이지 밀림")
    println("• 무한 스크롤 상태 관리 코드가 분산되고 복잡함")
    println("• 네트워크/캐시/UI 간 데이터 동기화가 어려움")
}
