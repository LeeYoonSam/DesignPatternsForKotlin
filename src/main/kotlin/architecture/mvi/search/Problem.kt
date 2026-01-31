package architecture.mvi.search

/**
 * MVI (Model-View-Intent) Pattern - Problem
 *
 * 이 파일은 MVI를 적용하지 않았을 때 복잡한 UI 상태 관리에서
 * 발생하는 문제점을 보여줍니다.
 *
 * 예시: 상품 검색 화면
 *
 * 문제점:
 * 1. 상태가 여러 변수에 분산되어 불일치 발생
 * 2. 양방향 데이터 흐름으로 상태 추적 어려움
 * 3. 여러 이벤트가 동시에 상태를 수정하면 Race Condition 발생
 * 4. 어떤 경로로 현재 상태에 도달했는지 재현 불가
 * 5. 화면 재생성(회전) 시 상태 복원 어려움
 */

import java.time.LocalDateTime

// ========================================
// 문제가 있는 ViewModel 구현
// ========================================

/**
 * 문제 1: 상태가 여러 변수에 분산
 *
 * 각 상태 조각이 독립적으로 변경되어 불일치 발생 가능
 * ex) isLoading=true인데 products도 존재 → 어떤 상태?
 */
class ProblematicSearchViewModel {
    // 상태가 여러 변수에 흩어져 있음
    var query: String = ""
    var products: List<Product> = emptyList()
    var isLoading: Boolean = false
    var errorMessage: String? = null
    var selectedCategory: String? = null
    var sortOrder: String = "relevance"
    var currentPage: Int = 1
    var hasMorePages: Boolean = true
    var favorites: MutableSet<String> = mutableSetOf()

    // 상태 조합이 유효한지 확인하기 어려움
    // isLoading=true + errorMessage!=null → 가능한 상태인가?
    // products.isNotEmpty() + isLoading=true → 로딩 중에 이전 결과 표시?

    fun search(query: String) {
        this.query = query
        this.isLoading = true
        this.errorMessage = null  // 에러 초기화 잊으면?
        // this.products = emptyList()  // 이전 결과 초기화 잊으면?
        this.currentPage = 1

        try {
            // 비동기 호출 시뮬레이션
            val results = fetchProducts(query)
            this.products = results
            this.isLoading = false
            this.hasMorePages = results.size >= 20
        } catch (e: Exception) {
            this.errorMessage = e.message
            this.isLoading = false
            // products는 이전 상태 그대로 → 에러인데 이전 검색 결과 표시됨!
        }
    }

    fun loadNextPage() {
        if (!hasMorePages || isLoading) return

        isLoading = true
        currentPage++

        try {
            val moreResults = fetchProducts(query, currentPage)
            products = products + moreResults  // 기존 결과에 추가
            isLoading = false
            hasMorePages = moreResults.size >= 20
        } catch (e: Exception) {
            errorMessage = e.message
            isLoading = false
            currentPage--  // 롤백 잊으면?
        }
    }

    fun toggleFavorite(productId: String) {
        if (favorites.contains(productId)) {
            favorites.remove(productId)
        } else {
            favorites.add(productId)
        }
        // View에 변경 통지는? Observer 패턴 없이는 수동 갱신 필요
    }

    fun changeSort(sortOrder: String) {
        this.sortOrder = sortOrder
        // 정렬 변경 시 다시 검색해야 하는데 잊으면?
        search(query)
    }

    private fun fetchProducts(query: String, page: Int = 1): List<Product> {
        // 시뮬레이션
        if (query == "error") throw RuntimeException("네트워크 오류")
        return listOf(
            Product("1", "상품 $query - $page", (10000..50000).random().toDouble())
        )
    }
}

/**
 * 문제 2: View에서 직접 상태를 변경
 *
 * 양방향 바인딩으로 어디서 상태가 변경되었는지 추적 어려움
 */
class ProblematicSearchView {
    private val viewModel = ProblematicSearchViewModel()

    fun onSearchClicked(query: String) {
        // View에서 ViewModel 상태를 직접 변경
        viewModel.query = query
        viewModel.search(query)
    }

    fun onScrollToBottom() {
        viewModel.loadNextPage()
    }

    fun onSortChanged(sort: String) {
        // View에서 정렬을 바꾸고 검색도 직접 호출
        viewModel.sortOrder = sort  // 직접 상태 변경!
        viewModel.search(viewModel.query)
    }

    fun onFavoriteClicked(productId: String) {
        viewModel.toggleFavorite(productId)
        // View를 수동으로 갱신해야 함
        render()
    }

    fun render() {
        println("=== 화면 렌더링 ===")
        println("검색어: ${viewModel.query}")
        println("로딩: ${viewModel.isLoading}")
        println("에러: ${viewModel.errorMessage}")
        println("결과: ${viewModel.products.size}개")
        println("정렬: ${viewModel.sortOrder}")

        // 상태 불일치 가능:
        // - 로딩 중인데 에러 메시지가 남아있음
        // - 검색어가 바뀌었는데 이전 결과가 표시됨
    }
}

/**
 * 문제 3: 비동기 처리 시 Race Condition
 *
 * 빠르게 검색어를 바꾸면 이전 요청 결과가 나중에 도착하여
 * 현재 검색어와 다른 결과가 표시될 수 있음
 */
class RaceConditionProblem {
    private val viewModel = ProblematicSearchViewModel()

    fun demonstrate() {
        println("=== Race Condition 시나리오 ===")
        println()
        println("1. 사용자가 '노트북' 검색 → 요청 A 발송")
        println("2. 사용자가 바로 '키보드' 검색 → 요청 B 발송")
        println("3. 요청 B가 먼저 도착 → '키보드' 결과 표시")
        println("4. 요청 A가 나중에 도착 → '노트북' 결과로 덮어씌워짐!")
        println()
        println("결과: 검색어는 '키보드'인데 '노트북' 결과가 표시됨")
        println("원인: 상태 변경의 순서를 보장하지 않음")
    }
}

/**
 * 문제 4: 상태 재현 불가
 *
 * 버그 리포트가 들어왔을 때 사용자가 어떤 순서로 조작했는지,
 * 어떤 상태에서 문제가 발생했는지 알 수 없음
 */
class StateReproductionProblem {
    fun demonstrate() {
        println("=== 상태 재현 불가 문제 ===")
        println()
        println("버그 리포트: '검색 결과가 이상하게 표시됩니다'")
        println()
        println("디버깅 시도:")
        println("  Q: 어떤 검색어를 입력했나요?")
        println("  Q: 정렬 방식을 변경했나요?")
        println("  Q: 스크롤을 했나요?")
        println("  Q: 에러가 발생한 적이 있나요?")
        println()
        println("→ 상태 변경 이력이 없어 정확한 재현 불가")
        println("→ MVI에서는 Intent 이력으로 정확한 재현 가능")
    }
}

// ========================================
// 지원 클래스
// ========================================

data class Product(
    val id: String,
    val name: String,
    val price: Double
)

/**
 * 문제점 요약:
 *
 * 1. 분산된 가변 상태
 *    - 여러 var 변수가 독립적으로 변경됨
 *    - 상태 조합의 유효성 보장 불가
 *
 * 2. 양방향 데이터 흐름
 *    - View와 ViewModel이 서로 상태를 변경
 *    - 상태 변경 흐름 추적 어려움
 *
 * 3. Race Condition
 *    - 비동기 작업의 순서 보장 없음
 *    - 오래된 결과가 최신 상태를 덮어씌움
 *
 * 4. 디버깅/재현 어려움
 *    - 상태 변경 이력이 없음
 *    - 어떤 경로로 현재 상태에 도달했는지 알 수 없음
 *
 * 5. 테스트 어려움
 *    - 가변 상태가 많아 모든 조합 테스트 어려움
 *    - 상태 초기화 누락 시 테스트 불안정
 *
 * MVI Pattern으로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 구현을 확인하세요.
 */

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║           MVI Pattern 적용 전 문제점 데모                     ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    println("--- 1. 분산된 가변 상태 ---")
    val viewModel = ProblematicSearchViewModel()
    viewModel.search("노트북")
    println("query=${viewModel.query}, loading=${viewModel.isLoading}, error=${viewModel.errorMessage}")
    println("결과: ${viewModel.products}")
    println()

    viewModel.search("error")
    println("에러 후: query=${viewModel.query}, loading=${viewModel.isLoading}, error=${viewModel.errorMessage}")
    println("이전 결과 남아있음: ${viewModel.products}")
    println()

    println("--- 2. 양방향 데이터 흐름 ---")
    val view = ProblematicSearchView()
    view.onSearchClicked("키보드")
    view.render()
    println()

    println("--- 3. Race Condition ---")
    RaceConditionProblem().demonstrate()
    println()

    println("--- 4. 상태 재현 불가 ---")
    StateReproductionProblem().demonstrate()
    println()

    println("Solution.kt에서 MVI Pattern을 적용한 해결책을 확인하세요.")
}
