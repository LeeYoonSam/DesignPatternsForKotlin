# Unidirectional Data Flow (Redux) Pattern

## 개요

Unidirectional Data Flow(단방향 데이터 흐름)는 애플리케이션 상태를 **단일 저장소(Store)**에서 관리하고, **Action → Reducer → State** 방향으로만 데이터가 흐르게 하여 상태 변경을 **예측 가능**하고 **추적 가능**하게 만드는 패턴입니다.

## 핵심 원칙 (Redux 3원칙)

| 원칙 | 설명 |
|------|------|
| **Single Source of Truth** | 모든 상태는 하나의 Store에 저장 |
| **State is Read-Only** | 상태 변경은 오직 Action dispatch를 통해서만 |
| **Pure Reducers** | 상태 전이는 순수 함수(Reducer)로만 수행 |

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                           View (UI)                              │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │   • 상태를 구독하여 UI 렌더링                            │   │
│   │   • 사용자 이벤트를 Action으로 변환                      │   │
│   │   • store.state.collect { render(it) }                  │   │
│   └─────────────────────────┬───────────────────────────────┘   │
│                             │ dispatch(Action)                   │
└─────────────────────────────┼───────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Store                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │   state: StateFlow<AppState>                            │   │
│   │   dispatch(action: Action)                              │   │
│   │   select(selector): Flow<R>                             │   │
│   └─────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│   ┌─────────────────────────┼───────────────────────────────┐   │
│   │                   Middleware Chain                       │   │
│   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │   │
│   │  │ Logging  │→│  Thunk   │→│ Effects  │→│   ...    │   │   │
│   │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │   │
│   └─────────────────────────┬───────────────────────────────┘   │
│                             ▼                                    │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                      Reducer                             │   │
│   │   (state: S, action: Action) → S                        │   │
│   │   • 순수 함수 (Side Effect 없음)                         │   │
│   │   • 불변 상태 반환                                       │   │
│   └─────────────────────────┬───────────────────────────────┘   │
│                             │ newState                           │
│                             ▼                                    │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    State (Immutable)                     │   │
│   │   data class AppState(                                  │   │
│   │       counter: CounterState,                            │   │
│   │       cart: CartState,                                  │   │
│   │       user: UserState                                   │   │
│   │   )                                                     │   │
│   └─────────────────────────────────────────────────────────┘   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ state 변경 알림
                                ▼
                         View 자동 갱신
```

## 데이터 흐름

```
┌──────┐   dispatch   ┌──────────┐   process   ┌─────────────┐
│ View │ ──────────►  │Middleware│ ──────────► │   Reducer   │
└──────┘              └──────────┘             └──────┬──────┘
    ▲                                                 │
    │                 ┌──────────┐                    │
    └──────────────── │  Store   │ ◄──────────────────┘
       collect/       │  State   │    newState
       subscribe      └──────────┘
```

## 구성 요소

### 1. State (불변 상태)

```kotlin
data class AppState(
    val counter: CounterState = CounterState(),
    val cart: CartState = CartState(),
    val user: UserState = UserState()
)

data class CounterState(
    val count: Int = 0,
    val history: List<Int> = listOf(0)  // Undo용 이력
)
```

### 2. Action (상태 변경 의도)

```kotlin
sealed interface Action

sealed interface CounterAction : Action {
    object Increment : CounterAction
    object Decrement : CounterAction
    data class SetValue(val value: Int) : CounterAction
    object Undo : CounterAction
}
```

### 3. Reducer (순수 함수)

```kotlin
typealias Reducer<S> = (state: S, action: Action) -> S

val counterReducer: Reducer<CounterState> = { state, action ->
    when (action) {
        is CounterAction.Increment -> state.copy(
            count = state.count + 1,
            history = state.history + (state.count + 1)
        )
        is CounterAction.Decrement -> state.copy(count = state.count - 1)
        is CounterAction.Undo -> {
            val newHistory = state.history.dropLast(1)
            state.copy(count = newHistory.last(), history = newHistory)
        }
        else -> state
    }
}
```

### 4. Store

```kotlin
class Store<S>(
    initialState: S,
    private val reducer: Reducer<S>,
    private val middlewares: List<Middleware<S>> = emptyList()
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    fun dispatch(action: Action) {
        // Middleware 체인 → Reducer 실행
        _state.value = reducer(_state.value, action)
    }

    fun <R> select(selector: (S) -> R): Flow<R> =
        state.map(selector).distinctUntilChanged()
}
```

### 5. Middleware (Action 가로채기)

```kotlin
typealias Middleware<S> = (
    store: Store<S>,
    next: (Action) -> Unit,
    action: Action
) -> Unit

// 로깅 Middleware
val loggingMiddleware: Middleware<AppState> = { store, next, action ->
    println("Before: ${store.state.value}")
    println("Action: $action")
    next(action)
    println("After: ${store.state.value}")
}

// Thunk Middleware (비동기)
data class ThunkAction<S>(
    val thunk: suspend (dispatch: (Action) -> Unit, getState: () -> S) -> Unit
) : Action
```

### 6. Effect (Side Effect 처리)

```kotlin
interface Effect<S> {
    fun handle(action: Action, state: S, dispatch: (Action) -> Unit)
}

class SaveCartEffect : Effect<AppState> {
    override fun handle(action: Action, state: AppState, dispatch: (Action) -> Unit) {
        if (action is AsyncAction.SaveCart) {
            dispatch(UiAction.ShowLoading)
            // API 호출...
            dispatch(UiAction.HideLoading)
        }
    }
}
```

### 7. Selector (파생 상태)

```kotlin
object Selectors {
    val selectCount: (AppState) -> Int = { it.counter.count }
    val selectCartTotal: (AppState) -> Int = { it.cart.totalPrice }

    // 파생 상태
    val selectFinalPrice: (AppState) -> Int = { state ->
        val cartTotal = state.cart.totalPrice
        val discount = state.user.membershipLevel.discountRate
        (cartTotal * (1 - discount)).toInt()
    }
}

// 사용
store.select(Selectors.selectCount).collect { count ->
    updateCounterUI(count)
}
```

## Thunk를 통한 비동기 처리

```kotlin
val fetchProducts = ThunkAction<AppState> { dispatch, getState ->
    dispatch(UiAction.ShowLoading)
    try {
        val products = api.fetchProducts()
        dispatch(ProductAction.SetProducts(products))
    } catch (e: Exception) {
        dispatch(UiAction.ShowError(e.message))
    } finally {
        dispatch(UiAction.HideLoading)
    }
}

store.dispatch(fetchProducts)
```

## 시간 여행 디버깅

```kotlin
class TimeTravelDebugger<S>(
    private val reducer: Reducer<S>,
    private val initialState: S
) {
    private val snapshots = mutableListOf<StateSnapshot<S>>()

    fun record(action: Action, state: S)
    fun replayTo(index: Int): S
    fun undo(): S
    fun redo(): S
}

// 상태 이력 확인
debugger.printHistory()
// [0] Increment @ 10:30:01
// [1] AddItem @ 10:30:02
// [2] Login @ 10:30:03

// 특정 시점으로 되돌리기
val pastState = debugger.replayTo(1)
```

## Redux vs MVI 비교

| 측면 | Redux | MVI |
|------|-------|-----|
| 상태 범위 | 전역 (앱 전체) | 화면/컴포넌트 단위 |
| Action 이름 | Action | Intent |
| Side Effect | Middleware/Thunk | SideEffect sealed class |
| 상태 구독 | StateFlow + Selector | StateFlow |
| 주로 사용 | Web (React), Flutter | Android (Compose) |
| 복잡도 | 높음 (Middleware 체인) | 중간 |

## 장점

1. **예측 가능**: Action 로그만 보면 상태 변화 추적 가능
2. **테스트 용이**: Reducer가 순수 함수라 입출력 테스트 쉬움
3. **시간 여행**: 상태 이력으로 Undo/Redo, 디버깅 가능
4. **관심사 분리**: State, Action, Reducer, Effect 명확히 분리
5. **단일 진실 소스**: 모든 상태가 Store에 집중
6. **도구 지원**: Redux DevTools로 상태 모니터링

## 단점

1. **보일러플레이트**: Action, Reducer 정의에 코드량 증가
2. **학습 곡선**: Middleware, Thunk, Selector 등 개념 많음
3. **오버엔지니어링**: 작은 앱에는 과도할 수 있음
4. **성능**: 모든 Action이 전체 Reducer 체인 통과

## 적용 시점

- 복잡한 상태 관리가 필요한 앱
- 여러 화면에서 같은 상태를 공유해야 할 때
- 상태 변경 이력 추적이 필요할 때 (Undo/Redo)
- 팀 협업 시 상태 관리 규칙 통일이 필요할 때
- 디버깅이 어려운 복잡한 상태 흐름

## 실제 사례

| 플랫폼 | 라이브러리 |
|--------|------------|
| **Web (React)** | Redux, Redux Toolkit, Zustand |
| **Android** | Orbit MVI, Redux-Kotlin |
| **iOS** | ReSwift, TCA (The Composable Architecture) |
| **Flutter** | flutter_redux, Riverpod |
| **Kotlin Multiplatform** | Redux-Kotlin, MVIKotlin |

## 관련 패턴

- **MVI Pattern**: Redux의 화면 단위 적용
- **Observer Pattern**: Store 상태 구독에 사용
- **Command Pattern**: Action이 Command와 유사
- **Memento Pattern**: 시간 여행 디버깅에 사용
- **Chain of Responsibility**: Middleware 체인

## 참고 자료

- [Redux 공식 문서](https://redux.js.org/)
- [Redux Toolkit](https://redux-toolkit.js.org/)
- [Flux Architecture (Facebook)](https://facebook.github.io/flux/)
- [The Composable Architecture (iOS)](https://github.com/pointfreeco/swift-composable-architecture)
- [MVIKotlin](https://arkivanov.github.io/MVIKotlin/)
