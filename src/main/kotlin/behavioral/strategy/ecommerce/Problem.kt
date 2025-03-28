package behavioral.strategy.ecommerce

import java.math.BigDecimal
import java.time.LocalDateTime

// 문제점을 보여주는 초기 구현
class LegacyDiscountCalculator {
    fun calculateDiscount(
        order: Order,
        customerType: CustomerType,
        season: String
    ): BigDecimal {
        var discount = BigDecimal.ZERO

        // 복잡한 할인 로직 하드코딩
        if (customerType == CustomerType.VIP) {
            discount += order.totalAmount * BigDecimal("0.15")
        }

        if (season == "WINTER") {
            discount += order.totalAmount * BigDecimal("0.20")
        }

        // 추가 할인 로직들...
        // 새로운 할인 정책 추가 시 메서드 수정 필요

        return discount
    }
}

fun main() {
    val legacyCalculator = LegacyDiscountCalculator()

    val order = Order(
        items = listOf(),
        customerType = CustomerType.VIP,
        totalAmount = BigDecimal("1000000"),
        purchaseDate = LocalDateTime.now()
    )

    val discount = legacyCalculator.calculateDiscount(
        order,
        CustomerType.VIP,
        "WINTER"
    )

    println("Legacy Discount: $discount")
}