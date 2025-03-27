package structural.adapter.externalapi

// 각기 다른 외부 결제 API들
class PayPalPaymentAPI {
    fun processPayPalPayment(amount: Double, currency: String): Boolean {
        println("PayPal 결제 처리: $amount $currency")
        return true
    }
}

class StripePaymentAPI {
    fun chargeStripePayment(amount: Int, curr: String): String {
        println("Stripe 결제 처리: $amount $curr")
        return "success"
    }
}

class ApplePayPaymentAPI {
    fun executeApplePayTransaction(price: Double, code: String): Int {
        println("ApplePay 결제 처리: $price $code")
        return 200
    }
}
