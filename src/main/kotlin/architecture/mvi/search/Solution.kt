package architecture.mvi.search

/**
 * MVI (Model-View-Intent) Pattern - Solution
 *
 * MVI는 단방향 데이터 흐름(Unidirectional Data Flow)을 기반으로 하는
 * UI 아키텍처 패턴입니다.
 *
 * 핵심 개념:
 * - Model: 불변(Immutable) UI 상태. 화면에 표시되는 모든 정보를 담은 단일 객체
 * - View: Model을 렌더링하고, 사용자 조작을 Intent로 변환
 * - Intent: 사용자의 의도를 나타내는 이벤트 (검색, 정렬 변경, 즐겨찾기 등)
 *
 * 데이터 흐름:
 *   View → Intent → Reducer → State(Model) → View
 *
 * 장점:
 * - 단일 불변 상태로 불일치 불가
 * - 단방향 흐름으로 상태 추적 용이
 * - Intent 로그로 정확한 상태 재현 가능
 * - 순수 함수(Reducer)로 테스트 용이
 */

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime

// ========================================
// Model: 불변 UI 상태
// ========================================

/**
 * 화면의 전체 상태를 표현하는 단일 불변 데이터 클래스
 *
 * 모든 가능한 상태가 하나의 객체에 명확히 정의됨
 * → isLoading=true이면서 error!=null인 불일치 상태가 발생하지 않음
 */
data class SearchState(
    val query: String = "",
    val products: List<ProductItem> = emptyList(),
    val screenState: ScreenState = ScreenState.Initial,
    val selectedCategory: Category? = null,
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val currentPage: Int = 1,
    val hasMorePages: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val totalResults: Int = 0
) {
    /**
     * 화면 상태를 sealed class로 명확히 구분
     * → 동시에 두 가지 상태가 될 수 없음
     */
    sealed class ScreenState {
        /** 초기 상태 (검색 전) */
        object Initial : ScreenState() {
            override fun toString() = "Initial"
        }

        /** 로딩 중 */
        data class Loading(val isLoadingMore: Boolean = false) : ScreenState()

        /** 결과 표시 */
        object Content : ScreenState() {
            override fun toString() = "Content"
        }

        /** 결과 없음 */
        data class Empty(val query: String) : ScreenState()

        /** 에러 */
        data class Error(val message: String, val retryable: Boolean = true) : ScreenState()
    }
}

data class ProductItem(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val imageUrl: String = ""
)

enum class Category(val displayName: String) {
    ELECTRONICS("전자기기"),
    CLOTHING("의류"),
    BOOKS("도서"),
    FOOD("식품")
}

enum class SortOrder(val displayName: String) {
    RELEVANCE("관련도"),
    PRICE_LOW("가격 낮은순"),
    PRICE_HIGH("가격 높은순"),
    NEWEST("최신순")
}

// ========================================
// Intent: 사용자의 의도
// ========================================

/**
 * 사용자가 수행할 수 있는 모든 액션을 sealed class로 정의
 * → 누락 없이 모든 케이스 처리 보장 (when exhaustive)
 */
sealed class SearchIntent {
    /** 검색 실행 */
    data class Search(val query: String) : SearchIntent()

    /** 다음 페이지 로드 */
    object LoadNextPage : SearchIntent()

    /** 카테고리 필터 변경 */
    data class ChangeCategory(val category: Category?) : SearchIntent()

    /** 정렬 변경 */
    data class ChangeSort(val sortOrder: SortOrder) : SearchIntent()

    /** 즐겨찾기 토글 */
    data class ToggleFavorite(val productId: String) : SearchIntent()

    /** 에러 후 재시도 */
    object Retry : SearchIntent()

    /** 검색어 초기화 */
    object ClearSearch : SearchIntent()
}

// ========================================
// Side Effect: UI에서 한 번만 처리되는 이벤트
// ========================================

/**
 * 상태에 포함되지 않는 일회성 이벤트
 * (토스트, 네비게이션, 스낵바 등)
 */
sealed class SearchSideEffect {
    data class ShowToast(val message: String) : SearchSideEffect()
    data class NavigateToDetail(val productId: String) : SearchSideEffect()
    object ScrollToTop : SearchSideEffect()
}

// ========================================
// Reducer: 순수 함수로 상태 변환
// ========================================

/**
 * Reducer는 (현재 상태, 결과) → 새로운 상태 를 반환하는 순수 함수
 *
 * 순수 함수이므로:
 * - 동일 입력에 동일 출력 보장
 * - 부수효과 없음
 * - 테스트가 매우 쉬움
 */
object SearchReducer {

    /**
     * 내부 결과 타입: Intent 처리 후의 중간 결과
     */
    sealed class Result {
        data class SearchStarted(val query: String) : Result()
        data class SearchSuccess(
            val query: String,
            val products: List<ProductItem>,
            val totalResults: Int,
            val hasMore: Boolean
        ) : Result()
        data class SearchError(val message: String) : Result()
        data class LoadMoreStarted(val nextPage: Int) : Result()
        data class LoadMoreSuccess(
            val products: List<ProductItem>,
            val hasMore: Boolean
        ) : Result()
        data class LoadMoreError(val message: String) : Result()
        data class CategoryChanged(val category: Category?) : Result()
        data class SortChanged(val sortOrder: SortOrder) : Result()
        data class FavoriteToggled(val productId: String) : Result()
        object SearchCleared : Result()
    }

    /**
     * 핵심: 상태 전이 함수
     *
     * 현재 상태 + 결과 → 새로운 상태 (불변)
     */
    fun reduce(currentState: SearchState, result: Result): SearchState {
        return when (result) {
            is Result.SearchStarted -> currentState.copy(
                query = result.query,
                screenState = SearchState.ScreenState.Loading(),
                currentPage = 1,
                products = emptyList() // 이전 결과 초기화
            )

            is Result.SearchSuccess -> currentState.copy(
                screenState = if (result.products.isEmpty()) {
                    SearchState.ScreenState.Empty(result.query)
                } else {
                    SearchState.ScreenState.Content
                },
                products = result.products,
                totalResults = result.totalResults,
                hasMorePages = result.hasMore
            )

            is Result.SearchError -> currentState.copy(
                screenState = SearchState.ScreenState.Error(result.message)
            )

            is Result.LoadMoreStarted -> currentState.copy(
                screenState = SearchState.ScreenState.Loading(isLoadingMore = true),
                currentPage = result.nextPage
            )

            is Result.LoadMoreSuccess -> currentState.copy(
                screenState = SearchState.ScreenState.Content,
                products = currentState.products + result.products,
                hasMorePages = result.hasMore
            )

            is Result.LoadMoreError -> currentState.copy(
                screenState = SearchState.ScreenState.Content, // 기존 결과는 유지
                currentPage = currentState.currentPage - 1  // 페이지 롤백
            )

            is Result.CategoryChanged -> currentState.copy(
                selectedCategory = result.category
            )

            is Result.SortChanged -> currentState.copy(
                sortOrder = result.sortOrder
            )

            is Result.FavoriteToggled -> {
                val newFavorites = if (result.productId in currentState.favorites) {
                    currentState.favorites - result.productId
                } else {
                    currentState.favorites + result.productId
                }
                currentState.copy(favorites = newFavorites)
            }

            is Result.SearchCleared -> SearchState() // 초기 상태로 리셋
        }
    }
}

// ========================================
// Repository (데이터 소스)
// ========================================

class ProductRepository {
    private val allProducts = listOf(
        ProductItem("1", "맥북 프로 16인치", 3200000.0, "전자기기"),
        ProductItem("2", "기계식 키보드", 150000.0, "전자기기"),
        ProductItem("3", "울트라와이드 모니터", 800000.0, "전자기기"),
        ProductItem("4", "무선 마우스", 89000.0, "전자기기"),
        ProductItem("5", "프로그래밍 서적", 35000.0, "도서"),
        ProductItem("6", "코틀린 인 액션", 42000.0, "도서"),
        ProductItem("7", "캐주얼 후드티", 45000.0, "의류"),
        ProductItem("8", "USB-C 허브", 65000.0, "전자기기"),
        ProductItem("9", "노이즈캔슬링 헤드폰", 350000.0, "전자기기"),
        ProductItem("10", "개발자 티셔츠", 25000.0, "의류")
    )

    suspend fun searchProducts(
        query: String,
        category: Category? = null,
        sortOrder: SortOrder = SortOrder.RELEVANCE,
        page: Int = 1,
        pageSize: Int = 5
    ): SearchResult {
        // 네트워크 지연 시뮬레이션
        delay(500)

        if (query == "error") throw RuntimeException("네트워크 연결 실패")

        var filtered = allProducts.filter {
            it.name.contains(query, ignoreCase = true)
        }

        if (category != null) {
            filtered = filtered.filter { it.category == category.displayName }
        }

        val sorted = when (sortOrder) {
            SortOrder.PRICE_LOW -> filtered.sortedBy { it.price }
            SortOrder.PRICE_HIGH -> filtered.sortedByDescending { it.price }
            SortOrder.NEWEST -> filtered.reversed()
            SortOrder.RELEVANCE -> filtered
        }

        val startIndex = (page - 1) * pageSize
        val pagedResults = sorted.drop(startIndex).take(pageSize)

        return SearchResult(
            products = pagedResults,
            totalResults = sorted.size,
            hasMore = startIndex + pageSize < sorted.size
        )
    }

    data class SearchResult(
        val products: List<ProductItem>,
        val totalResults: Int,
        val hasMore: Boolean
    )
}

// ========================================
// ViewModel (Store): Intent 처리 + 상태 관리
// ========================================

/**
 * MVI Store: Intent를 받아 처리하고 State를 발행
 *
 * 단방향 흐름:
 * Intent → processIntent() → Result → Reducer → State → View
 */
class SearchViewModel(
    private val repository: ProductRepository = ProductRepository()
) {
    // 불변 상태 스트림
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    // 일회성 이벤트
    private val _sideEffects = MutableSharedFlow<SearchSideEffect>()
    val sideEffects: SharedFlow<SearchSideEffect> = _sideEffects.asSharedFlow()

    // Intent 이력 (디버깅/재현용)
    private val intentHistory = mutableListOf<Pair<LocalDateTime, SearchIntent>>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job? = null

    /**
     * Intent 처리 진입점
     *
     * View는 이 함수만 호출하면 됨
     * → 상태를 직접 변경하지 않음 (단방향)
     */
    fun processIntent(intent: SearchIntent) {
        // Intent 이력 기록
        intentHistory.add(LocalDateTime.now() to intent)

        when (intent) {
            is SearchIntent.Search -> handleSearch(intent.query)
            is SearchIntent.LoadNextPage -> handleLoadNextPage()
            is SearchIntent.ChangeCategory -> handleChangeCategory(intent.category)
            is SearchIntent.ChangeSort -> handleChangeSort(intent.sortOrder)
            is SearchIntent.ToggleFavorite -> handleToggleFavorite(intent.productId)
            is SearchIntent.Retry -> handleRetry()
            is SearchIntent.ClearSearch -> handleClearSearch()
        }
    }

    private fun handleSearch(query: String) {
        if (query.isBlank()) return

        // 이전 검색 취소 → Race Condition 방지
        searchJob?.cancel()

        // 로딩 상태로 전환
        reduce(SearchReducer.Result.SearchStarted(query))

        searchJob = scope.launch {
            try {
                val result = repository.searchProducts(
                    query = query,
                    category = _state.value.selectedCategory,
                    sortOrder = _state.value.sortOrder
                )
                reduce(SearchReducer.Result.SearchSuccess(
                    query = query,
                    products = result.products,
                    totalResults = result.totalResults,
                    hasMore = result.hasMore
                ))
                _sideEffects.emit(SearchSideEffect.ScrollToTop)
            } catch (e: Exception) {
                reduce(SearchReducer.Result.SearchError(
                    e.message ?: "알 수 없는 오류"
                ))
            }
        }
    }

    private fun handleLoadNextPage() {
        val currentState = _state.value
        if (!currentState.hasMorePages) return
        if (currentState.screenState is SearchState.ScreenState.Loading) return

        val nextPage = currentState.currentPage + 1
        reduce(SearchReducer.Result.LoadMoreStarted(nextPage))

        scope.launch {
            try {
                val result = repository.searchProducts(
                    query = currentState.query,
                    category = currentState.selectedCategory,
                    sortOrder = currentState.sortOrder,
                    page = nextPage
                )
                reduce(SearchReducer.Result.LoadMoreSuccess(
                    products = result.products,
                    hasMore = result.hasMore
                ))
            } catch (e: Exception) {
                reduce(SearchReducer.Result.LoadMoreError(e.message ?: "로드 실패"))
                _sideEffects.emit(SearchSideEffect.ShowToast("추가 로드 실패"))
            }
        }
    }

    private fun handleChangeCategory(category: Category?) {
        reduce(SearchReducer.Result.CategoryChanged(category))
        // 카테고리 변경 시 자동 재검색
        if (_state.value.query.isNotBlank()) {
            handleSearch(_state.value.query)
        }
    }

    private fun handleChangeSort(sortOrder: SortOrder) {
        reduce(SearchReducer.Result.SortChanged(sortOrder))
        if (_state.value.query.isNotBlank()) {
            handleSearch(_state.value.query)
        }
    }

    private fun handleToggleFavorite(productId: String) {
        reduce(SearchReducer.Result.FavoriteToggled(productId))
        val isFavorite = productId in _state.value.favorites
        scope.launch {
            _sideEffects.emit(SearchSideEffect.ShowToast(
                if (isFavorite) "즐겨찾기 추가" else "즐겨찾기 해제"
            ))
        }
    }

    private fun handleRetry() {
        val query = _state.value.query
        if (query.isNotBlank()) handleSearch(query)
    }

    private fun handleClearSearch() {
        searchJob?.cancel()
        reduce(SearchReducer.Result.SearchCleared)
    }

    /**
     * Reducer를 통한 상태 변환
     *
     * 모든 상태 변경은 이 함수를 통해서만 이루어짐
     */
    private fun reduce(result: SearchReducer.Result) {
        val currentState = _state.value
        val newState = SearchReducer.reduce(currentState, result)
        _state.value = newState
    }

    /**
     * Intent 이력 조회 (디버깅용)
     */
    fun getIntentHistory(): List<Pair<LocalDateTime, SearchIntent>> =
        intentHistory.toList()

    fun destroy() {
        scope.cancel()
    }
}

// ========================================
// View: 상태를 렌더링하고 Intent를 발행
// ========================================

/**
 * View는 두 가지 역할만 수행:
 * 1. State를 화면에 렌더링
 * 2. 사용자 조작을 Intent로 변환하여 ViewModel에 전달
 */
class SearchView(private val viewModel: SearchViewModel) {

    fun render(state: SearchState) {
        println("┌────────────────────────────────────────┐")
        println("│  🔍 검색: ${state.query.ifBlank { "(입력해주세요)" }}")
        println("│  카테고리: ${state.selectedCategory?.displayName ?: "전체"}")
        println("│  정렬: ${state.sortOrder.displayName}")
        println("├────────────────────────────────────────┤")

        when (val screenState = state.screenState) {
            is SearchState.ScreenState.Initial -> {
                println("│  검색어를 입력해주세요")
            }

            is SearchState.ScreenState.Loading -> {
                if (screenState.isLoadingMore) {
                    renderProducts(state)
                    println("│  [추가 로딩 중...]")
                } else {
                    println("│  [검색 중...]")
                }
            }

            is SearchState.ScreenState.Content -> {
                println("│  총 ${state.totalResults}개 결과")
                renderProducts(state)
                if (state.hasMorePages) {
                    println("│  [↓ 더 보기]")
                }
            }

            is SearchState.ScreenState.Empty -> {
                println("│  '${screenState.query}'에 대한 결과가 없습니다")
            }

            is SearchState.ScreenState.Error -> {
                println("│  ❌ 오류: ${screenState.message}")
                if (screenState.retryable) {
                    println("│  [다시 시도]")
                }
            }
        }

        println("└────────────────────────────────────────┘")
    }

    private fun renderProducts(state: SearchState) {
        state.products.forEach { product ->
            val favIcon = if (product.id in state.favorites) "★" else "☆"
            println("│  $favIcon ${product.name} - ${String.format("%,.0f", product.price)}원")
        }
    }

    // === 사용자 액션 → Intent 변환 ===

    fun onSearchSubmit(query: String) {
        viewModel.processIntent(SearchIntent.Search(query))
    }

    fun onScrollToBottom() {
        viewModel.processIntent(SearchIntent.LoadNextPage)
    }

    fun onCategorySelected(category: Category?) {
        viewModel.processIntent(SearchIntent.ChangeCategory(category))
    }

    fun onSortSelected(sortOrder: SortOrder) {
        viewModel.processIntent(SearchIntent.ChangeSort(sortOrder))
    }

    fun onFavoriteClicked(productId: String) {
        viewModel.processIntent(SearchIntent.ToggleFavorite(productId))
    }

    fun onRetryClicked() {
        viewModel.processIntent(SearchIntent.Retry)
    }

    fun onClearClicked() {
        viewModel.processIntent(SearchIntent.ClearSearch)
    }
}

// ========================================
// Testing (순수 함수 Reducer 테스트)
// ========================================

object ReducerTests {

    fun runAll() {
        testSearchStarted()
        testSearchSuccess()
        testSearchError()
        testFavoriteToggle()
        testClearSearch()
        println("모든 Reducer 테스트 통과!")
    }

    private fun testSearchStarted() {
        val initial = SearchState(products = listOf(ProductItem("1", "이전 결과", 100.0, "전자")))
        val result = SearchReducer.reduce(
            initial,
            SearchReducer.Result.SearchStarted("새 검색어")
        )

        assert(result.query == "새 검색어") { "검색어 업데이트 실패" }
        assert(result.screenState is SearchState.ScreenState.Loading) { "로딩 상태 전환 실패" }
        assert(result.products.isEmpty()) { "이전 결과 초기화 실패" }
        assert(result.currentPage == 1) { "페이지 리셋 실패" }
        println("  ✅ testSearchStarted 통과")
    }

    private fun testSearchSuccess() {
        val loading = SearchState(
            query = "키보드",
            screenState = SearchState.ScreenState.Loading()
        )
        val products = listOf(ProductItem("1", "기계식 키보드", 150000.0, "전자기기"))
        val result = SearchReducer.reduce(
            loading,
            SearchReducer.Result.SearchSuccess("키보드", products, 1, false)
        )

        assert(result.screenState is SearchState.ScreenState.Content) { "Content 상태 전환 실패" }
        assert(result.products.size == 1) { "상품 목록 업데이트 실패" }
        assert(!result.hasMorePages) { "hasMorePages 업데이트 실패" }
        println("  ✅ testSearchSuccess 통과")
    }

    private fun testSearchError() {
        val loading = SearchState(screenState = SearchState.ScreenState.Loading())
        val result = SearchReducer.reduce(
            loading,
            SearchReducer.Result.SearchError("네트워크 오류")
        )

        assert(result.screenState is SearchState.ScreenState.Error) { "Error 상태 전환 실패" }
        val errorState = result.screenState as SearchState.ScreenState.Error
        assert(errorState.message == "네트워크 오류") { "에러 메시지 불일치" }
        println("  ✅ testSearchError 통과")
    }

    private fun testFavoriteToggle() {
        val state = SearchState(favorites = setOf("1", "2"))

        // 추가
        val added = SearchReducer.reduce(state, SearchReducer.Result.FavoriteToggled("3"))
        assert("3" in added.favorites) { "즐겨찾기 추가 실패" }

        // 제거
        val removed = SearchReducer.reduce(state, SearchReducer.Result.FavoriteToggled("1"))
        assert("1" !in removed.favorites) { "즐겨찾기 제거 실패" }
        println("  ✅ testFavoriteToggle 통과")
    }

    private fun testClearSearch() {
        val state = SearchState(
            query = "키보드",
            products = listOf(ProductItem("1", "제품", 100.0, "전자")),
            screenState = SearchState.ScreenState.Content
        )
        val result = SearchReducer.reduce(state, SearchReducer.Result.SearchCleared)

        assert(result.query == "") { "검색어 초기화 실패" }
        assert(result.products.isEmpty()) { "결과 초기화 실패" }
        assert(result.screenState is SearchState.ScreenState.Initial) { "초기 상태 전환 실패" }
        println("  ✅ testClearSearch 통과")
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║          MVI Pattern - 상품 검색 화면 데모                     ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val viewModel = SearchViewModel()
    val view = SearchView(viewModel)

    runBlocking {
        // State 구독 (View가 자동으로 렌더링)
        val renderJob = launch {
            viewModel.state.collect { state ->
                view.render(state)
                println()
            }
        }

        // SideEffect 구독
        val effectJob = launch {
            viewModel.sideEffects.collect { effect ->
                when (effect) {
                    is SearchSideEffect.ShowToast -> println("🔔 Toast: ${effect.message}")
                    is SearchSideEffect.ScrollToTop -> println("📜 스크롤 맨 위로")
                    is SearchSideEffect.NavigateToDetail -> println("➡️ 상세 화면: ${effect.productId}")
                }
            }
        }

        // === 사용자 시나리오 ===

        println("=== 1. 검색 ===")
        view.onSearchSubmit("키보드")
        delay(1000)

        println("=== 2. 즐겨찾기 ===")
        view.onFavoriteClicked("2")
        delay(300)

        println("=== 3. 정렬 변경 ===")
        view.onSortSelected(SortOrder.PRICE_LOW)
        delay(1000)

        println("=== 4. 에러 시나리오 ===")
        view.onSearchSubmit("error")
        delay(1000)

        println("=== 5. 재시도 ===")
        view.onSearchSubmit("모니터")
        delay(1000)

        println("=== 6. 검색 초기화 ===")
        view.onClearClicked()
        delay(300)

        // === Reducer 단위 테스트 ===
        println("=== Reducer 단위 테스트 ===")
        ReducerTests.runAll()
        println()

        // === Intent 이력 ===
        println("=== Intent 이력 (디버깅용) ===")
        viewModel.getIntentHistory().forEachIndexed { index, (time, intent) ->
            println("  ${index + 1}. [$time] $intent")
        }
        println()

        renderJob.cancel()
        effectJob.cancel()
        viewModel.destroy()
    }

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                      MVI Pattern 장점                        ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 단일 불변 상태: 상태 불일치 원천 차단                    ║")
    println("║ 2. 단방향 흐름: Intent → Reducer → State → View            ║")
    println("║ 3. 상태 재현: Intent 이력으로 정확한 버그 재현 가능         ║")
    println("║ 4. 순수 함수: Reducer 테스트가 매우 쉬움                    ║")
    println("║ 5. 예측 가능: 동일 Intent 순서 → 동일 최종 상태            ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
