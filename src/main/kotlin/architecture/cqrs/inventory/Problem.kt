package architecture.cqrs.inventory

/**
 * 문제점
 * - 읽기/쓰기 작업의 성능 요구사항 차이
 * - 복잡한 도메인 모델 관리
 * - 확장성 제약
 * - 데이터 일관성 유지의 어려움
 * - 성능 최적화의 한계
 */
class Problem {
    // 단일 모델을 사용하는 재고 관리 시스템
    class InventorySystem {
        private val inventory = mutableMapOf<String, Product>()

        // 제품 추가/수정 (쓰기 작업)
        fun updateProduct(product: Product) {
            inventory[product.id] = product
        }

        // 재고 조정 (쓰기 작업)
        fun adjustStock(productId: String, quantity: Int) {
            val product = inventory[productId] ?: throw IllegalArgumentException("Product not found")
            product.quantity += quantity
        }

        // 제품 조회 (읽기 작업)
        fun getProduct(productId: String): Product? {
            return inventory[productId]
        }

        // 재고 리포트 생성 (복잡한 읽기 작업)
        fun generateStockReport(): List<StockReport> {
            return inventory.values.map { product ->
                StockReport(
                    productId = product.id,
                    name = product.name,
                    quantity = product.quantity,
                    value = product.price * product.quantity
                )
            }
        }
    }

    data class Product(
        val id: String,
        val name: String,
        var quantity: Int,
        val price: Double
    )

    data class StockReport(
        val productId: String,
        val name: String,
        val quantity: Int,
        val value: Double
    )
}

fun main() {
    // 문제가 있는 구현
    println("Traditional Implementation:")
    val traditionalSystem = Problem.InventorySystem()
    traditionalSystem.updateProduct(Problem.Product("1", "Product A", 10, 100.0))
    traditionalSystem.adjustStock("1", -5)
    println("Stock Report: ${traditionalSystem.generateStockReport()}")
}
