package structural.adapter.payment

/**
 * 문제 코드: 호환되지 않는 서로 다른 결제 시스템
 *
 * 문제점
 *
 * - 서로 다른 인터페이스를 가진 시스템 간 통합의 어려움
 * - 레거시 코드와 새로운 코드 사이의 호환성 문제
 * - 직접적인 수정이 어려운 외부 라이브러리나 시스템 연동
 * - 다양한 결제 시스템을 단일 인터페이스로 통합하기 어려움
 */
class Problem {
    class LegacyPaymentSystem {
        fun processLegacyPayment(amount: Double) {
            println("Legacy payment processed: $$amount")
        }
    }

    class ModernPaymentSystem {
        fun processPayment(amount: Double, currency: String) {
            println("Modern payment processed: $$amount in $currency")
        }
    }

    class PaymentProcessor {
        fun pay(paymentSystem: Any, amount: Double) {
            // 이 코드는 다른 결제 시스템과 호환되지 않음
            when (paymentSystem) {
                is LegacyPaymentSystem -> paymentSystem.processLegacyPayment(amount)
                is ModernPaymentSystem -> paymentSystem.processPayment(amount, "USD")
                else -> throw IllegalArgumentException("Unsupported payment system")
            }
        }
    }
}

fun main() {
    val paymentProcessor = Problem.PaymentProcessor()

    val legacyPaymentSystem = Problem.LegacyPaymentSystem()
    val modernPaymentSystem = Problem.ModernPaymentSystem()

    paymentProcessor.pay(legacyPaymentSystem, 100.0)
    paymentProcessor.pay(modernPaymentSystem, 100.0)
}