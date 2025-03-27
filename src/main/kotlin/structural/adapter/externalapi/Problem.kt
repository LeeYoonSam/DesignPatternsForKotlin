package structural.adapter.externalapi

fun main() {
    val payPalApi = PayPalPaymentAPI()
    val stripeApi = StripePaymentAPI()
    val applePayApi = ApplePayPaymentAPI()

    // 서로 다른 메서드 시그니처로 인한 통합의 어려움
    payPalApi.processPayPalPayment(100.0, "USD")
    stripeApi.chargeStripePayment(100, "USD")
    applePayApi.executeApplePayTransaction(100.0, "USD")
}