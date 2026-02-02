package architecture.coordinator.shopping

/**
 * Coordinator Pattern - Solution
 *
 * Coordinator Pattern은 화면 전환(Navigation) 로직을 화면(View)에서 분리하여
 * 별도의 Coordinator 객체가 관리하도록 하는 패턴입니다.
 *
 * 핵심 아이디어:
 * 1. 화면(Screen)은 다음 화면을 모른다 (네비게이션 로직 없음)
 * 2. 화면은 이벤트만 Coordinator에 전달한다
 * 3. Coordinator가 앱의 전체 흐름을 중앙에서 관리한다
 * 4. Coordinator는 계층적으로 구성하여 관심사를 분리한다
 *
 * 예시: 쇼핑 앱 네비게이션
 */

import java.util.Stack

// ========================================
// Navigation Infrastructure
// ========================================

/**
 * 화면을 나타내는 sealed class
 */
sealed class Screen(val name: String) {
    object ProductList : Screen("상품 목록")
    data class ProductDetail(val productId: String) : Screen("상품 상세")
    object Cart : Screen("장바구니")
    data class Checkout(val orderId: String) : Screen("결제")
    data class OrderComplete(val orderId: String) : Screen("주문 완료")
    object Login : Screen("로그인")
    object SignUp : Screen("회원가입")
    data class Category(val categoryId: String) : Screen("카테고리")
}

/**
 * 네비게이션을 실행하는 인터페이스
 *
 * 실제 앱에서는 NavController, FragmentManager, Activity 등이 구현
 */
interface Navigator {
    fun push(screen: Screen)
    fun pop(): Screen?
    fun popToRoot()
    fun replace(screen: Screen)
    fun currentScreen(): Screen?
    fun backStackSize(): Int
}

class StackNavigator : Navigator {
    private val backStack = Stack<Screen>()

    override fun push(screen: Screen) {
        backStack.push(screen)
        println("  [Nav] → ${screen.name} (스택: ${backStack.size})")
    }

    override fun pop(): Screen? {
        if (backStack.isEmpty()) return null
        val popped = backStack.pop()
        println("  [Nav] ← ${popped.name} 닫힘 (스택: ${backStack.size})")
        return popped
    }

    override fun popToRoot() {
        val root = if (backStack.isNotEmpty()) backStack.first() else return
        backStack.clear()
        backStack.push(root)
        println("  [Nav] ← 루트로 복귀: ${root.name} (스택: 1)")
    }

    override fun replace(screen: Screen) {
        if (backStack.isNotEmpty()) backStack.pop()
        backStack.push(screen)
        println("  [Nav] ↔ ${screen.name}로 교체 (스택: ${backStack.size})")
    }

    override fun currentScreen(): Screen? =
        if (backStack.isNotEmpty()) backStack.peek() else null

    override fun backStackSize(): Int = backStack.size
}

// ========================================
// Auth Service (인증 상태 관리)
// ========================================

class AuthService {
    var isLoggedIn: Boolean = false
        private set

    var currentUserId: String? = null
        private set

    fun login(userId: String) {
        isLoggedIn = true
        currentUserId = userId
        println("  [Auth] 로그인 완료: $userId")
    }

    fun logout() {
        isLoggedIn = false
        currentUserId = null
        println("  [Auth] 로그아웃")
    }
}

// ========================================
// Coordinator Protocol
// ========================================

/**
 * 모든 Coordinator의 기본 인터페이스
 */
interface Coordinator {
    val navigator: Navigator

    /** Coordinator 시작 */
    fun start()

    /** 하위 Coordinator 종료 시 호출 */
    fun onChildFinished(child: Coordinator) {}
}

/**
 * 하위 Coordinator를 관리하는 부모 Coordinator
 */
abstract class ParentCoordinator(override val navigator: Navigator) : Coordinator {
    protected val childCoordinators = mutableListOf<Coordinator>()

    protected fun startChild(child: Coordinator) {
        childCoordinators.add(child)
        child.start()
    }

    protected fun removeChild(child: Coordinator) {
        childCoordinators.remove(child)
    }

    override fun onChildFinished(child: Coordinator) {
        removeChild(child)
    }
}

// ========================================
// Screen Events (화면 → Coordinator 이벤트)
// ========================================

/**
 * 각 화면이 발행할 수 있는 이벤트
 *
 * 화면은 다음 화면을 모르고, 이벤트만 발행
 */
sealed class ProductListEvent {
    data class ProductSelected(val productId: String) : ProductListEvent()
    object CartTapped : ProductListEvent()
    data class CategorySelected(val categoryId: String) : ProductListEvent()
}

sealed class ProductDetailEvent {
    data class AddToCart(val productId: String) : ProductDetailEvent()
    data class BuyNow(val productId: String) : ProductDetailEvent()
    object BackTapped : ProductDetailEvent()
}

sealed class CartEvent {
    object CheckoutTapped : CartEvent()
    object ContinueShoppingTapped : CartEvent()
    data class ProductTapped(val productId: String) : CartEvent()
}

sealed class CheckoutEvent {
    data class PaymentCompleted(val orderId: String) : CheckoutEvent()
    object CancelTapped : CheckoutEvent()
}

sealed class OrderCompleteEvent {
    object GoHomeTapped : OrderCompleteEvent()
    data class ViewOrderTapped(val orderId: String) : OrderCompleteEvent()
}

sealed class LoginEvent {
    data class LoginSuccess(val userId: String) : LoginEvent()
    object SignUpTapped : LoginEvent()
    object CancelTapped : LoginEvent()
}

sealed class SignUpEvent {
    data class SignUpSuccess(val userId: String) : SignUpEvent()
    object CancelTapped : SignUpEvent()
}

// ========================================
// Screens (네비게이션 로직 없는 순수한 화면)
// ========================================

/**
 * 화면은 이벤트 콜백만 가지고 있음
 * → 다음 화면이 뭔지 전혀 모름
 * → 어떤 흐름에서든 재사용 가능
 */
class ProductListView(private val onEvent: (ProductListEvent) -> Unit) {
    fun render(products: List<String>) {
        println("  [Screen] 상품 목록 렌더링: $products")
    }

    fun simulateProductClick(productId: String) {
        onEvent(ProductListEvent.ProductSelected(productId))
    }

    fun simulateCartClick() {
        onEvent(ProductListEvent.CartTapped)
    }
}

class ProductDetailView(
    private val productId: String,
    private val onEvent: (ProductDetailEvent) -> Unit
) {
    fun render() {
        println("  [Screen] 상품 상세 렌더링: $productId")
    }

    fun simulateAddToCart() {
        onEvent(ProductDetailEvent.AddToCart(productId))
    }

    fun simulateBuyNow() {
        onEvent(ProductDetailEvent.BuyNow(productId))
    }
}

class CartView(private val onEvent: (CartEvent) -> Unit) {
    fun render(items: List<String>) {
        println("  [Screen] 장바구니 렌더링: $items")
    }

    fun simulateCheckout() {
        onEvent(CartEvent.CheckoutTapped)
    }
}

class CheckoutView(
    private val orderId: String,
    private val onEvent: (CheckoutEvent) -> Unit
) {
    fun render() {
        println("  [Screen] 결제 화면 렌더링: $orderId")
    }

    fun simulatePayment() {
        onEvent(CheckoutEvent.PaymentCompleted(orderId))
    }
}

class OrderCompleteView(
    private val orderId: String,
    private val onEvent: (OrderCompleteEvent) -> Unit
) {
    fun render() {
        println("  [Screen] 주문 완료: $orderId")
    }

    fun simulateGoHome() {
        onEvent(OrderCompleteEvent.GoHomeTapped)
    }
}

class LoginView(private val onEvent: (LoginEvent) -> Unit) {
    fun render() {
        println("  [Screen] 로그인 화면")
    }

    fun simulateLogin(userId: String) {
        onEvent(LoginEvent.LoginSuccess(userId))
    }

    fun simulateSignUp() {
        onEvent(LoginEvent.SignUpTapped)
    }
}

class SignUpView(private val onEvent: (SignUpEvent) -> Unit) {
    fun render() {
        println("  [Screen] 회원가입 화면")
    }

    fun simulateSignUp(userId: String) {
        onEvent(SignUpEvent.SignUpSuccess(userId))
    }
}

// ========================================
// Coordinators (네비게이션 흐름 관리)
// ========================================

/**
 * Auth Coordinator: 로그인/회원가입 흐름 관리
 *
 * 로그인이 필요한 모든 곳에서 재사용 가능
 */
class AuthCoordinator(
    override val navigator: Navigator,
    private val authService: AuthService,
    private val onComplete: (authenticated: Boolean) -> Unit
) : Coordinator {

    override fun start() {
        println("[AuthCoordinator] 인증 흐름 시작")
        showLogin()
    }

    private fun showLogin() {
        navigator.push(Screen.Login)
        val loginView = LoginView { event ->
            when (event) {
                is LoginEvent.LoginSuccess -> {
                    authService.login(event.userId)
                    navigator.pop()
                    onComplete(true)
                }
                is LoginEvent.SignUpTapped -> showSignUp()
                is LoginEvent.CancelTapped -> {
                    navigator.pop()
                    onComplete(false)
                }
            }
        }
        loginView.render()
    }

    private fun showSignUp() {
        navigator.push(Screen.SignUp)
        val signUpView = SignUpView { event ->
            when (event) {
                is SignUpEvent.SignUpSuccess -> {
                    authService.login(event.userId)
                    navigator.pop() // 회원가입 닫기
                    navigator.pop() // 로그인 닫기
                    onComplete(true)
                }
                is SignUpEvent.CancelTapped -> navigator.pop()
            }
        }
        signUpView.render()
    }
}

/**
 * Checkout Coordinator: 결제 흐름 관리
 *
 * 결제 → 완료 흐름을 독립적으로 관리
 */
class CheckoutCoordinator(
    override val navigator: Navigator,
    private val productId: String,
    private val onComplete: (orderId: String?) -> Unit
) : Coordinator {

    override fun start() {
        println("[CheckoutCoordinator] 결제 흐름 시작")
        showCheckout()
    }

    private fun showCheckout() {
        val orderId = "order-${System.currentTimeMillis() % 10000}"
        navigator.push(Screen.Checkout(orderId))

        val checkoutView = CheckoutView(orderId) { event ->
            when (event) {
                is CheckoutEvent.PaymentCompleted -> showOrderComplete(event.orderId)
                is CheckoutEvent.CancelTapped -> {
                    navigator.pop()
                    onComplete(null) // 취소
                }
            }
        }
        checkoutView.render()
    }

    private fun showOrderComplete(orderId: String) {
        navigator.replace(Screen.OrderComplete(orderId))

        val completeView = OrderCompleteView(orderId) { event ->
            when (event) {
                is OrderCompleteEvent.GoHomeTapped -> onComplete(orderId)
                is OrderCompleteEvent.ViewOrderTapped -> {
                    println("[CheckoutCoordinator] 주문 상세로 이동: ${event.orderId}")
                }
            }
        }
        completeView.render()
    }
}

/**
 * Shopping Coordinator: 메인 쇼핑 흐름 관리
 *
 * 상품 탐색, 장바구니, 결제까지의 전체 흐름을 조율
 */
class ShoppingCoordinator(
    navigator: Navigator,
    private val authService: AuthService
) : ParentCoordinator(navigator) {

    override fun start() {
        println("[ShoppingCoordinator] 쇼핑 흐름 시작")
        showProductList()
    }

    private fun showProductList() {
        navigator.push(Screen.ProductList)

        val productListView = ProductListView { event ->
            when (event) {
                is ProductListEvent.ProductSelected ->
                    showProductDetail(event.productId)
                is ProductListEvent.CartTapped ->
                    requireAuth { showCart() }
                is ProductListEvent.CategorySelected ->
                    println("[ShoppingCoordinator] 카테고리: ${event.categoryId}")
            }
        }
        productListView.render(listOf("맥북 프로", "기계식 키보드", "모니터"))
    }

    private fun showProductDetail(productId: String) {
        navigator.push(Screen.ProductDetail(productId))

        val detailView = ProductDetailView(productId) { event ->
            when (event) {
                is ProductDetailEvent.AddToCart ->
                    requireAuth {
                        println("  [Cart] 상품 추가됨: ${event.productId}")
                        navigator.pop() // 상세 화면 닫기
                        showCart()
                    }
                is ProductDetailEvent.BuyNow ->
                    requireAuth { startCheckout(event.productId) }
                is ProductDetailEvent.BackTapped ->
                    navigator.pop()
            }
        }
        detailView.render()
    }

    private fun showCart() {
        navigator.push(Screen.Cart)

        val cartView = CartView { event ->
            when (event) {
                is CartEvent.CheckoutTapped -> startCheckout("cart-items")
                is CartEvent.ContinueShoppingTapped -> navigator.pop()
                is CartEvent.ProductTapped -> showProductDetail(event.productId)
            }
        }
        cartView.render(listOf("맥북 프로 x1", "키보드 x2"))
    }

    /**
     * 결제 흐름은 별도 Coordinator에 위임
     */
    private fun startCheckout(productId: String) {
        val checkoutCoordinator = CheckoutCoordinator(
            navigator = navigator,
            productId = productId
        ) { orderId ->
            // 결제 완료/취소 후 처리
            if (orderId != null) {
                println("[ShoppingCoordinator] 주문 완료: $orderId → 홈으로")
                navigator.popToRoot()
            } else {
                println("[ShoppingCoordinator] 결제 취소")
            }
            onChildFinished(checkoutCoordinator)
        }
        startChild(checkoutCoordinator)
    }

    /**
     * 인증이 필요한 작업을 래핑
     *
     * 로그인 여부를 확인하고, 미로그인 시 AuthCoordinator를 시작
     * 로그인 성공 후 원래 하려던 작업을 이어서 실행
     */
    private fun requireAuth(onAuthenticated: () -> Unit) {
        if (authService.isLoggedIn) {
            onAuthenticated()
            return
        }

        println("[ShoppingCoordinator] 인증 필요 → AuthCoordinator 시작")
        val authCoordinator = AuthCoordinator(
            navigator = navigator,
            authService = authService
        ) { authenticated ->
            if (authenticated) {
                println("[ShoppingCoordinator] 인증 완료 → 원래 작업 계속")
                onAuthenticated()
            } else {
                println("[ShoppingCoordinator] 인증 취소")
            }
            onChildFinished(authCoordinator)
        }
        startChild(authCoordinator)
    }
}

// ========================================
// App Coordinator: 최상위 Coordinator
// ========================================

/**
 * App Coordinator: 앱 진입점
 *
 * Deep Link 처리, 초기 화면 결정 등을 담당
 */
class AppCoordinator(
    navigator: Navigator,
    private val authService: AuthService = AuthService()
) : ParentCoordinator(navigator) {

    override fun start() {
        println("[AppCoordinator] 앱 시작")
        startShopping()
    }

    private fun startShopping() {
        val shoppingCoordinator = ShoppingCoordinator(navigator, authService)
        startChild(shoppingCoordinator)
    }

    /**
     * Deep Link 처리
     *
     * 모든 Deep Link 처리가 한 곳에 집중
     */
    fun handleDeepLink(url: String) {
        println("[AppCoordinator] Deep Link 처리: $url")

        when {
            url.contains("/product/") -> {
                val productId = url.substringAfterLast("/")
                // 홈 → 상품 상세 순서로 백스택 구성
                navigator.popToRoot()
                navigator.push(Screen.ProductDetail(productId))
                println("[AppCoordinator] 상품 상세로 이동 (백스택 정리됨)")
            }
            url.contains("/cart") -> {
                if (!authService.isLoggedIn) {
                    println("[AppCoordinator] Deep Link → 로그인 필요")
                    navigator.push(Screen.Login)
                } else {
                    navigator.push(Screen.Cart)
                }
            }
            url.contains("/order/") -> {
                val orderId = url.substringAfterLast("/")
                navigator.push(Screen.OrderComplete(orderId))
            }
        }
    }
}

// ========================================
// Main - 데모
// ========================================

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║          Coordinator Pattern - 쇼핑 앱 네비게이션 데모        ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val navigator = StackNavigator()
    val authService = AuthService()
    val appCoordinator = AppCoordinator(navigator, authService)

    // === 시나리오 1: 앱 시작 ===
    println("=== 시나리오 1: 앱 시작 ===")
    appCoordinator.start()
    println()

    // === 시나리오 2: 미로그인 상태에서 상품 클릭 ===
    println("=== 시나리오 2: 상품 상세 보기 ===")
    val shoppingCoordinator = ShoppingCoordinator(StackNavigator(), authService)
    shoppingCoordinator.start()
    println()

    // === 시나리오 3: 로그인 후 결제 흐름 ===
    println("=== 시나리오 3: 로그인 후 결제 흐름 ===")
    val nav3 = StackNavigator()
    val auth3 = AuthService()
    auth3.login("user-001") // 이미 로그인 상태

    val shopping3 = ShoppingCoordinator(nav3, auth3)
    shopping3.start()
    println()

    // === 시나리오 4: Deep Link ===
    println("=== 시나리오 4: Deep Link 처리 ===")
    val nav4 = StackNavigator()
    val app4 = AppCoordinator(nav4)
    app4.start()
    println()
    app4.handleDeepLink("myapp://product/macbook-pro")
    println()
    app4.handleDeepLink("myapp://cart")
    println()

    println("╔══════════════════════════════════════════════════════════════╗")
    println("║                 Coordinator Pattern 장점                     ║")
    println("╠══════════════════════════════════════════════════════════════╣")
    println("║ 1. 화면 분리: Screen은 다음 화면을 모름 (재사용 가능)       ║")
    println("║ 2. 흐름 집중: 네비게이션 로직이 Coordinator에 집중          ║")
    println("║ 3. 계층 관리: 하위 Coordinator로 복잡한 흐름 분리           ║")
    println("║ 4. 인증 통합: requireAuth()로 인증 로직 중앙화             ║")
    println("║ 5. Deep Link: 한 곳에서 모든 Deep Link 처리               ║")
    println("╚══════════════════════════════════════════════════════════════╝")
}
