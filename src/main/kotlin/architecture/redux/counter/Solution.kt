package architecture.redux.counter

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime

/**
 * Unidirectional Data Flow (Redux) Pattern - 해결책
 *
 * 쇼핑 앱에 단방향 데이터 흐름을 적용하여:
 * - 모든 상태를 단일 Store에서 관리 (Single Source of Truth)
 * - Action을 통해서만 상태 변경 (예측 가능)
 * - Reducer는 순수 함수로 상태 전이 (테스트 용이)
 * - Side Effect는 Middleware/Effect에서 처리 (관심사 분리)
 *
 * 핵심 구성:
 * - State: 애플리케이션의 전체 상태 (불변)
 * - Action: 상태 변경 의도를 나타내는 객체
 * - Reducer: (State, Action) → State 순수 함수
 * - Store: State 보관 및 dispatch/subscribe 관리
 * - Middleware: Action 가로채기 (로깅, 비동기 처리)
 * - Effect: Side Effect 처리 (API 호출, 네비게이션)
 */

// ============================================================
// 1. State - 불변 상태 정의
// ============================================================

/**
 * 앱의 전체 상태 (Single Source of Truth)
 * data class로 정의하여 불변성 보장
 */
data class AppState(
    val counter: CounterState = CounterState(),
    val cart: CartState = CartState(),
    val user: UserState = UserState(),
    val ui: UiState = UiState()
) {
    companion object {
        val INITIAL = AppState()
    }
}

data class CounterState(
    val count: Int = 0,
    val history: List<Int> = listOf(0)  // Undo를 위한 이력
)

data class CartState(
    val items: List<CartItemState> = emptyList(),
    val totalPrice: Int = 0,
    val discountCode: String? = null,
    val discountRate: Double = 0.0
)

data class CartItemState(
    val productId: String,
    val productName: String,
    val price: Int,
    val quantity: Int
)

data class UserState(
    val isLoggedIn: Boolean = false,
    val userId: String? = null,
    val userName: String? = null,
    val membershipLevel: MembershipLevel = MembershipLevel.BASIC
)

enum class MembershipLevel(val discountRate: Double) {
    BASIC(0.0),
    SILVER(0.05),
    GOLD(0.10),
    PLATINUM(0.15)
}

data class UiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null
)

// ============================================================
// 2. Action - 상태 변경 의도
// ============================================================

/**
 * 모든 Action의 마커 인터페이스
 * sealed interface로 정의하여 exhaustive when 사용 가능
 */
sealed interface Action

// Counter Actions
sealed interface CounterAction : Action {
    object Increment : CounterAction
    object Decrement : CounterAction
    data class SetValue(val value: Int) : CounterAction
    object Undo : CounterAction
    object Reset : CounterAction
}

// Cart Actions
sealed interface CartAction : Action {
    data class AddItem(val productId: String, val name: String, val price: Int) : CartAction
    data class RemoveItem(val productId: String) : CartAction
    data class UpdateQuantity(val productId: String, val quantity: Int) : CartAction
    data class ApplyDiscount(val code: String) : CartAction
    object ClearCart : CartAction
}

// User Actions
sealed interface UserAction : Action {
    data class Login(val userId: String, val userName: String) : UserAction
    object Logout : UserAction
    data class UpdateMembership(val level: MembershipLevel) : UserAction
}

// UI Actions
sealed interface UiAction : Action {
    object ShowLoading : UiAction
    object HideLoading : UiAction
    data class ShowError(val message: String) : UiAction
    object ClearError : UiAction
    data class ShowSnackbar(val message: String) : UiAction
    object ClearSnackbar : UiAction
}

// Async Actions (Side Effect 트리거)
sealed interface AsyncAction : Action {
    data class FetchProducts(val category: String) : AsyncAction
    data class SaveCart(val items: List<CartItemState>) : AsyncAction
    data class ValidateDiscount(val code: String) : AsyncAction
}

// ============================================================
// 3. Reducer - 순수 함수로 상태 전이
// ============================================================

/**
 * Reducer 타입: (State, Action) → State
 * 순수 함수: 같은 입력에 항상 같은 출력, Side Effect 없음
 */
typealias Reducer<S> = (state: S, action: Action) -> S

/**
 * Counter Reducer
 */
val counterReducer: Reducer<CounterState> = { state, action ->
    when (action) {
        is CounterAction.Increment -> state.copy(
            count = state.count + 1,
            history = state.history + (state.count + 1)
        )
        is CounterAction.Decrement -> state.copy(
            count = state.count - 1,
            history = state.history + (state.count - 1)
        )
        is CounterAction.SetValue -> state.copy(
            count = action.value,
            history = state.history + action.value
        )
        is CounterAction.Undo -> {
            if (state.history.size > 1) {
                val newHistory = state.history.dropLast(1)
                state.copy(
                    count = newHistory.last(),
                    history = newHistory
                )
            } else state
        }
        is CounterAction.Reset -> CounterState()
        else -> state
    }
}

/**
 * Cart Reducer
 */
val cartReducer: Reducer<CartState> = { state, action ->
    when (action) {
        is CartAction.AddItem -> {
            val existingItem = state.items.find { it.productId == action.productId }
            val newItems = if (existingItem != null) {
                state.items.map {
                    if (it.productId == action.productId)
                        it.copy(quantity = it.quantity + 1)
                    else it
                }
            } else {
                state.items + CartItemState(
                    productId = action.productId,
                    productName = action.name,
                    price = action.price,
                    quantity = 1
                )
            }
            state.copy(
                items = newItems,
                totalPrice = calculateTotal(newItems, state.discountRate)
            )
        }
        is CartAction.RemoveItem -> {
            val newItems = state.items.filter { it.productId != action.productId }
            state.copy(
                items = newItems,
                totalPrice = calculateTotal(newItems, state.discountRate)
            )
        }
        is CartAction.UpdateQuantity -> {
            val newItems = if (action.quantity <= 0) {
                state.items.filter { it.productId != action.productId }
            } else {
                state.items.map {
                    if (it.productId == action.productId)
                        it.copy(quantity = action.quantity)
                    else it
                }
            }
            state.copy(
                items = newItems,
                totalPrice = calculateTotal(newItems, state.discountRate)
            )
        }
        is CartAction.ApplyDiscount -> {
            // 실제로는 서버 검증 필요 (AsyncAction으로 처리)
            val rate = when (action.code.uppercase()) {
                "SAVE10" -> 0.10
                "SAVE20" -> 0.20
                "VIP30" -> 0.30
                else -> 0.0
            }
            state.copy(
                discountCode = if (rate > 0) action.code else null,
                discountRate = rate,
                totalPrice = calculateTotal(state.items, rate)
            )
        }
        is CartAction.ClearCart -> CartState()
        else -> state
    }
}

private fun calculateTotal(items: List<CartItemState>, discountRate: Double): Int {
    val subtotal = items.sumOf { it.price * it.quantity }
    return (subtotal * (1 - discountRate)).toInt()
}

/**
 * User Reducer
 */
val userReducer: Reducer<UserState> = { state, action ->
    when (action) {
        is UserAction.Login -> state.copy(
            isLoggedIn = true,
            userId = action.userId,
            userName = action.userName
        )
        is UserAction.Logout -> UserState()
        is UserAction.UpdateMembership -> state.copy(
            membershipLevel = action.level
        )
        else -> state
    }
}

/**
 * UI Reducer
 */
val uiReducer: Reducer<UiState> = { state, action ->
    when (action) {
        is UiAction.ShowLoading -> state.copy(isLoading = true)
        is UiAction.HideLoading -> state.copy(isLoading = false)
        is UiAction.ShowError -> state.copy(error = action.message, isLoading = false)
        is UiAction.ClearError -> state.copy(error = null)
        is UiAction.ShowSnackbar -> state.copy(snackbarMessage = action.message)
        is UiAction.ClearSnackbar -> state.copy(snackbarMessage = null)
        else -> state
    }
}

/**
 * Root Reducer - 모든 하위 Reducer를 결합
 */
val rootReducer: Reducer<AppState> = { state, action ->
    AppState(
        counter = counterReducer(state.counter, action),
        cart = cartReducer(state.cart, action),
        user = userReducer(state.user, action),
        ui = uiReducer(state.ui, action)
    )
}

// ============================================================
// 4. Middleware - Action 가로채기
// ============================================================

/**
 * Middleware 타입
 * Action을 가로채서 로깅, 변환, 비동기 처리 등을 수행
 */
typealias Middleware<S> = (
    store: Store<S>,
    next: (Action) -> Unit,
    action: Action
) -> Unit

/**
 * 로깅 Middleware - 모든 Action과 상태 변화를 로그
 */
fun <S> loggingMiddleware(): Middleware<S> = { store, next, action ->
    val prevState = store.state.value
    println("  ┌─ Action: $action")
    next(action)
    val nextState = store.state.value
    println("  └─ State: $prevState → $nextState")
}

/**
 * 액션 기록 Middleware - 시간 여행 디버깅용
 */
data class ActionRecord(
    val action: Action,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

class ActionRecorderMiddleware<S> : (Store<S>, (Action) -> Unit, Action) -> Unit {
    private val _records = mutableListOf<ActionRecord>()
    val records: List<ActionRecord> get() = _records.toList()

    override fun invoke(store: Store<S>, next: (Action) -> Unit, action: Action) {
        _records.add(ActionRecord(action))
        next(action)
    }

    fun clear() = _records.clear()
}

/**
 * Thunk Middleware - 비동기 Action 처리
 */
typealias Thunk<S> = suspend (dispatch: (Action) -> Unit, getState: () -> S) -> Unit

data class ThunkAction<S>(val thunk: Thunk<S>) : Action

fun <S> thunkMiddleware(scope: CoroutineScope): Middleware<S> = { store, next, action ->
    when (action) {
        is ThunkAction<*> -> {
            @Suppress("UNCHECKED_CAST")
            val thunk = action.thunk as Thunk<S>
            scope.launch {
                thunk(
                    { store.dispatch(it) },
                    { store.state.value }
                )
            }
        }
        else -> next(action)
    }
}

// ============================================================
// 5. Store - 상태 저장소
// ============================================================

/**
 * Store - Single Source of Truth
 * 상태 보관, Action dispatch, 상태 구독 관리
 */
class Store<S>(
    initialState: S,
    private val reducer: Reducer<S>,
    private val middlewares: List<Middleware<S>> = emptyList()
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Action을 dispatch하여 상태 변경
     * Middleware 체인을 통과한 후 Reducer 실행
     */
    fun dispatch(action: Action) {
        val chain = middlewares.foldRight<Middleware<S>, (Action) -> Unit>(
            { a -> _state.value = reducer(_state.value, a) }
        ) { middleware, next ->
            { a -> middleware(this, next, a) }
        }
        chain(action)
    }

    /**
     * 특정 상태 조각만 구독 (Selector)
     */
    fun <R> select(selector: (S) -> R): Flow<R> {
        return state.map(selector).distinctUntilChanged()
    }
}

// ============================================================
// 6. Effect - Side Effect 처리
// ============================================================

/**
 * Effect 인터페이스
 * 특정 Action에 반응하여 Side Effect 실행 후 새 Action dispatch
 */
interface Effect<S> {
    fun handle(action: Action, state: S, dispatch: (Action) -> Unit)
}

/**
 * 장바구니 저장 Effect
 */
class SaveCartEffect : Effect<AppState> {
    override fun handle(action: Action, state: AppState, dispatch: (Action) -> Unit) {
        if (action is AsyncAction.SaveCart) {
            println("    [Effect] 장바구니 저장 중... (${action.items.size}개 상품)")
            dispatch(UiAction.ShowLoading)

            // 비동기 저장 시뮬레이션
            GlobalScope.launch {
                delay(500)
                println("    [Effect] 장바구니 저장 완료")
                dispatch(UiAction.HideLoading)
                dispatch(UiAction.ShowSnackbar("장바구니가 저장되었습니다"))
            }
        }
    }
}

/**
 * 할인 코드 검증 Effect
 */
class ValidateDiscountEffect : Effect<AppState> {
    override fun handle(action: Action, state: AppState, dispatch: (Action) -> Unit) {
        if (action is AsyncAction.ValidateDiscount) {
            println("    [Effect] 할인 코드 검증 중: ${action.code}")
            dispatch(UiAction.ShowLoading)

            GlobalScope.launch {
                delay(300)
                // 가상의 서버 검증
                val isValid = action.code.uppercase() in listOf("SAVE10", "SAVE20", "VIP30")
                dispatch(UiAction.HideLoading)

                if (isValid) {
                    dispatch(CartAction.ApplyDiscount(action.code))
                    dispatch(UiAction.ShowSnackbar("할인 코드가 적용되었습니다"))
                } else {
                    dispatch(UiAction.ShowError("유효하지 않은 할인 코드입니다"))
                }
            }
        }
    }
}

/**
 * Effect Manager - 모든 Effect를 관리하는 Middleware
 */
class EffectManager<S>(
    private val effects: List<Effect<S>>
) : Middleware<S> {
    override fun invoke(store: Store<S>, next: (Action) -> Unit, action: Action) {
        next(action) // 먼저 Reducer 실행
        // 그 후 Effect 실행
        effects.forEach { effect ->
            effect.handle(action, store.state.value, store::dispatch)
        }
    }
}

// ============================================================
// 7. Selector - 파생 상태 계산
// ============================================================

/**
 * Selector - 상태에서 필요한 정보를 추출/계산하는 함수
 * Memoization으로 불필요한 재계산 방지 가능
 */
object Selectors {
    // Counter
    val selectCount: (AppState) -> Int = { it.counter.count }
    val selectCanUndo: (AppState) -> Boolean = { it.counter.history.size > 1 }

    // Cart
    val selectCartItems: (AppState) -> List<CartItemState> = { it.cart.items }
    val selectCartItemCount: (AppState) -> Int = { it.cart.items.sumOf { item -> item.quantity } }
    val selectCartTotal: (AppState) -> Int = { it.cart.totalPrice }
    val selectHasDiscount: (AppState) -> Boolean = { it.cart.discountCode != null }

    // 파생 상태 - 멤버십 할인을 적용한 최종 가격
    val selectFinalPrice: (AppState) -> Int = { state ->
        val cartTotal = state.cart.totalPrice
        val membershipDiscount = state.user.membershipLevel.discountRate
        (cartTotal * (1 - membershipDiscount)).toInt()
    }

    // User
    val selectIsLoggedIn: (AppState) -> Boolean = { it.user.isLoggedIn }
    val selectUserName: (AppState) -> String? = { it.user.userName }

    // UI
    val selectIsLoading: (AppState) -> Boolean = { it.ui.isLoading }
    val selectError: (AppState) -> String? = { it.ui.error }
}

// ============================================================
// 8. 시간 여행 디버깅
// ============================================================

/**
 * 상태 스냅샷
 */
data class StateSnapshot<S>(
    val state: S,
    val action: Action,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 시간 여행 디버거
 */
class TimeTravelDebugger<S>(
    private val store: Store<S>,
    private val reducer: Reducer<S>,
    private val initialState: S
) {
    private val snapshots = mutableListOf<StateSnapshot<S>>()

    fun record(action: Action, state: S) {
        snapshots.add(StateSnapshot(state, action))
    }

    fun getHistory(): List<StateSnapshot<S>> = snapshots.toList()

    fun replayTo(index: Int): S {
        if (index < 0 || index >= snapshots.size) {
            throw IndexOutOfBoundsException("Invalid snapshot index: $index")
        }

        // 처음부터 해당 인덱스까지 Action을 다시 실행
        var state = initialState
        for (i in 0..index) {
            state = reducer(state, snapshots[i].action)
        }
        return state
    }

    fun printHistory() {
        println("  === 상태 변경 이력 ===")
        snapshots.forEachIndexed { index, snapshot ->
            println("  [$index] ${snapshot.action::class.simpleName} @ ${snapshot.timestamp}")
        }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() = runBlocking {
    println("=== Unidirectional Data Flow (Redux) Pattern ===\n")

    // --- 1. Store 생성 ---
    println("--- 1. Store 생성 ---")
    val actionRecorder = ActionRecorderMiddleware<AppState>()

    val store = Store(
        initialState = AppState.INITIAL,
        reducer = rootReducer,
        middlewares = listOf(
            loggingMiddleware(),
            actionRecorder,
            thunkMiddleware(this),
            EffectManager(listOf(SaveCartEffect(), ValidateDiscountEffect()))
        )
    )
    println("초기 상태: ${store.state.value}\n")

    // --- 2. Counter 예제 ---
    println("--- 2. Counter Actions ---")
    store.dispatch(CounterAction.Increment)
    store.dispatch(CounterAction.Increment)
    store.dispatch(CounterAction.Increment)
    store.dispatch(CounterAction.Decrement)
    println("현재 카운터: ${Selectors.selectCount(store.state.value)}")
    println("Undo 가능: ${Selectors.selectCanUndo(store.state.value)}")

    store.dispatch(CounterAction.Undo)
    println("Undo 후: ${Selectors.selectCount(store.state.value)}\n")

    // --- 3. Cart 예제 ---
    println("--- 3. Cart Actions ---")
    store.dispatch(CartAction.AddItem("p1", "맥북 프로", 2_500_000))
    store.dispatch(CartAction.AddItem("p2", "아이패드", 1_200_000))
    store.dispatch(CartAction.AddItem("p1", "맥북 프로", 2_500_000)) // 수량 증가
    store.dispatch(CartAction.UpdateQuantity("p2", 2))

    println("장바구니 아이템 수: ${Selectors.selectCartItemCount(store.state.value)}")
    println("장바구니 총액: ${Selectors.selectCartTotal(store.state.value)}원\n")

    // --- 4. User 로그인 + 멤버십 할인 ---
    println("--- 4. User + Membership Discount ---")
    store.dispatch(UserAction.Login("user123", "홍길동"))
    store.dispatch(UserAction.UpdateMembership(MembershipLevel.GOLD))

    println("로그인 상태: ${Selectors.selectIsLoggedIn(store.state.value)}")
    println("사용자: ${Selectors.selectUserName(store.state.value)}")
    println("멤버십 레벨: ${store.state.value.user.membershipLevel}")
    println("최종 가격 (멤버십 할인 적용): ${Selectors.selectFinalPrice(store.state.value)}원\n")

    // --- 5. Async Action (Thunk) ---
    println("--- 5. Async Action with Thunk ---")
    val fetchProductsThunk = ThunkAction<AppState> { dispatch, getState ->
        dispatch(UiAction.ShowLoading)
        println("    [Thunk] 상품 로딩 중...")
        delay(500)
        println("    [Thunk] 상품 로딩 완료")
        dispatch(UiAction.HideLoading)
        dispatch(UiAction.ShowSnackbar("상품 목록을 불러왔습니다"))
    }
    store.dispatch(fetchProductsThunk)
    delay(600) // Thunk 완료 대기
    println()

    // --- 6. Effect (Side Effect) ---
    println("--- 6. Effect - 할인 코드 검증 ---")
    store.dispatch(AsyncAction.ValidateDiscount("SAVE20"))
    delay(400)
    println("할인 적용 후 총액: ${Selectors.selectCartTotal(store.state.value)}원")
    println("할인 코드: ${store.state.value.cart.discountCode}\n")

    // --- 7. Selector로 상태 구독 ---
    println("--- 7. Selector로 상태 구독 ---")
    val job = launch {
        store.select(Selectors.selectCartItemCount)
            .take(3)
            .collect { count ->
                println("  [구독] 장바구니 아이템 수 변경: $count")
            }
    }

    store.dispatch(CartAction.AddItem("p3", "에어팟", 300_000))
    store.dispatch(CartAction.RemoveItem("p2"))
    delay(100)
    job.cancel()
    println()

    // --- 8. 액션 이력 확인 ---
    println("--- 8. 액션 이력 (Time Travel Debugging) ---")
    println("총 ${actionRecorder.records.size}개의 액션이 기록됨")
    actionRecorder.records.takeLast(5).forEach { record ->
        println("  - ${record.action::class.simpleName} @ ${record.timestamp}")
    }

    println("\n=== Redux 핵심 원칙 ===")
    println("1. Single Source of Truth: 모든 상태는 Store에 집중")
    println("2. State is Read-Only: Action을 통해서만 상태 변경")
    println("3. Pure Reducer: 순수 함수로 상태 전이 (테스트 용이)")
    println("4. Unidirectional Flow: View → Action → Reducer → State → View")
    println("5. Middleware: 로깅, 비동기 처리, Side Effect 분리")
    println("6. Selector: 파생 상태 계산 및 캐싱")
    println("7. Time Travel: 상태 이력 기록으로 디버깅/Undo 가능")
}
