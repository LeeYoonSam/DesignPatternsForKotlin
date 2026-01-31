# MVI (Model-View-Intent) Pattern

## 개요

MVI(Model-View-Intent)는 **단방향 데이터 흐름(Unidirectional Data Flow)**을 기반으로 하는 UI 아키텍처 패턴입니다. 함수형 프로그래밍과 리액티브 프로그래밍에서 영감을 받아 만들어졌으며, Jetpack Compose나 SwiftUI와 같은 선언형 UI 프레임워크와 특히 잘 어울립니다.

## MVVM vs MVI

| 측면 | MVVM | MVI |
|------|------|-----|
| 상태 관리 | 여러 Observable 변수 | 단일 불변 State 객체 |
| 데이터 흐름 | 양방향 바인딩 | 단방향 (Intent → State → View) |
| 사용자 입력 | View가 직접 ViewModel 호출 | Intent로 추상화 |
| 상태 일관성 | 분산 상태 불일치 가능 | 단일 상태로 불일치 불가 |
| 디버깅 | 상태 추적 어려움 | Intent 이력으로 재현 가능 |
| 테스트 | ViewModel 테스트 | 순수 함수 Reducer 테스트 |

## 핵심 개념

### 데이터 흐름

```
┌──────┐   Intent   ┌───────────┐   Result   ┌─────────┐   State   ┌──────┐
│ View │ ─────────► │ ViewModel │ ─────────► │ Reducer │ ────────► │ View │
│      │            │ (Store)   │            │(순수함수)│           │      │
│ 사용자│ ◄───────── │           │            │         │           │렌더링 │
│ 조작  │   State   │           │            │         │           │      │
└──────┘            └───────────┘            └─────────┘           └──────┘
                          │
                    Side Effect
                     (Toast, Navigation)
```

### 1. Model (State) - 불변 상태

화면에 필요한 모든 정보를 담은 **단일 불변 데이터 클래스**입니다.

```kotlin
data class SearchState(
    val query: String = "",
    val products: List<ProductItem> = emptyList(),
    val screenState: ScreenState = ScreenState.Initial,
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val favorites: Set<String> = emptySet()
) {
    // 화면 상태를 sealed class로 명확히 구분
    // → isLoading=true이면서 error!=null인 불일치 불가
    sealed class ScreenState {
        object Initial : ScreenState()
        data class Loading(val isLoadingMore: Boolean = false) : ScreenState()
        object Content : ScreenState()
        data class Empty(val query: String) : ScreenState()
        data class Error(val message: String) : ScreenState()
    }
}
```

### 2. Intent - 사용자의 의도

사용자가 수행할 수 있는 모든 액션을 sealed class로 정의합니다.

```kotlin
sealed class SearchIntent {
    data class Search(val query: String) : SearchIntent()
    object LoadNextPage : SearchIntent()
    data class ChangeSort(val sortOrder: SortOrder) : SearchIntent()
    data class ToggleFavorite(val productId: String) : SearchIntent()
    object Retry : SearchIntent()
    object ClearSearch : SearchIntent()
}
```

### 3. Reducer - 순수 함수 상태 전환

현재 상태 + 결과 → 새로운 상태를 반환하는 **순수 함수**입니다.

```kotlin
object SearchReducer {
    fun reduce(currentState: SearchState, result: Result): SearchState {
        return when (result) {
            is Result.SearchStarted -> currentState.copy(
                query = result.query,
                screenState = ScreenState.Loading(),
                products = emptyList()  // 이전 결과 확실히 초기화
            )
            is Result.SearchSuccess -> currentState.copy(
                screenState = if (result.products.isEmpty()) {
                    ScreenState.Empty(result.query)
                } else {
                    ScreenState.Content
                },
                products = result.products
            )
            is Result.SearchError -> currentState.copy(
                screenState = ScreenState.Error(result.message)
            )
            // ...
        }
    }
}
```

### 4. ViewModel (Store) - Intent 처리

Intent를 받아 비동기 작업을 수행하고 Reducer를 통해 상태를 변환합니다.

```kotlin
class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun processIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> handleSearch(intent.query)
            is SearchIntent.ToggleFavorite -> handleToggleFavorite(intent.productId)
            // ...
        }
    }

    private fun handleSearch(query: String) {
        searchJob?.cancel()  // Race Condition 방지
        reduce(Result.SearchStarted(query))

        searchJob = viewModelScope.launch {
            try {
                val result = repository.search(query)
                reduce(Result.SearchSuccess(query, result.products))
            } catch (e: Exception) {
                reduce(Result.SearchError(e.message))
            }
        }
    }

    private fun reduce(result: Result) {
        _state.value = SearchReducer.reduce(_state.value, result)
    }
}
```

### 5. View - 렌더링 + Intent 발행

```kotlin
// Jetpack Compose 예시
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsState()

    Column {
        SearchBar(
            query = state.query,
            onSearch = { viewModel.processIntent(SearchIntent.Search(it)) }
        )

        when (val screenState = state.screenState) {
            is ScreenState.Loading -> LoadingIndicator()
            is ScreenState.Content -> ProductList(
                products = state.products,
                onFavorite = { viewModel.processIntent(SearchIntent.ToggleFavorite(it)) },
                onScrollEnd = { viewModel.processIntent(SearchIntent.LoadNextPage) }
            )
            is ScreenState.Error -> ErrorView(
                message = screenState.message,
                onRetry = { viewModel.processIntent(SearchIntent.Retry) }
            )
            is ScreenState.Empty -> EmptyView(query = screenState.query)
            is ScreenState.Initial -> WelcomeView()
        }
    }
}
```

## Side Effect (일회성 이벤트)

상태에 포함되지 않는 일회성 이벤트(토스트, 네비게이션)를 별도 채널로 처리합니다.

```kotlin
sealed class SearchSideEffect {
    data class ShowToast(val message: String) : SearchSideEffect()
    data class NavigateToDetail(val productId: String) : SearchSideEffect()
    object ScrollToTop : SearchSideEffect()
}

// ViewModel에서
private val _sideEffects = Channel<SearchSideEffect>()
val sideEffects = _sideEffects.receiveAsFlow()

// View에서 수집
LaunchedEffect(Unit) {
    viewModel.sideEffects.collect { effect ->
        when (effect) {
            is ShowToast -> snackbarHostState.showSnackbar(effect.message)
            is NavigateToDetail -> navController.navigate("detail/${effect.productId}")
            is ScrollToTop -> listState.animateScrollToItem(0)
        }
    }
}
```

## Race Condition 방지

```kotlin
private var searchJob: Job? = null

private fun handleSearch(query: String) {
    // 이전 검색 취소 → 오래된 결과가 최신 상태를 덮어쓰는 문제 방지
    searchJob?.cancel()

    searchJob = viewModelScope.launch {
        reduce(Result.SearchStarted(query))
        try {
            val result = repository.search(query)
            reduce(Result.SearchSuccess(query, result.products))
        } catch (e: CancellationException) {
            // 취소된 요청은 무시 (새 검색이 시작됨)
        } catch (e: Exception) {
            reduce(Result.SearchError(e.message))
        }
    }
}
```

## 테스트 용이성

Reducer가 순수 함수이므로 단위 테스트가 매우 쉽습니다.

```kotlin
class SearchReducerTest {

    @Test
    fun `검색 시작 시 로딩 상태로 전환되고 이전 결과가 초기화된다`() {
        val initial = SearchState(
            products = listOf(ProductItem("1", "이전 결과", 100.0))
        )

        val result = SearchReducer.reduce(
            initial,
            Result.SearchStarted("새 검색어")
        )

        assertEquals("새 검색어", result.query)
        assertIs<ScreenState.Loading>(result.screenState)
        assertTrue(result.products.isEmpty())
    }

    @Test
    fun `즐겨찾기 토글이 올바르게 동작한다`() {
        val state = SearchState(favorites = setOf("1", "2"))

        // 추가
        val added = SearchReducer.reduce(state, Result.FavoriteToggled("3"))
        assertContains(added.favorites, "3")

        // 제거
        val removed = SearchReducer.reduce(state, Result.FavoriteToggled("1"))
        assertFalse("1" in removed.favorites)
    }
}
```

## 상태 재현 (Time Travel Debugging)

Intent 이력을 통해 정확한 상태 재현이 가능합니다.

```kotlin
// Intent 이력 기록
val intentHistory = mutableListOf<Pair<Timestamp, SearchIntent>>()

fun processIntent(intent: SearchIntent) {
    intentHistory.add(now() to intent)
    // ...
}

// 버그 재현: 기록된 Intent를 순서대로 재생
fun replay(intents: List<SearchIntent>): SearchState {
    var state = SearchState()
    intents.forEach { intent ->
        state = processIntentSync(state, intent)
    }
    return state  // 정확히 동일한 최종 상태
}
```

## Android에서의 적용

### Jetpack Compose + MVI

```kotlin
// build.gradle.kts
dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.orbit-mvi:orbit-viewmodel:6.1.0")  // MVI 라이브러리
}
```

### 인기 MVI 라이브러리

| 라이브러리 | 특징 |
|-----------|------|
| **Orbit MVI** | 간단한 API, Compose 지원 |
| **MVIKotlin** | JetBrains, 멀티플랫폼 |
| **Mavericks** | Airbnb, Fragment 기반 |
| **FlowRedux** | Redux 스타일, Flow 기반 |

## 장점

1. **단일 불변 상태**: 상태 불일치 원천 차단
2. **단방향 흐름**: 상태 변경 흐름 추적 용이
3. **상태 재현**: Intent 이력으로 정확한 버그 재현 가능
4. **순수 함수 테스트**: Reducer 테스트가 매우 쉬움
5. **예측 가능**: 동일 Intent 순서 → 동일 최종 상태
6. **Compose 친화**: 선언형 UI와 자연스러운 조합

## 단점

1. **보일러플레이트**: State, Intent, Result, Reducer 등 많은 클래스 필요
2. **학습 곡선**: 함수형/리액티브 사고 방식 필요
3. **간단한 화면에는 과도**: 단순 CRUD 화면에는 MVVM이 적합
4. **상태 객체 크기**: 복잡한 화면은 State 객체가 커질 수 있음

## 적용 시점

- 복잡한 상태를 가진 화면 (검색, 필터, 페이지네이션 조합)
- 여러 비동기 작업이 동시에 상태에 영향을 주는 경우
- 디버깅/재현이 중요한 프로덕션 앱
- Jetpack Compose / SwiftUI 기반 앱
- 상태 일관성이 매우 중요한 경우 (결제, 주문 화면)

## 관련 패턴

- **MVVM**: MVI의 전신, 양방향 바인딩 기반
- **Redux**: MVI와 유사한 웹 프론트엔드 패턴 (Action → Reducer → Store)
- **Observer Pattern**: State 변경 알림에 사용
- **Command Pattern**: Intent가 Command와 유사한 역할
- **State Pattern**: 화면 상태를 sealed class로 표현

## 참고 자료

- [Hannes Dorfmann - MVI on Android](http://hannesdorfmann.com/android/model-view-intent/)
- [Orbit MVI](https://orbit-mvi.org/)
- [MVIKotlin by JetBrains](https://github.com/arkivanov/MVIKotlin)
- [Google - Guide to App Architecture](https://developer.android.com/topic/architecture)
