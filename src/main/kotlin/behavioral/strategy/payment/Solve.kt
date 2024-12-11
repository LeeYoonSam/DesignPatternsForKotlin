package behavioral.strategy.payment

class Solve {
    interface PaymentStrategy {
        fun pay(amount: Double)
    }

    class CreditCardPaymentStrategy(
        private val cardNumber: String,
        private val cvs: String,
        private val expirationDate: String,
    ) : PaymentStrategy {
        override fun pay(amount: Double) {
            println("Paying $$amount using Credit Card")
            // 신용카드 결제 상세 로직
            println("Card Number: $cardNumber")
        }
    }

    class PayPalPaymentStrategy(
        private val email: String
    ) : PaymentStrategy {
        override fun pay(amount: Double) {
            println("Paying $$amount using PayPal")
            // 페이팔 결제 상세 로직
            println("PayPal Email: $email")
        }
    }

    class BankTransferPaymentStrategy(
        private val bankAccount: String,
        private val bankName: String
    ) : PaymentStrategy {
        override fun pay(amount: Double) {
            println("Paying $$amount using Bank Transfer")
            // 은행 이체 결제 상세 로직
            println("Bank: $bankName, Account: $bankAccount")
        }
    }

    // 결제 처리자 (컨텍스트)
    class PaymentProcessor {
        private var paymentStrategy: PaymentStrategy? = null

        // 전략 설정 메서드
        fun setPaymentStrategy(strategy: PaymentStrategy) {
            this.paymentStrategy = strategy
        }

        // 결제 처리 메서드
        fun processPayment(amount: Double) {
            paymentStrategy?.pay(amount)
                ?: throw IllegalStateException("Payment strategy not set")
        }
    }
}

fun main() {
    val paymentProcessor = Solve.PaymentProcessor()

    // 신용카드 결제
    val creditCardStrategy = Solve.CreditCardPaymentStrategy(
        cardNumber = "1234-5678-9012-3456",
        cvs = "123",
        expirationDate = "12/25"
    )
    paymentProcessor.setPaymentStrategy(creditCardStrategy)
    paymentProcessor.processPayment(100.0)

    // 페이팔 결제
    val paypalStrategy = Solve.PayPalPaymentStrategy("user@example.com")
    paymentProcessor.setPaymentStrategy(paypalStrategy)
    paymentProcessor.processPayment(50.0)

    // 은행 이체 결제
    val bankTransferStrategy = Solve.BankTransferPaymentStrategy(
        "1234567890",
        "국민은행"
    )
    paymentProcessor.setPaymentStrategy(bankTransferStrategy)
    paymentProcessor.processPayment(200.0)
}