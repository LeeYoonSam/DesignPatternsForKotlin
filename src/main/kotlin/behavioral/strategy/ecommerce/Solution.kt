package behavioral.strategy.ecommerce

import java.math.BigDecimal
import java.time.LocalDateTime

class Solution {
    interface DiscountStrategy {
        fun calculateDiscount(order: Order): BigDecimal
    }

    // 구체적인 할인 전략들
    class CustomerTypeDiscountStrategy : DiscountStrategy {
        override fun calculateDiscount(order: Order): BigDecimal {
            return when (order.customerType) {
                CustomerType.VIP -> order.totalAmount * BigDecimal("0.15")
                CustomerType.PREMIUM -> order.totalAmount * BigDecimal("0.10")
                CustomerType.REGULAR -> order.totalAmount * BigDecimal("0.05")
            }
        }
    }

    class SeasonalDiscountStrategy : DiscountStrategy {
        override fun calculateDiscount(order: Order): BigDecimal {
            val currentSeason = determineSeason(order.purchaseDate)
            return when (currentSeason) {
                Season.WINTER -> order.totalAmount * BigDecimal("0.20")
                Season.SUMMER -> order.totalAmount * BigDecimal("0.15")
                else -> BigDecimal.ZERO
            }
        }

        private fun determineSeason(date: LocalDateTime): Season {
            return when (date.monthValue) {
                in 12 downTo 2 -> Season.WINTER
                in 6..8 -> Season.SUMMER
                else -> Season.OFF_SEASON
            }
        }

        enum class Season {
            WINTER, SUMMER, OFF_SEASON
        }
    }

    // 복합 할인 전략
    class CompositeDiscountStrategy(
        private val strategies: List<DiscountStrategy>
    ) : DiscountStrategy {
        override fun calculateDiscount(order: Order): BigDecimal {
            return strategies.fold(BigDecimal.ZERO) { acc, strategy ->
                acc + strategy.calculateDiscount(order)
            }
        }
    }

    // 할인 서비스
    class DiscountService(
        private val discountStrategy: DiscountStrategy
    ) {
        fun applyDiscount(order: Order): OrderSummary {
            val discountAmount = discountStrategy.calculateDiscount(order)
            val finalAmount = order.totalAmount - discountAmount

            return OrderSummary(
                originalAmount = order.totalAmount,
                discountAmount = discountAmount,
                finalAmount = finalAmount
            )
        }
    }

    // 주문 요약 데이터 클래스
    data class OrderSummary(
        val originalAmount: BigDecimal,
        val discountAmount: BigDecimal,
        val finalAmount: BigDecimal
    )
}

fun main() {
    val product = Product("노트북", ProductCategory.ELECTRONICS, BigDecimal("1000000"))

    val order = Order(
        items = listOf(OrderItem(product, 2)),
        customerType = CustomerType.VIP,
        totalAmount = BigDecimal("2000000"),
        purchaseDate = LocalDateTime.now()
    )

    // 복합 할인 전략 생성
    val compositeDiscountStrategy = Solution.CompositeDiscountStrategy(
        listOf(
            Solution.CustomerTypeDiscountStrategy(),
            Solution.SeasonalDiscountStrategy()
        )
    )

    val discountService = Solution.DiscountService(compositeDiscountStrategy)
    val orderSummary = discountService.applyDiscount(order)

    println("원본 금액: ${orderSummary.originalAmount}")
    println("할인 금액: ${orderSummary.discountAmount}")
    println("최종 금액: ${orderSummary.finalAmount}")
}