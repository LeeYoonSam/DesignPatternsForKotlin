package architecture.featuretoggle.management

import java.time.LocalDateTime

class Solution {

    // Feature Toggle Manager
    class FeatureToggleManager private constructor() {
        private val toggles = mutableMapOf<String, Toggle>()

        companion object {
            private val instance = FeatureToggleManager()
            fun getInstance() = instance
        }

        fun isEnabled(featureKey: String, context: FeatureContext = FeatureContext()): Boolean {
            return toggles[featureKey]?.isEnabled(context) ?: false
        }

        fun registerToggle(key: String, toggle: Toggle) {
            toggles[key] = toggle
        }
    }

    // Feature Context
    data class FeatureContext(
        val userId: String? = null,
        val userType: String? = null,
        val region: String? = null,
        val currentTime: LocalDateTime = LocalDateTime.now()
    )

    // Toggle Interface
    interface Toggle {
        fun isEnabled(context: FeatureContext): Boolean
    }

    // Different types of toggles
    class SimpleToggle(private val enabled: Boolean) : Toggle {
        override fun isEnabled(context: FeatureContext): Boolean = enabled
    }

    class TimeBasedToggle(
        private val startTime: LocalDateTime,
        private val endTime: LocalDateTime,
    ) : Toggle {
        override fun isEnabled(context: FeatureContext): Boolean {
            return context.currentTime.isAfter(startTime) &&
                    context.currentTime.isBefore(endTime)
        }
    }

    class PercentageToggle(private val percentage: Int) : Toggle {
        override fun isEnabled(context: FeatureContext): Boolean {
            return context.userId?.let {
                it.hashCode() % 100 < percentage
            } ?: false
        }
    }

    class UserTypeToggle(private val allowedTypes: Set<String>) : Toggle {
        override fun isEnabled(context: FeatureContext): Boolean {
            return context.userType?.let { allowedTypes.contains(it) } ?: false
        }
    }

    // Enhanced PaymentProcessor with Feature Toggles
    class PaymentProcessor {
        private val featureManager = FeatureToggleManager.getInstance()

        fun processPayment(amount: Double, method: String, context: FeatureContext) {
            if (featureManager.isEnabled("new-payment-system", context)) {
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

    // Enhanced UserInterface with Feature Toggles
    class UserInterface {
        private val featureManager = FeatureToggleManager.getInstance()

        fun showDashboard(context: FeatureContext) {
            if (featureManager.isEnabled("new-dashboard", context)) {
                println("Showing new dashboard with advanced analytics for user: ${context.userId}")
            } else {
                println("Showing old dashboard for user: ${context.userId}")
            }
        }
    }
}

fun main() {
    // Configure feature toggles
    val featureManager = Solution.FeatureToggleManager.getInstance()

    // Register different types of toggles
    featureManager.registerToggle("new-payment-system",
        Solution.TimeBasedToggle(
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30)
        )
    )

    featureManager.registerToggle("new-dashboard",
        Solution.UserTypeToggle(setOf("PREMIUM", "BETA_TESTER"))
    )

    val paymentProcessor = Solution.PaymentProcessor()
    val ui = Solution.UserInterface()

    // Test with different contexts
    println("=== Regular User ===")
    val regularContext = Solution.FeatureContext(
        userId = "user123",
        userType = "REGULAR"
    )
    paymentProcessor.processPayment(100.0, "CREDIT_CARD", regularContext)
    ui.showDashboard(regularContext)

    println("\n=== Premium User ===")
    val premiumContext = Solution.FeatureContext(
        userId = "user456",
        userType = "PREMIUM"
    )
    paymentProcessor.processPayment(200.0, "CREDIT_CARD", premiumContext)
    ui.showDashboard(premiumContext)

    println("\n=== A/B Testing ===")
    featureManager.registerToggle("new-feature", Solution.PercentageToggle(50))
    repeat(5) { userId ->
        val testContext = Solution.FeatureContext(userId = "user$userId")
        println("User $userId: Feature ${if (featureManager.isEnabled("new-feature", testContext)) "enabled" else "disabled"}")
    }
}