package structural.adapter.externalapi

class Solution {
    interface PaymentProcessor {
        fun processPayment(amount: Double, currency: String): Boolean
    }

    // PayPal 어댑터
    class PayPalAdapter(private val payPalApi: PayPalPaymentAPI) : PaymentProcessor {
        override fun processPayment(amount: Double, currency: String): Boolean {
            return payPalApi.processPayPalPayment(amount, currency)
        }
    }

    // Stripe 어댑터
    class StripeAdapter(private val stripeApi: StripePaymentAPI) : PaymentProcessor {
        override fun processPayment(amount: Double, currency: String): Boolean {
            return stripeApi.chargeStripePayment(amount.toInt(), currency) == "success"
        }
    }

    // ApplePay 어댑터
    class ApplePayAdapter(private val applePayApi: ApplePayPaymentAPI) : PaymentProcessor {
        override fun processPayment(amount: Double, currency: String): Boolean {
            return applePayApi.executeApplePayTransaction(amount, currency) == 200
        }
    }

    // 통합 결제 서비스
    class PaymentService {
        fun pay(processor: PaymentProcessor, amount: Double, currency: String): Boolean {
            return try {
                processor.processPayment(amount, currency)
            } catch (e: Exception) {
                println("결제 실패: ${e.message}")
                false
            }
        }
    }
}

fun main() {
    val paymentService = Solution.PaymentService()

    val payPalApi = PayPalPaymentAPI()
    val stripeApi = StripePaymentAPI()
    val applePayApi = ApplePayPaymentAPI()

    val payPalAdapter = Solution.PayPalAdapter(payPalApi)
    val stripeAdapter = Solution.StripeAdapter(stripeApi)
    val applePayAdapter = Solution.ApplePayAdapter(applePayApi)

    // 통합된 인터페이스로 결제 처리
    println("PayPal 결제: ${paymentService.pay(payPalAdapter, 100.0, "USD")}")
    println("Stripe 결제: ${paymentService.pay(stripeAdapter, 100.0, "USD")}")
    println("ApplePay 결제: ${paymentService.pay(applePayAdapter, 100.0, "USD")}")
}