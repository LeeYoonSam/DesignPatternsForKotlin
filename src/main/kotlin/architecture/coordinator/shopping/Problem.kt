package architecture.coordinator.shopping

/**
 * Coordinator Pattern - Problem
 *
 * 이 파일은 Coordinator Pattern을 적용하지 않았을 때
 * 화면 전환(Navigation) 관리에서 발생하는 문제점을 보여줍니다.
 *
 * 예시: 쇼핑 앱 (상품 목록 → 상세 → 장바구니 → 결제 → 완료)
 *
 * 문제점:
 * 1. 화면(View/Activity/Fragment)이 다음 화면을 직접 알고 있어 강한 결합
 * 2. 화면 전환 로직이 여러 화면에 분산되어 흐름 파악 어려움
 * 3. 조건부 네비게이션(로그인 필요, 권한 체크)이 화면에 혼재
 * 4. 동일 화면을 다른 흐름에서 재사용 어려움
 * 5. Deep Link 처리가 복잡하고 파편화됨
 */

// ========================================
// 문제: 화면 간 강한 결합
// ========================================

/**
 * 문제 1: 화면이 다음 화면을 직접 참조
 *
 * ProductListScreen이 ProductDetailScreen, CartScreen,
 * LoginScreen 등을 직접 알아야 함
 */
class ProductListScreen {
    private var isLoggedIn = false

    fun onProductClicked(productId: String) {
        // 화면이 직접 다른 화면을 생성하고 전환
        println("[ProductList] 상품 클릭: $productId")

        // 조건부 로직이 화면에 포함
        if (!isLoggedIn) {
            println("[ProductList] → LoginScreen으로 이동")
            val loginScreen = LoginScreen()
            loginScreen.show()
            // 로그인 후 다시 상세로 가야 하는데... 어떻게?
            return
        }

        // 다음 화면을 직접 생성
        val detailScreen = ProductDetailScreen()
        detailScreen.show(productId)
    }

    fun onCartClicked() {
        println("[ProductList] 장바구니 클릭")

        if (!isLoggedIn) {
            println("[ProductList] → LoginScreen으로 이동")
            val loginScreen = LoginScreen()
            loginScreen.show()
            return
        }

        // 또 다른 화면 직접 생성
        val cartScreen = CartScreen()
        cartScreen.show()
    }

    fun show() {
        println("[ProductList] 화면 표시")
    }
}

class ProductDetailScreen {
    private var isLoggedIn = false

    fun show(productId: String) {
        println("[ProductDetail] 상품 상세 표시: $productId")
    }

    fun onAddToCart(productId: String) {
        println("[ProductDetail] 장바구니 추가: $productId")

        // 또 다른 조건부 네비게이션 중복
        if (!isLoggedIn) {
            println("[ProductDetail] → LoginScreen으로 이동")
            val loginScreen = LoginScreen()
            loginScreen.show()
            return
        }

        val cartScreen = CartScreen()
        cartScreen.show()
    }

    fun onBuyNow(productId: String) {
        println("[ProductDetail] 바로 구매: $productId")

        if (!isLoggedIn) {
            val loginScreen = LoginScreen()
            loginScreen.show()
            return
        }

        // 결제 화면으로 직접 이동
        val checkoutScreen = CheckoutScreen()
        checkoutScreen.show(productId)
    }
}

class CartScreen {
    fun show() {
        println("[Cart] 장바구니 표시")
    }

    fun onCheckout() {
        println("[Cart] 결제 진행")
        // 결제 화면을 직접 생성
        val checkoutScreen = CheckoutScreen()
        checkoutScreen.show("")
    }

    fun onContinueShopping() {
        println("[Cart] 쇼핑 계속")
        // 뒤로 가기? 상품 목록으로? 어디로?
        val productListScreen = ProductListScreen()
        productListScreen.show()
    }
}

class CheckoutScreen {
    fun show(productId: String) {
        println("[Checkout] 결제 화면 표시")
    }

    fun onPaymentComplete() {
        println("[Checkout] 결제 완료")
        // 완료 화면으로 이동
        val completeScreen = OrderCompleteScreen()
        completeScreen.show("order-123")
    }
}

class OrderCompleteScreen {
    fun show(orderId: String) {
        println("[OrderComplete] 주문 완료: $orderId")
    }

    fun onGoHome() {
        // 홈으로 돌아가기... 중간 화면들은 어떻게 정리?
        println("[OrderComplete] 홈으로 이동")
        val productListScreen = ProductListScreen()
        productListScreen.show()
    }
}

class LoginScreen {
    fun show() {
        println("[Login] 로그인 화면 표시")
    }

    fun onLoginSuccess() {
        println("[Login] 로그인 성공")
        // 로그인 후 어디로 돌아가야 하는지 모름!
        // 상품 상세에서 왔나? 장바구니에서 왔나? 결제에서 왔나?
        println("[Login] ⚠️ 이전 화면으로 돌아가야 하는데 어디인지 모름!")
    }
}

/**
 * 문제 2: 흐름 파악이 어려움
 *
 * 앱의 전체 네비게이션 흐름이 여러 화면에 분산
 */
class NavigationFlowProblem {
    fun demonstrate() {
        println("=== 네비게이션 흐름 파악 문제 ===")
        println()
        println("상품 목록 → 상세: ProductListScreen.onProductClicked()")
        println("상세 → 장바구니: ProductDetailScreen.onAddToCart()")
        println("상세 → 결제: ProductDetailScreen.onBuyNow()")
        println("장바구니 → 결제: CartScreen.onCheckout()")
        println("결제 → 완료: CheckoutScreen.onPaymentComplete()")
        println()
        println("→ 전체 흐름을 파악하려면 모든 화면 코드를 확인해야 함")
        println("→ 흐름 변경 시 여러 화면을 수정해야 함")
    }
}

/**
 * 문제 3: 동일 화면을 다른 흐름에서 재사용 어려움
 *
 * LoginScreen은 여러 곳에서 호출되지만,
 * 로그인 후 돌아갈 곳이 매번 다름
 */
class ReuseProlem {
    fun demonstrate() {
        println("=== 화면 재사용 문제 ===")
        println()
        println("LoginScreen이 호출되는 곳:")
        println("  1. ProductListScreen → 상품 클릭 시")
        println("  2. ProductDetailScreen → 장바구니 추가 시")
        println("  3. ProductDetailScreen → 바로 구매 시")
        println("  4. CartScreen → 결제 시")
        println()
        println("각각 로그인 후 돌아갈 화면이 다른데,")
        println("LoginScreen이 이 모든 케이스를 알아야 함 → 강한 결합")
    }
}

/**
 * 문제 4: Deep Link 처리가 어려움
 */
class DeepLinkProblem {
    fun handleDeepLink(url: String) {
        println("=== Deep Link 처리 문제 ===")
        println("URL: $url")
        println()

        // Deep Link 처리 로직이 중앙화되지 않음
        when {
            url.contains("/product/") -> {
                val productId = url.substringAfterLast("/")
                println("→ ProductDetailScreen 직접 열기")
                println("  그런데 백스택은? 뒤로 가기하면 어디로?")
            }
            url.contains("/cart") -> {
                println("→ CartScreen 직접 열기")
                println("  로그인 체크는? 상품 목록은 스택에 있어야 하나?")
            }
            url.contains("/order/") -> {
                println("→ OrderCompleteScreen 직접 열기")
                println("  결제 과정 없이 바로 완료 화면?")
            }
        }
    }
}

/**
 * 문제점 요약:
 *
 * 1. 강한 결합: 화면이 다음 화면을 직접 알아야 함
 * 2. 분산된 흐름: 네비게이션 로직이 여러 화면에 흩어짐
 * 3. 중복 로직: 로그인 체크 등 조건부 네비게이션이 반복
 * 4. 재사용 불가: 같은 화면을 다른 흐름에서 쓰기 어려움
 * 5. Deep Link: 중간 화면 없이 특정 화면을 열기 어려움
 *
 * Coordinator Pattern으로 이 문제들을 해결할 수 있습니다.
 * Solution.kt에서 구현을 확인하세요.
 */

fun main() {
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║          Coordinator Pattern 적용 전 문제점 데모              ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    println("--- 1. 화면 간 강한 결합 ---")
    val productList = ProductListScreen()
    productList.show()
    productList.onProductClicked("product-001")
    println()

    println("--- 2. 네비게이션 흐름 파악 ---")
    NavigationFlowProblem().demonstrate()
    println()

    println("--- 3. 화면 재사용 문제 ---")
    ReuseProlem().demonstrate()
    println()

    println("--- 4. Deep Link 처리 ---")
    DeepLinkProblem().handleDeepLink("myapp://product/product-001")
    println()

    println("Solution.kt에서 Coordinator Pattern을 적용한 해결책을 확인하세요.")
}
