# Coordinator Pattern

## 개요

Coordinator Pattern은 **화면 전환(Navigation) 로직을 화면(View)에서 분리**하여 별도의 Coordinator 객체가 관리하도록 하는 아키텍처 패턴입니다. Soroush Khanlou가 iOS 개발에서 처음 제안했으며, Android/KMP 등 모든 클라이언트 앱에 적용 가능합니다.

## 문제 상황

화면이 다음 화면을 직접 알고 있으면 발생하는 문제:

```kotlin
class ProductDetailScreen {
    fun onBuyNow(productId: String) {
        // 화면이 다음 화면을 직접 생성 → 강한 결합
        if (!isLoggedIn) {
            val loginScreen = LoginScreen()  // LoginScreen 의존
            loginScreen.show()
            return
        }
        val checkoutScreen = CheckoutScreen()  // CheckoutScreen 의존
        checkoutScreen.show(productId)
    }
}
```

**문제점:**
- 화면 간 강한 결합 (ProductDetail → Login, Checkout 의존)
- 네비게이션 로직이 여러 화면에 분산
- 동일 화면(LoginScreen)을 다른 흐름에서 재사용 어려움
- 전체 앱 흐름을 파악하려면 모든 화면 코드를 확인해야 함

## 해결 방법

화면은 **이벤트만 발행**하고, Coordinator가 **흐름을 결정**합니다.

```
┌──────────────────────────────────────────────────────────┐
│                    AppCoordinator                        │
│  ┌────────────────────────┐  ┌─────────────────────────┐ │
│  │  ShoppingCoordinator   │  │   AuthCoordinator       │ │
│  │                        │  │                         │ │
│  │  ProductList ─event──┐ │  │  Login ───event───┐     │ │
│  │  ProductDetail ─event┤ │  │  SignUp ──event───┤     │ │
│  │  Cart ────────event──┤ │  │                   ▼     │ │
│  │                      ▼ │  │  onComplete(auth)       │ │
│  │  Coordinator가 흐름 결정│  │                         │ │
│  └────────────────────────┘  └─────────────────────────┘ │
│                                                          │
│  ┌────────────────────────┐                              │
│  │  CheckoutCoordinator   │                              │
│  │  Checkout ──event──┐   │                              │
│  │  OrderComplete ────┤   │                              │
│  │                    ▼   │                              │
│  │  onComplete(orderId)   │                              │
│  └────────────────────────┘                              │
└──────────────────────────────────────────────────────────┘
```

## 핵심 구현

### 1. 화면 이벤트 정의

화면이 발행할 수 있는 이벤트를 sealed class로 정의합니다.

```kotlin
sealed class ProductDetailEvent {
    data class AddToCart(val productId: String) : ProductDetailEvent()
    data class BuyNow(val productId: String) : ProductDetailEvent()
    object BackTapped : ProductDetailEvent()
}
```

### 2. 화면 (네비게이션 로직 없음)

화면은 다음 화면을 모르고 이벤트 콜백만 호출합니다.

```kotlin
class ProductDetailView(
    private val productId: String,
    private val onEvent: (ProductDetailEvent) -> Unit  // 이벤트만 전달
) {
    fun render() { /* UI 렌더링 */ }

    fun onBuyNowClicked() {
        onEvent(ProductDetailEvent.BuyNow(productId))
        // 다음 화면이 뭔지 전혀 모름!
    }
}
```

### 3. Coordinator (흐름 관리)

```kotlin
class ShoppingCoordinator(
    navigator: Navigator,
    private val authService: AuthService
) : ParentCoordinator(navigator) {

    override fun start() {
        showProductList()
    }

    private fun showProductDetail(productId: String) {
        navigator.push(Screen.ProductDetail(productId))

        ProductDetailView(productId) { event ->
            when (event) {
                is ProductDetailEvent.AddToCart ->
                    requireAuth { addToCartAndShowCart(event.productId) }
                is ProductDetailEvent.BuyNow ->
                    requireAuth { startCheckout(event.productId) }
                is ProductDetailEvent.BackTapped ->
                    navigator.pop()
            }
        }
    }

    // 인증이 필요한 작업을 래핑
    private fun requireAuth(onAuthenticated: () -> Unit) {
        if (authService.isLoggedIn) {
            onAuthenticated()
            return
        }

        // 별도 Coordinator에 위임
        val authCoordinator = AuthCoordinator(navigator, authService) { success ->
            if (success) onAuthenticated()
            onChildFinished(authCoordinator)
        }
        startChild(authCoordinator)
    }
}
```

### 4. 계층적 Coordinator 구조

```kotlin
// 기본 인터페이스
interface Coordinator {
    val navigator: Navigator
    fun start()
}

// 하위 Coordinator를 관리하는 부모
abstract class ParentCoordinator(override val navigator: Navigator) : Coordinator {
    protected val childCoordinators = mutableListOf<Coordinator>()

    protected fun startChild(child: Coordinator) {
        childCoordinators.add(child)
        child.start()
    }

    fun onChildFinished(child: Coordinator) {
        childCoordinators.remove(child)
    }
}
```

## Coordinator 계층 구조

```
AppCoordinator
├── ShoppingCoordinator          (상품 탐색 흐름)
│   ├── AuthCoordinator          (로그인/회원가입 흐름)
│   └── CheckoutCoordinator      (결제 흐름)
├── MyPageCoordinator            (마이페이지 흐름)
│   └── AuthCoordinator          (재사용!)
└── SettingsCoordinator          (설정 흐름)
```

## requireAuth 패턴

로그인 체크를 한 곳에서 처리합니다.

```kotlin
// Before (모든 화면에서 중복)
fun onBuyNow() {
    if (!isLoggedIn) { showLogin(); return }
    showCheckout()
}

// After (Coordinator에서 한 번만)
private fun requireAuth(onAuthenticated: () -> Unit) {
    if (authService.isLoggedIn) {
        onAuthenticated()
    } else {
        startChild(AuthCoordinator(navigator, authService) { success ->
            if (success) onAuthenticated()
        })
    }
}

// 사용
requireAuth { startCheckout(productId) }
requireAuth { showCart() }
```

## Deep Link 처리

모든 Deep Link를 AppCoordinator에서 중앙 관리합니다.

```kotlin
class AppCoordinator : ParentCoordinator(navigator) {
    fun handleDeepLink(url: String) {
        when {
            url.contains("/product/") -> {
                val productId = url.substringAfterLast("/")
                navigator.popToRoot()
                navigator.push(Screen.ProductDetail(productId))
            }
            url.contains("/cart") -> {
                if (!authService.isLoggedIn) {
                    navigator.push(Screen.Login)
                } else {
                    navigator.push(Screen.Cart)
                }
            }
        }
    }
}
```

## Android에서의 적용

### Jetpack Navigation과 함께

```kotlin
class ShoppingCoordinator(
    private val navController: NavController,
    private val authService: AuthService
) {
    fun showProductDetail(productId: String) {
        navController.navigate("product/$productId")
    }

    fun requireAuth(destination: String) {
        if (authService.isLoggedIn) {
            navController.navigate(destination)
        } else {
            navController.navigate("login?redirect=$destination")
        }
    }
}
```

### Compose Navigation과 함께

```kotlin
@Composable
fun ShoppingNavHost(coordinator: ShoppingCoordinator) {
    NavHost(navController, startDestination = "products") {
        composable("products") {
            ProductListScreen(
                onProductClick = { coordinator.showProductDetail(it) },
                onCartClick = { coordinator.requireAuth("cart") }
            )
        }
        composable("product/{id}") { backStackEntry ->
            ProductDetailScreen(
                productId = backStackEntry.arguments?.getString("id"),
                onBuyNow = { coordinator.startCheckout(it) }
            )
        }
    }
}
```

## MVI + Coordinator 조합

MVI의 상태 관리와 Coordinator의 네비게이션을 분리합니다.

```kotlin
// MVI → 상태 관리
class ProductDetailViewModel : ViewModel() {
    val state: StateFlow<ProductDetailState>
    fun processIntent(intent: ProductDetailIntent)
}

// Coordinator → 화면 전환
class ShoppingCoordinator {
    fun showProductDetail(productId: String)  // 네비게이션만 담당
}

// View → 연결
@Composable
fun ProductDetailScreen(
    viewModel: ProductDetailViewModel,
    onNavigationEvent: (ProductDetailEvent) -> Unit  // Coordinator로 전달
)
```

## 장점

1. **화면 분리**: Screen은 다음 화면을 모름 (재사용 가능)
2. **흐름 집중**: 전체 네비게이션 로직이 Coordinator에 집중
3. **계층 관리**: 복잡한 흐름을 하위 Coordinator로 분리
4. **인증 통합**: requireAuth()로 인증 로직 중앙화
5. **Deep Link**: 한 곳에서 모든 Deep Link 처리
6. **테스트**: Coordinator 단위로 흐름 테스트 가능

## 단점

1. **초기 설계 비용**: Coordinator 계층 구조 설계 필요
2. **간단한 앱에는 과도**: 화면 수가 적은 앱에는 불필요
3. **생명주기 관리**: Coordinator의 생명주기를 적절히 관리해야 함

## 적용 시점

- 화면 수가 10개 이상인 중대형 앱
- 동일 화면이 여러 흐름에서 재사용되는 경우
- 조건부 네비게이션(로그인, 권한 체크)이 많은 경우
- Deep Link 지원이 필요한 경우
- 다중 모듈 앱에서 모듈 간 네비게이션 관리

## 관련 패턴

- **MVI Pattern**: 상태 관리는 MVI, 네비게이션은 Coordinator
- **Mediator Pattern**: Coordinator가 화면 간 중재자 역할
- **Chain of Responsibility**: 계층적 Coordinator 구조와 유사
- **Strategy Pattern**: 흐름에 따라 다른 Coordinator 선택

## 참고 자료

- [Soroush Khanlou - Coordinators Redux](https://khanlou.com/2015/10/coordinators-redux/)
- [Android Developers - Navigation](https://developer.android.com/guide/navigation)
- [Paul Hudson - How to use the Coordinator Pattern in SwiftUI](https://www.hackingwithswift.com/books/ios-swiftui/how-to-use-the-coordinator-pattern-in-swiftui)
