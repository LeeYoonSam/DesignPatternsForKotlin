package behavioral.specification.product

/**
 * 해결책: Specification 패턴을 사용한 제품 필터링
 */
class Solution {
    // Specification 인터페이스
    interface Specification<T> {
        fun isSatisfiedBy(item: T): Boolean

        // 논리 연산자 오버로딩
        infix fun and(other: Specification<T>): Specification<T> =
            AndSpecification(this, other)

        infix fun or(other: Specification<T>): Specification<T> =
            OrSpecification(this, other)

        infix fun not(other: Specification<T>): Specification<T> =
            NotSpecification(this)
    }

    // 복합 Specification
    class AndSpecification<T>(
        private val spec1: Specification<T>,
        private val spec2: Specification<T>,
    ) : Specification<T> {
        override fun isSatisfiedBy(item: T): Boolean =
            spec1.isSatisfiedBy(item) && spec2.isSatisfiedBy(item)
    }

    class OrSpecification<T>(
        private val spec1: Specification<T>,
        private val spec2: Specification<T>,
    ) : Specification<T> {
        override fun isSatisfiedBy(item: T): Boolean =
            spec1.isSatisfiedBy(item) || spec2.isSatisfiedBy(item)
    }

    class NotSpecification<T>(
        private val spec: Specification<T>,
    ) : Specification<T> {
        override fun isSatisfiedBy(item: T): Boolean =
            !spec.isSatisfiedBy(item)
    }

    // 구체적인 Specification 구현체
    class PriceSpecification(
        private val minPrice: Double? = null,
        private val maxPrice: Double? = null
    ) : Specification<Product> {
        override fun isSatisfiedBy(item: Product): Boolean {
            return (minPrice == null || item.price >= minPrice) &&
                    (maxPrice == null || item.price <= maxPrice)
        }
    }

    class CategorySpecification(
        private val category: String
    ) : Specification<Product> {
        override fun isSatisfiedBy(item: Product): Boolean {
            return item.category == category
        }
    }

    class InStockSpecification : Specification<Product> {
        override fun isSatisfiedBy(item: Product): Boolean {
            return item.inStock
        }
    }

    // 제품 필터 클래스
    class ProductFilter {
        fun findBy(products: List<Product>, specification: Specification<Product>): List<Product> {
            return products.filter { specification.isSatisfiedBy(it) }
        }
    }

    // 제품 데이터 클래스
    data class Product(
        val id: String,
        val name: String,
        val price: Double,
        val category: String,
        val inStock: Boolean
    )
}

fun main() {
    val productFilter = Solution.ProductFilter()
    val products = listOf(
        Solution.Product("1", "Laptop", 1000.0, "Electronics", true),
        Solution.Product("2", "Phone", 500.0, "Electronics", false),
        Solution.Product("3", "Book", 20.0, "Books", true)
    )

    // 성공 예제: 복잡한 필터링 조건 조합
    val priceSpec = Solution.PriceSpecification(minPrice = 100.0, maxPrice = 2000.0)
    val categorySpec = Solution.CategorySpecification("Electronics")
    val inStockSpec = Solution.InStockSpecification()

    // 가격 AND 카테고리 AND 재고 있는 제품
    val complexFilteredProducts = productFilter.findBy(
        products = products,
        specification = priceSpec and categorySpec and inStockSpec
    )
    println("Complex Filtered Products: $complexFilteredProducts")

    // OR 연산자 사용 예제
    val alternativeSpec = Solution.PriceSpecification(maxPrice = 500.0) or Solution.CategorySpecification("Books")
    val alternativeProducts = productFilter.findBy(
        products = products,
        specification = alternativeSpec
    )
    println("Alternative Filtered Products: $alternativeProducts")
}