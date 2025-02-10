package architecture.eventsourcing.bank

class Problem {
    // 전통적인 방식의 은행 계좌 시스템
    data class BankAccount(
        val accountId: String,
        var balance: Double
    ) {
        fun deposit(amount: Double) {
            balance += amount
        }

        fun withdraw(amount: Double) {
            if (balance >= amount) {
                balance -= amount
            } else {
                throw IllegalStateException("Insufficient funds")
            }
        }
    }

    // 은행 계좌 관리 시스템
    class BankAccountManager {
        private val accounts = mutableMapOf<String, BankAccount>()

        fun createAccount(accountId: String, initialBalance: Double = 0.0) {
            accounts[accountId] = BankAccount(accountId, initialBalance)
        }

        fun deposit(accountId: String, amount: Double) {
            accounts[accountId]?.deposit(amount)
                ?: throw IllegalArgumentException("Account not found")
        }

        fun withdraw(accountId: String, amount: Double) {
            accounts[accountId]?.withdraw(amount)
                ?: throw IllegalArgumentException("Account not found")
        }

        fun getBalance(accountId: String): Double {
            return accounts[accountId]?.balance
                ?: throw IllegalArgumentException("Account not found")
        }
    }
}

fun main() {
    val manager = Problem.BankAccountManager()

    // 계좌 생성 및 거래 수행
    manager.createAccount("ACC-001", 1000.0)
    println("Initial balance: ${manager.getBalance("ACC-001")}")

    manager.deposit("ACC-001", 500.0)
    println("After deposit: ${manager.getBalance("ACC-001")}")

    manager.withdraw("ACC-001", 200.0)
    println("After withdraw: ${manager.getBalance("ACC-001")}")

    // 문제점:
    // 1. 거래 내역을 추적할 수 없음
    // 2. 시스템 상태를 특정 시점으로 되돌릴 수 없음
    // 3. 감사(audit)가 어려움
    // 4. 동시성 문제 발생 가능
}