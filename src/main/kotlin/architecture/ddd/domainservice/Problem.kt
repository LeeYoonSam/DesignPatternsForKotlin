package architecture.ddd.domainservice

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.*

/**
 * 은행 송금 시스템 - Domain Service 패턴 적용 전
 *
 * 문제점:
 * - 도메인 로직이 Entity에 억지로 배치됨
 * - 여러 Aggregate를 조율하는 로직이 Entity에 존재
 * - Application Service에 도메인 로직이 유출됨
 * - 도메인 규칙이 분산되어 있음
 * - 테스트하기 어려움
 */
class Problem {

    data class AccountId(val value: String = UUID.randomUUID().toString())
    data class CustomerId(val value: String)

    data class Money(val amount: BigDecimal, val currency: String = "KRW") {
        operator fun plus(other: Money): Money {
            require(currency == other.currency) { "통화가 다릅니다" }
            return Money(amount + other.amount, currency)
        }

        operator fun minus(other: Money): Money {
            require(currency == other.currency) { "통화가 다릅니다" }
            return Money(amount - other.amount, currency)
        }

        operator fun times(rate: BigDecimal): Money {
            return Money(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP), currency)
        }

        fun isGreaterThanOrEqual(other: Money): Boolean {
            require(currency == other.currency) { "통화가 다릅니다" }
            return amount >= other.amount
        }

        companion object {
            fun of(amount: Long, currency: String = "KRW") =
                Money(BigDecimal.valueOf(amount), currency)
        }
    }

    // 계좌 Entity
    class Account(
        val id: AccountId = AccountId(),
        val customerId: CustomerId,
        val accountNumber: String,
        private var _balance: Money,
        val accountType: AccountType,
        val createdAt: LocalDateTime = LocalDateTime.now()
    ) {
        val balance: Money get() = _balance

        enum class AccountType { CHECKING, SAVINGS, PREMIUM }

        // 문제 1: 송금 로직이 Entity에 있지만, 실제로는 두 계좌가 필요
        fun transferTo(target: Account, amount: Money) {
            // 잔액 확인
            if (!_balance.isGreaterThanOrEqual(amount)) {
                throw IllegalStateException("잔액이 부족합니다")
            }

            // 문제: 같은 통화인지 확인 - 환율 계산 로직 필요
            if (_balance.currency != amount.currency) {
                throw IllegalStateException("통화가 다릅니다")
            }

            // 문제: 수수료 계산 로직이 여기에 있어야 하나?
            val fee = calculateTransferFee(amount)

            // 문제: 일일 한도 확인 로직은 어디에?
            // 문제: 자금세탁방지(AML) 체크는 어디에?

            // 출금
            _balance = _balance - amount - fee

            // 입금 - 다른 Entity를 직접 수정 (캡슐화 위반)
            target.deposit(amount)
        }

        // 문제 2: 수수료 계산이 Entity에 있음 (비즈니스 규칙이 Entity에 결합)
        private fun calculateTransferFee(amount: Money): Money {
            val feeRate = when (accountType) {
                AccountType.PREMIUM -> BigDecimal("0.001")  // 0.1%
                AccountType.SAVINGS -> BigDecimal("0.002")  // 0.2%
                AccountType.CHECKING -> BigDecimal("0.003") // 0.3%
            }
            return amount * feeRate
        }

        fun deposit(amount: Money) {
            require(amount.amount > BigDecimal.ZERO) { "입금액은 양수여야 합니다" }
            _balance = _balance + amount
        }

        fun withdraw(amount: Money) {
            require(_balance.isGreaterThanOrEqual(amount)) { "잔액이 부족합니다" }
            _balance = _balance - amount
        }
    }

    // Application Service - 도메인 로직이 유출됨
    class TransferApplicationService(
        private val accounts: MutableMap<AccountId, Account> = mutableMapOf()
    ) {
        // 문제: 도메인 로직이 Application Service에 있음
        fun transfer(
            fromAccountId: AccountId,
            toAccountId: AccountId,
            amount: Money
        ): TransferResult {
            val fromAccount = accounts[fromAccountId]
                ?: throw IllegalArgumentException("출금 계좌를 찾을 수 없습니다")
            val toAccount = accounts[toAccountId]
                ?: throw IllegalArgumentException("입금 계좌를 찾을 수 없습니다")

            // 문제: 비즈니스 규칙이 Application Service에 분산
            // 규칙 1: 같은 계좌로 송금 불가
            if (fromAccountId == toAccountId) {
                throw IllegalArgumentException("같은 계좌로 송금할 수 없습니다")
            }

            // 규칙 2: 일일 송금 한도 확인 (여기서 하는 게 맞나?)
            val dailyLimit = when (fromAccount.accountType) {
                Account.AccountType.PREMIUM -> Money.of(100_000_000)
                Account.AccountType.SAVINGS -> Money.of(10_000_000)
                Account.AccountType.CHECKING -> Money.of(5_000_000)
            }
            if (!dailyLimit.isGreaterThanOrEqual(amount)) {
                throw IllegalStateException("일일 송금 한도를 초과했습니다")
            }

            // 규칙 3: 수수료 계산 (Entity에서도 하고 여기서도 하고...)
            val feeRate = when (fromAccount.accountType) {
                Account.AccountType.PREMIUM -> BigDecimal("0.001")
                Account.AccountType.SAVINGS -> BigDecimal("0.002")
                Account.AccountType.CHECKING -> BigDecimal("0.003")
            }
            val fee = amount * feeRate

            // 규칙 4: 환율 계산 (여기서 하는 게 맞나?)
            var transferAmount = amount
            if (fromAccount.balance.currency != toAccount.balance.currency) {
                val exchangeRate = getExchangeRate(
                    fromAccount.balance.currency,
                    toAccount.balance.currency
                )
                transferAmount = Money(
                    amount.amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP),
                    toAccount.balance.currency
                )
            }

            // 규칙 5: AML 체크 (여기서 하는 게 맞나?)
            if (amount.amount >= BigDecimal("10000000")) {
                println("  [AML] 고액 송금 감지: ${amount.amount}")
                // 보고서 생성 로직...
            }

            // 실제 송금 수행
            fromAccount.withdraw(amount + fee)
            toAccount.deposit(transferAmount)

            return TransferResult(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                fee = fee,
                exchangedAmount = transferAmount,
                timestamp = LocalDateTime.now()
            )
        }

        // 문제: 환율 조회도 여기에
        private fun getExchangeRate(from: String, to: String): BigDecimal {
            return when {
                from == "KRW" && to == "USD" -> BigDecimal("0.00075")
                from == "USD" && to == "KRW" -> BigDecimal("1330.00")
                from == "KRW" && to == "JPY" -> BigDecimal("0.11")
                from == "JPY" && to == "KRW" -> BigDecimal("9.09")
                else -> BigDecimal.ONE
            }
        }

        fun createAccount(customerId: CustomerId, accountNumber: String, type: Account.AccountType): Account {
            val account = Account(
                customerId = customerId,
                accountNumber = accountNumber,
                _balance = Money.of(0),
                accountType = type
            )
            accounts[account.id] = account
            return account
        }

        fun deposit(accountId: AccountId, amount: Money) {
            val account = accounts[accountId]
                ?: throw IllegalArgumentException("계좌를 찾을 수 없습니다")
            account.deposit(amount)
        }
    }

    data class TransferResult(
        val fromAccountId: AccountId,
        val toAccountId: AccountId,
        val amount: Money,
        val fee: Money,
        val exchangedAmount: Money,
        val timestamp: LocalDateTime
    )
}

fun main() {
    val service = Problem.TransferApplicationService()

    // 계좌 생성
    val account1 = service.createAccount(
        Problem.CustomerId("CUST001"),
        "1234-5678-9012",
        Problem.Account.AccountType.PREMIUM
    )
    val account2 = service.createAccount(
        Problem.CustomerId("CUST002"),
        "9876-5432-1098",
        Problem.Account.AccountType.CHECKING
    )

    // 입금
    service.deposit(account1.id, Problem.Money.of(10_000_000))
    service.deposit(account2.id, Problem.Money.of(1_000_000))

    println("=== 송금 전 잔액 ===")
    println("계좌1: ${account1.balance}")
    println("계좌2: ${account2.balance}")
    println()

    // 송금
    println("=== 송금 실행 ===")
    val result = service.transfer(
        account1.id,
        account2.id,
        Problem.Money.of(1_000_000)
    )
    println("송금액: ${result.amount}")
    println("수수료: ${result.fee}")
    println()

    println("=== 송금 후 잔액 ===")
    println("계좌1: ${account1.balance}")
    println("계좌2: ${account2.balance}")
    println()

    println("=== 문제점 요약 ===")
    println("1. 송금 로직이 Entity와 Application Service에 분산")
    println("2. 수수료 계산 로직이 여러 곳에 중복")
    println("3. 환율 계산이 Application Service에 있음")
    println("4. AML 체크가 Application Service에 있음")
    println("5. 일일 한도 체크가 Application Service에 있음")
    println("6. Entity가 다른 Entity를 직접 수정 (캡슐화 위반)")
    println("7. 도메인 규칙이 흩어져 있어 이해하기 어려움")
    println("8. 테스트하기 어려움 (모든 로직이 결합)")
}
