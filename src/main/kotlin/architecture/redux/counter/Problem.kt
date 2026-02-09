package architecture.redux.counter

/**
 * Unidirectional Data Flow (Redux) Pattern - 문제 상황
 *
 * 쇼핑 앱을 개발하고 있습니다.
 * 장바구니, 상품 목록, 사용자 정보 등 여러 상태를 관리하는데,
 * 상태가 여러 곳에 분산되고 양방향으로 변경되면서 다양한 문제가 발생합니다.
 */

// ============================================================
// ❌ 문제 1: 상태가 여러 곳에 분산
// ============================================================

/**
 * 각 컴포넌트가 자체 상태를 가지고 있어
 * 전역 상태 파악이 어렵고 동기화 문제 발생
 */
class ProductListComponent {
    private var products = mutableListOf<Product>()
    private var isLoading = false
    private var selectedCategory: String? = null
    private var sortOrder = "latest"

    // 장바구니 수량을 표시하려면 CartComponent의 상태가 필요
    // → 컴포넌트 간 상태 공유가 어려움
}

class CartComponent {
    private val cartItems = mutableListOf<CartItem>()
    private var totalPrice = 0
    private var discountCode: String? = null

    fun addItem(product: Product) {
        cartItems.add(CartItem(product, 1))
        updateTotalPrice()
        // ProductListComponent의 "장바구니에 담김" 표시를 갱신하려면?
        // → 직접 참조? 이벤트 버스? 콜백?
    }

    private fun updateTotalPrice() {
        totalPrice = cartItems.sumOf { it.product.price * it.quantity }
    }
}

class UserComponent {
    private var user: User? = null
    private var isLoggedIn = false
    private var membershipLevel = "basic"

    // 멤버십 레벨에 따라 할인율이 달라지는데
    // CartComponent가 이 정보를 어떻게 알 수 있나?
}

data class Product(val id: String, val name: String, val price: Int)
data class CartItem(val product: Product, var quantity: Int)
data class User(val id: String, val name: String)

class ScatteredStateProblem {
    fun demonstrate() {
        println("--- 분산된 상태 문제 ---")
        println()
        println("  ProductListComponent:")
        println("    - products, isLoading, selectedCategory, sortOrder")
        println("  CartComponent:")
        println("    - cartItems, totalPrice, discountCode")
        println("  UserComponent:")
        println("    - user, isLoggedIn, membershipLevel")
        println()
        println("  ❌ 문제점:")
        println("    • 전체 앱 상태를 한눈에 파악할 수 없음")
        println("    • 컴포넌트 간 상태 공유가 복잡 (props drilling, 이벤트 버스)")
        println("    • 상태 변경 시 다른 컴포넌트에 어떤 영향을 주는지 추적 어려움")
        println("    • 디버깅 시 현재 상태를 재현하기 어려움")
    }
}

// ============================================================
// ❌ 문제 2: 양방향 데이터 흐름
// ============================================================

class BidirectionalFlowProblem {
    // View가 직접 Model을 변경하고
    // Model도 View를 직접 업데이트하는 구조

    class ProductModel {
        var products = mutableListOf<Product>()
        var views = mutableListOf<ProductView>() // Model이 View 참조

        fun updateProduct(product: Product) {
            val index = products.indexOfFirst { it.id == product.id }
            if (index >= 0) {
                products[index] = product
                // Model이 직접 View 갱신
                views.forEach { it.refresh() }
            }
        }
    }

    class ProductView {
        lateinit var model: ProductModel // View가 Model 참조

        fun onUserClick(productId: String) {
            // View가 직접 Model 변경
            val product = model.products.find { it.id == productId }
            product?.let {
                model.updateProduct(it.copy(name = it.name + " (클릭됨)"))
            }
        }

        fun refresh() {
            println("View 갱신")
        }
    }

    fun demonstrate() {
        println("--- 양방향 데이터 흐름 문제 ---")
        println()
        println("  View → Model (사용자 액션으로 상태 변경)")
        println("  Model → View (상태 변경 후 UI 갱신)")
        println("  View → Model → View → Model → ...")
        println()
        println("  ❌ 문제점:")
        println("    • 데이터가 어디서 어디로 흐르는지 추적 어려움")
        println("    • 순환 참조로 인한 무한 루프 가능성")
        println("    • 상태 변경의 원인을 찾기 어려움")
        println("    • 테스트 시 View와 Model을 분리하기 어려움")
    }
}

// ============================================================
// ❌ 문제 3: 예측 불가능한 상태 변경
// ============================================================

class UnpredictableStateProblem {
    private var counter = 0

    // 여러 곳에서 상태를 직접 변경
    fun increment() { counter++ }
    fun decrement() { counter-- }
    fun reset() { counter = 0 }
    fun setTo(value: Int) { counter = value }
    fun double() { counter *= 2 }

    // 어디서든 직접 상태 변경 가능
    fun someBusinessLogic() {
        if (counter > 10) {
            counter = 0 // 갑자기 리셋!
        }
    }

    fun anotherLogic() {
        counter += (Math.random() * 10).toInt() // 예측 불가능한 변경!
    }

    fun demonstrate() {
        println("--- 예측 불가능한 상태 변경 ---")
        println()
        println("  여러 메서드가 counter를 직접 변경:")
        println("    increment(), decrement(), reset(), setTo(), double()")
        println("    someBusinessLogic(), anotherLogic()")
        println()
        println("  ❌ 문제점:")
        println("    • 상태가 언제, 어디서, 왜 변경되었는지 추적 불가")
        println("    • 버그 재현이 어려움 (비결정적 변경)")
        println("    • 상태 변경 이력이 없음")
        println("    • 시간 여행 디버깅 불가능")
    }
}

// ============================================================
// ❌ 문제 4: Side Effect 관리 어려움
// ============================================================

class SideEffectProblem {
    private var isLoading = false
    private var data: List<String>? = null
    private var error: String? = null

    // 비동기 작업과 상태 변경이 뒤섞임
    fun fetchData() {
        isLoading = true
        error = null

        // 가상의 비동기 작업
        Thread {
            try {
                Thread.sleep(1000)
                data = listOf("Item 1", "Item 2")
                isLoading = false

                // UI 갱신은? 메인 스레드 전환은?
                // 에러 처리는? 취소는?
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }.start()
    }

    fun demonstrate() {
        println("--- Side Effect 관리 문제 ---")
        println()
        println("  fetchData() 내부에서:")
        println("    1. 상태 변경 (isLoading = true)")
        println("    2. 비동기 작업 (Thread)")
        println("    3. 성공/실패에 따른 상태 변경")
        println("    4. UI 갱신 (어떻게?)")
        println()
        println("  ❌ 문제점:")
        println("    • 상태 변경과 Side Effect가 뒤섞여 테스트 어려움")
        println("    • 비동기 작업 취소 처리 복잡")
        println("    • 여러 비동기 작업의 순서/병렬 처리")
        println("    • 에러 상태 관리 누락 가능")
    }
}

// ============================================================
// ❌ 문제 5: 상태 변경 이력 추적 불가
// ============================================================

class NoHistoryProblem {
    private var items = mutableListOf("A", "B", "C")

    fun add(item: String) {
        items.add(item)
        // 이전 상태는? 왜 추가했는지?
    }

    fun remove(index: Int) {
        items.removeAt(index)
        // 실수로 삭제했다면 복구는?
    }

    fun demonstrate() {
        println("--- 상태 이력 추적 불가 ---")
        println()
        println("  현재 상태: $items")
        add("D")
        println("  추가 후: $items")
        remove(0)
        println("  삭제 후: $items")
        println()
        println("  ❌ 문제점:")
        println("    • Undo/Redo 구현 어려움")
        println("    • 상태 변경 원인 추적 불가")
        println("    • 시간 여행 디버깅 불가")
        println("    • 상태 스냅샷 저장/복원 어려움")
    }
}

// ============================================================
// ❌ 문제 6: 테스트 어려움
// ============================================================

class TestingProblem {
    private var counter = 0
    private var apiService: ApiService? = null // 외부 의존성

    interface ApiService {
        fun save(value: Int): Boolean
    }

    fun incrementAndSave() {
        counter++
        val success = apiService?.save(counter) ?: false
        if (!success) {
            counter-- // 롤백
        }
    }

    fun demonstrate() {
        println("--- 테스트 어려움 ---")
        println()
        println("  incrementAndSave():")
        println("    1. 상태 변경 (counter++)")
        println("    2. 외부 API 호출 (apiService.save)")
        println("    3. 실패 시 롤백")
        println()
        println("  ❌ 문제점:")
        println("    • 상태 변경 로직과 Side Effect가 결합")
        println("    • ApiService를 Mock해도 테스트 복잡")
        println("    • 순수 함수가 아니라 입력/출력 검증 어려움")
        println("    • 상태 전이만 테스트하고 싶어도 불가능")
    }
}

fun main() {
    println("=== Unidirectional Data Flow (Redux) Pattern - 문제 상황 ===\n")

    // 문제 1: 분산된 상태
    ScatteredStateProblem().demonstrate()
    println()

    // 문제 2: 양방향 데이터 흐름
    BidirectionalFlowProblem().demonstrate()
    println()

    // 문제 3: 예측 불가능한 상태 변경
    UnpredictableStateProblem().demonstrate()
    println()

    // 문제 4: Side Effect
    SideEffectProblem().demonstrate()
    println()

    // 문제 5: 이력 추적 불가
    NoHistoryProblem().demonstrate()
    println()

    // 문제 6: 테스트 어려움
    TestingProblem().demonstrate()

    println("\n핵심 문제:")
    println("• 상태가 여러 곳에 분산되어 전체 앱 상태 파악 어려움")
    println("• 양방향 데이터 흐름으로 변경 추적 불가")
    println("• 상태를 어디서든 직접 변경 가능 → 예측 불가능")
    println("• Side Effect와 상태 변경이 뒤섞여 테스트 어려움")
    println("• 상태 변경 이력이 없어 디버깅/Undo 어려움")
}
