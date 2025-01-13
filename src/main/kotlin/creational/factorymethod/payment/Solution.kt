package creational.factorymethod.payment

class Solution {
    interface Payment {
        fun processPayment(amount: Double)
    }

    // 구체적인 결제 방식 구현
    class CreditCardPayment : Payment {
        override fun processPayment(amount: Double) {
            println("신용카드 결제 처리: ${amount}원")
        }
    }

    class BankTransferPayment : Payment {
        override fun processPayment(amount: Double) {
            println("계좌이체 결제 처리: ${amount}원")
        }
    }

    // 팩토리 메서드 인터페이스
    interface PaymentFactory {
        fun createPayment(): Payment
    }

    // 구체적인 팩토리 구현
    class CreditCardPaymentFactory: PaymentFactory {
        override fun createPayment(): Payment = CreditCardPayment()
    }

    class BankTransferPaymentFactory: PaymentFactory {
        override fun createPayment(): Payment = BankTransferPayment()
    }

    // 클라이언트 코드
    class PaymentProcessor(private val factory: PaymentFactory) {
        fun processPayment(amount: Double) {
            val payment = factory.createPayment()
            payment.processPayment(amount)
        }
    }
}

fun main() {
    // 신용카드 결제 처리
    val creditProcessor = Solution.PaymentProcessor(Solution.CreditCardPaymentFactory())
    creditProcessor.processPayment(50000.0)

    // 계좌이체 결제 처리
    val bankProcessor = Solution.PaymentProcessor(Solution.BankTransferPaymentFactory())
    bankProcessor.processPayment(30000.0)
}