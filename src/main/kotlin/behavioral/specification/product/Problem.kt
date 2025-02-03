package behavioral.specification.product

/**
 * 문제점
 * - 복잡한 필터링 로직의 분산
 * - 코드 재사용성 저하
 * - 비즈니스 규칙의 하드코딩
 */
class Problem {
    data class Product(
        val id: String,
        val name: String,
        val price: Double,
        val category: String,
        val inStock: Boolean
    )

    class ProductFilter {
        fun filterProducts(
            products: List<Product>,
            minPrice: Double? = null,
            maxPrice: Double? = null,
            category: String? = null,
            inStockOnly: Boolean = false
        ): List<Product> {
            return products.filter { product ->
                var matches = true

                // 복잡한 필터링 로직
                minPrice?.let { matches = matches && product.price >= it }
                maxPrice?.let { matches = matches && product.price <= it }
                category?.let { matches = matches && product.category == it }

                if (inStockOnly) {
                    matches = matches && product.inStock
                }

                matches
            }
        }
    }
}

fun main() {
    val productFilter = Problem.ProductFilter()
    val products = listOf(
        Problem.Product("1", "Laptop", 1000.0, "Electronics", true),
        Problem.Product("2", "Phone", 500.0, "Electronics", false),
        Problem.Product("3", "Book", 20.0, "Books", true)
    )

    // 성공 예제: 필터링 가능
    val filteredProducts = productFilter.filterProducts(
        products = products,
        minPrice = 100.0,
        maxPrice = 2000.0,
        category = "Electronics",
        inStockOnly = true
    )
    println("Filtered Products: $filteredProducts")

    // 문제 발생 예제: 복잡한 필터링 시 가독성 저하
    val complexFilteredProducts = productFilter.filterProducts(
        products = products,
        minPrice = 10.0,
        maxPrice = 1000.0,
        category = "Electronics",
        inStockOnly = false
    )
    println("Complex Filtered Products: $complexFilteredProducts")
}