package structural.adapter.payment

class Solve {
    interface PaymentMethod {
        fun processPayment(amount: Double)
    }

    class LegacyPaymentAdapter(private val legacySystem: Problem.LegacyPaymentSystem) : PaymentMethod {
        override fun processPayment(amount: Double) {
            legacySystem.processLegacyPayment(amount)
        }
    }

    class ModernPaymentAdapter(private val modernSystem: Problem.ModernPaymentSystem) : PaymentMethod {
        override fun processPayment(amount: Double) {
            modernSystem.processPayment(amount, "USD")
        }
    }

    class ImprovedPaymentProcessor {
        fun pay(paymentMethod: PaymentMethod, amount: Double) {
            paymentMethod.processPayment(amount)
        }
    }
}

fun main() {
    val legacySystem = Problem.LegacyPaymentSystem()
    val modernSystem = Problem.ModernPaymentSystem()

    val improvedProcessor = Solve.ImprovedPaymentProcessor()

    // 레거시 시스템 결제
    val legacyAdapter = Solve.LegacyPaymentAdapter(legacySystem)
    improvedProcessor.pay(legacyAdapter, 100.0)

    // 모던 시스템 결제
    val modernAdapter = Solve.ModernPaymentAdapter(modernSystem)
    improvedProcessor.pay(modernAdapter, 100.0)
}