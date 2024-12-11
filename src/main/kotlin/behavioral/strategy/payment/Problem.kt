package behavioral.strategy.payment

class Problem {
    /**
     * 문제점:
     *
     * 새로운 결제 방식 추가 시 PaymentProcessor 클래스를 직접 수정해야 함
     * 각 결제 방식의 로직이 단일 클래스에 강하게 결합됨
     * 확장성과 유연성이 떨어짐
     */
    class PaymentProcessor {
        fun processPayment(method: String, amount: Double) {
            when (method) {
                "credit" -> processCreditCardPayment(amount)
                "paypal" -> processPayPalPayment(amount)
                "bank" -> processBankTransferPayment(amount)
                else -> throw IllegalArgumentException("Unknown payment method")
            }
        }

        private fun processCreditCardPayment(amount: Double) {
            println("Processing credit card payment of $$amount")
            // 신용카드 결제 로직
        }

        private fun processPayPalPayment(amount: Double) {
            println("Processing PayPal payment of $$amount")
            // 페이팔 결제 로직
        }

        private fun processBankTransferPayment(amount: Double) {
            println("Processing bank transfer of $$amount")
            // 은행 이체 결제 로직
        }
    }
}

fun main() {
    val paymentProcessor = Problem.PaymentProcessor()

    paymentProcessor.processPayment("credit", 1000.0)
    paymentProcessor.processPayment("paypal", 1000.0)
    paymentProcessor.processPayment("bank", 1000.0)
}