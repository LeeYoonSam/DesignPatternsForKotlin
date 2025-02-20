package architecture.featuretoggle.management

class Problem {
    class PaymentProcessor {
        fun processPayment(amount: Double, method: String) {
            // Hard-coded new feature implementation
            val useNewPaymentSystem = true // This would require code change and redeployment

            if (useNewPaymentSystem) {
                println("Processing $amount using new payment system")
                processWithNewSystem(amount, method)
            } else {
                println("Processing $amount using old payment system")
                processWithOldSystem(amount, method)
            }
        }

        private fun processWithNewSystem(amount: Double, method: String) {
            println("New System: Validating payment method: $method")
            println("New System: Processing payment with enhanced security")
            println("New System: Payment completed successfully")
        }

        private fun processWithOldSystem(amount: Double, method: String) {
            println("Old System: Processing payment of $amount")
            println("Old System: Payment completed")
        }
    }

    class UserInterface {
        fun showDashboard(userId: String) {
            // Hard-coded feature flag
            val showNewDashboard = true // This would require code change and redeployment

            if (showNewDashboard) {
                println("Showing new dashboard with advanced analytics for user: $userId")
            } else {
                println("Showing old dashboard for user: $userId")
            }
        }
    }
}

fun main() {
    val paymentProcessor = Problem.PaymentProcessor()
    val ui = Problem.UserInterface()

    println("=== Processing Payment ===")
    paymentProcessor.processPayment(100.0, "CREDIT_CARD")

    println("\n=== Showing Dashboard ===")
    ui.showDashboard("user123")
}