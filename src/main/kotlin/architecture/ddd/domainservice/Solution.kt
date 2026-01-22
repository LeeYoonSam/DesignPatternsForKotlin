package architecture.ddd.domainservice

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 은행 송금 시스템 - Domain Service 패턴 적용
 *
 * Domain Service 패턴의 장점:
 * - Entity에 속하지 않는 도메인 로직을 명확히 분리
 * - 여러 Aggregate 간 조율 로직의 적절한 위치 제공
 * - 도메인 언어로 비즈니스 규칙 표현
 * - 무상태(Stateless)로 테스트 용이
 * - 재사용성 향상
 */
class Solution {

    // ===== Value Objects =====

    @JvmInline
    value class AccountId(val value: String) {
        companion object {
            fun generate() = AccountId(UUID.randomUUID().toString())
        }
    }

    @JvmInline
    value class CustomerId(val value: String)

    @JvmInline
    value class TransferId(val value: String) {
        companion object {
            fun generate() = TransferId(UUID.randomUUID().toString())
        }
    }

    enum class Currency(val symbol: String, val code: String) {
        KRW("₩", "KRW"),
        USD("$", "USD"),
        JPY("¥", "JPY"),
        EUR("€", "EUR")
    }

    data class Money(val amount: BigDecimal, val currency: Currency = Currency.KRW) {
        init {
            require(amount.scale() <= 2) { "소수점 2자리까지만 허용됩니다" }
        }

        operator fun plus(other: Money): Money {
            requireSameCurrency(other)
            return Money(amount + other.amount, currency)
        }

        operator fun minus(other: Money): Money {
            requireSameCurrency(other)
            return Money(amount - other.amount, currency)
        }

        operator fun times(rate: BigDecimal): Money {
            return Money(amount.multiply(rate).setScale(2, RoundingMode.HALF_UP), currency)
        }

        fun isPositive() = amount > BigDecimal.ZERO
        fun isNegative() = amount < BigDecimal.ZERO
        fun isGreaterThanOrEqual(other: Money): Boolean {
            requireSameCurrency(other)
            return amount >= other.amount
        }

        fun convertTo(targetCurrency: Currency, exchangeRate: BigDecimal): Money {
            return Money(
                amount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP),
                targetCurrency
            )
        }

        private fun requireSameCurrency(other: Money) {
            require(currency == other.currency) {
                "통화가 다릅니다: $currency vs ${other.currency}"
            }
        }

        override fun toString() = "${currency.symbol}${amount}"

        companion object {
            fun of(amount: Long, currency: Currency = Currency.KRW) =
                Money(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), currency)

            fun zero(currency: Currency = Currency.KRW) =
                Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), currency)
        }
    }

    // ===== Entities =====

    enum class AccountType {
        CHECKING,   // 입출금 계좌
        SAVINGS,    // 저축 계좌
        PREMIUM     // 프리미엄 계좌
    }

    class Account private constructor(
        val id: AccountId,
        val customerId: CustomerId,
        val accountNumber: String,
        private var _balance: Money,
        val accountType: AccountType,
        val createdAt: LocalDateTime
    ) {
        val balance: Money get() = _balance

        fun canWithdraw(amount: Money): Boolean {
            return _balance.currency == amount.currency &&
                    _balance.isGreaterThanOrEqual(amount)
        }

        fun withdraw(amount: Money) {
            require(canWithdraw(amount)) { "출금할 수 없습니다: 잔액 부족 또는 통화 불일치" }
            _balance = _balance - amount
        }

        fun deposit(amount: Money) {
            require(amount.isPositive()) { "입금액은 양수여야 합니다" }
            require(_balance.currency == amount.currency) { "통화가 일치하지 않습니다" }
            _balance = _balance + amount
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Account) return false
            return id == other.id
        }

        override fun hashCode() = id.hashCode()

        companion object {
            fun create(
                customerId: CustomerId,
                accountNumber: String,
                currency: Currency,
                accountType: AccountType
            ): Account {
                return Account(
                    id = AccountId.generate(),
                    customerId = customerId,
                    accountNumber = accountNumber,
                    _balance = Money.zero(currency),
                    accountType = accountType,
                    createdAt = LocalDateTime.now()
                )
            }
        }
    }

    // ===== Domain Services =====

    /**
     * 수수료 계산 Domain Service
     * - 수수료 계산은 특정 Entity에 속하지 않는 도메인 로직
     * - 계좌 유형, 송금액, 회원 등급 등 여러 요소를 고려
     */
    class TransferFeeCalculationService {
        data class FeeCalculationResult(
            val baseFee: Money,
            val discountRate: BigDecimal,
            val finalFee: Money,
            val feeBreakdown: String
        )

        fun calculate(
            amount: Money,
            sourceAccountType: AccountType,
            isInternalTransfer: Boolean
        ): FeeCalculationResult {
            // 기본 수수료율 결정
            val baseFeeRate = when (sourceAccountType) {
                AccountType.PREMIUM -> BigDecimal("0.001")   // 0.1%
                AccountType.SAVINGS -> BigDecimal("0.002")   // 0.2%
                AccountType.CHECKING -> BigDecimal("0.003")  // 0.3%
            }

            // 기본 수수료 계산
            val baseFee = amount * baseFeeRate

            // 할인율 결정
            val discountRate = when {
                isInternalTransfer -> BigDecimal("0.5")  // 내부 이체 50% 할인
                amount.amount >= BigDecimal("10000000") -> BigDecimal("0.2")  // 고액 20% 할인
                else -> BigDecimal.ZERO
            }

            // 최종 수수료
            val discountAmount = baseFee * discountRate
            val finalFee = Money(
                (baseFee.amount - discountAmount.amount).setScale(2, RoundingMode.HALF_UP),
                amount.currency
            )

            val breakdown = buildString {
                append("기본 수수료: $baseFee")
                if (discountRate > BigDecimal.ZERO) {
                    append(", 할인: ${discountRate.multiply(BigDecimal(100))}%")
                }
            }

            return FeeCalculationResult(baseFee, discountRate, finalFee, breakdown)
        }
    }

    /**
     * 환율 Domain Service
     * - 환율 조회 및 환전 로직
     * - 외부 환율 API와 연동하는 인터페이스 역할
     */
    interface ExchangeRateService {
        fun getRate(from: Currency, to: Currency): BigDecimal
        fun convert(amount: Money, targetCurrency: Currency): Money
    }

    class DefaultExchangeRateService : ExchangeRateService {
        // 실제로는 외부 API나 DB에서 조회
        private val rates = mapOf(
            Pair(Currency.KRW, Currency.USD) to BigDecimal("0.00075"),
            Pair(Currency.USD, Currency.KRW) to BigDecimal("1330.00"),
            Pair(Currency.KRW, Currency.JPY) to BigDecimal("0.11"),
            Pair(Currency.JPY, Currency.KRW) to BigDecimal("9.09"),
            Pair(Currency.USD, Currency.JPY) to BigDecimal("149.50"),
            Pair(Currency.JPY, Currency.USD) to BigDecimal("0.0067"),
            Pair(Currency.KRW, Currency.EUR) to BigDecimal("0.00069"),
            Pair(Currency.EUR, Currency.KRW) to BigDecimal("1450.00")
        )

        override fun getRate(from: Currency, to: Currency): BigDecimal {
            if (from == to) return BigDecimal.ONE
            return rates[Pair(from, to)]
                ?: throw IllegalArgumentException("환율 정보가 없습니다: $from -> $to")
        }

        override fun convert(amount: Money, targetCurrency: Currency): Money {
            if (amount.currency == targetCurrency) return amount
            val rate = getRate(amount.currency, targetCurrency)
            return amount.convertTo(targetCurrency, rate)
        }
    }

    /**
     * 송금 한도 검증 Domain Service
     * - 일일/월간/건당 한도 검증
     * - 계좌 유형별 한도 정책
     */
    class TransferLimitService(
        private val transferHistory: TransferHistoryRepository
    ) {
        data class LimitCheckResult(
            val isWithinLimit: Boolean,
            val dailyUsed: Money,
            val dailyLimit: Money,
            val remainingDaily: Money,
            val message: String
        )

        fun checkLimit(
            accountId: AccountId,
            accountType: AccountType,
            amount: Money,
            date: LocalDate = LocalDate.now()
        ): LimitCheckResult {
            // 계좌 유형별 일일 한도
            val dailyLimit = when (accountType) {
                AccountType.PREMIUM -> Money.of(100_000_000, amount.currency)
                AccountType.SAVINGS -> Money.of(10_000_000, amount.currency)
                AccountType.CHECKING -> Money.of(5_000_000, amount.currency)
            }

            // 오늘 송금 내역 조회
            val dailyUsed = transferHistory.getDailyTotal(accountId, date)

            // 남은 한도
            val remaining = Money(
                (dailyLimit.amount - dailyUsed.amount).setScale(2, RoundingMode.HALF_UP),
                amount.currency
            )

            // 한도 확인
            val totalAfterTransfer = dailyUsed.amount + amount.amount
            val isWithinLimit = totalAfterTransfer <= dailyLimit.amount

            val message = if (isWithinLimit) {
                "한도 내 송금 가능"
            } else {
                "일일 한도 초과: 한도 $dailyLimit, 사용 $dailyUsed, 요청 $amount"
            }

            return LimitCheckResult(
                isWithinLimit = isWithinLimit,
                dailyUsed = dailyUsed,
                dailyLimit = dailyLimit,
                remainingDaily = remaining,
                message = message
            )
        }
    }

    /**
     * 자금세탁방지(AML) 검증 Domain Service
     * - 고액 거래 모니터링
     * - 의심 거래 탐지
     */
    class AmlCheckService {
        data class AmlCheckResult(
            val isPassed: Boolean,
            val riskLevel: RiskLevel,
            val requiresReporting: Boolean,
            val message: String
        )

        enum class RiskLevel { LOW, MEDIUM, HIGH }

        fun check(
            fromAccountId: AccountId,
            toAccountId: AccountId,
            amount: Money
        ): AmlCheckResult {
            // 고액 거래 체크 (1천만원 이상)
            val isHighValue = amount.amount >= BigDecimal("10000000")

            // 리스크 레벨 결정
            val riskLevel = when {
                amount.amount >= BigDecimal("100000000") -> RiskLevel.HIGH
                amount.amount >= BigDecimal("10000000") -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            // 보고 필요 여부
            val requiresReporting = riskLevel != RiskLevel.LOW

            val message = when (riskLevel) {
                RiskLevel.HIGH -> "고위험 거래: 즉시 보고 필요"
                RiskLevel.MEDIUM -> "중위험 거래: 모니터링 대상"
                RiskLevel.LOW -> "정상 거래"
            }

            // 실제로는 더 복잡한 규칙 적용
            // - 거래 패턴 분석
            // - 블랙리스트 확인
            // - 국가별 규제 확인 등

            return AmlCheckResult(
                isPassed = true,  // 실제로는 규칙에 따라 결정
                riskLevel = riskLevel,
                requiresReporting = requiresReporting,
                message = message
            )
        }
    }

    /**
     * 송금 Domain Service (핵심)
     * - 여러 Domain Service를 조합하여 송금 비즈니스 로직 수행
     * - 무상태(Stateless)
     * - 도메인 언어로 비즈니스 규칙 표현
     */
    class MoneyTransferService(
        private val feeCalculationService: TransferFeeCalculationService,
        private val exchangeRateService: ExchangeRateService,
        private val limitService: TransferLimitService,
        private val amlCheckService: AmlCheckService
    ) {
        data class TransferCommand(
            val fromAccountId: AccountId,
            val toAccountId: AccountId,
            val amount: Money,
            val description: String = ""
        )

        data class TransferResult(
            val transferId: TransferId,
            val fromAccountId: AccountId,
            val toAccountId: AccountId,
            val requestedAmount: Money,
            val fee: Money,
            val exchangedAmount: Money,
            val exchangeRate: BigDecimal?,
            val timestamp: LocalDateTime,
            val status: TransferStatus
        )

        enum class TransferStatus { COMPLETED, PENDING_REVIEW, REJECTED }

        /**
         * 송금 실행 - 핵심 도메인 로직
         */
        fun transfer(
            sourceAccount: Account,
            targetAccount: Account,
            command: TransferCommand
        ): TransferResult {
            val transferId = TransferId.generate()

            // 1. 기본 검증
            validateBasicRules(sourceAccount, targetAccount, command)

            // 2. 송금 한도 확인
            val limitCheck = limitService.checkLimit(
                sourceAccount.id,
                sourceAccount.accountType,
                command.amount
            )
            require(limitCheck.isWithinLimit) { limitCheck.message }

            // 3. AML 검사
            val amlCheck = amlCheckService.check(
                command.fromAccountId,
                command.toAccountId,
                command.amount
            )

            // 고위험 거래는 검토 대기
            if (amlCheck.riskLevel == AmlCheckService.RiskLevel.HIGH) {
                return TransferResult(
                    transferId = transferId,
                    fromAccountId = command.fromAccountId,
                    toAccountId = command.toAccountId,
                    requestedAmount = command.amount,
                    fee = Money.zero(command.amount.currency),
                    exchangedAmount = command.amount,
                    exchangeRate = null,
                    timestamp = LocalDateTime.now(),
                    status = TransferStatus.PENDING_REVIEW
                )
            }

            // 4. 수수료 계산
            val isInternalTransfer = sourceAccount.customerId == targetAccount.customerId
            val feeResult = feeCalculationService.calculate(
                command.amount,
                sourceAccount.accountType,
                isInternalTransfer
            )

            // 5. 환율 적용 (필요시)
            val exchangeRate: BigDecimal?
            val exchangedAmount: Money

            if (sourceAccount.balance.currency != targetAccount.balance.currency) {
                exchangeRate = exchangeRateService.getRate(
                    sourceAccount.balance.currency,
                    targetAccount.balance.currency
                )
                exchangedAmount = exchangeRateService.convert(
                    command.amount,
                    targetAccount.balance.currency
                )
            } else {
                exchangeRate = null
                exchangedAmount = command.amount
            }

            // 6. 잔액 확인
            val totalDeduction = command.amount + feeResult.finalFee
            require(sourceAccount.canWithdraw(totalDeduction)) {
                "잔액이 부족합니다: 필요 $totalDeduction, 잔액 ${sourceAccount.balance}"
            }

            // 7. 실제 이체 수행
            sourceAccount.withdraw(totalDeduction)
            targetAccount.deposit(exchangedAmount)

            return TransferResult(
                transferId = transferId,
                fromAccountId = command.fromAccountId,
                toAccountId = command.toAccountId,
                requestedAmount = command.amount,
                fee = feeResult.finalFee,
                exchangedAmount = exchangedAmount,
                exchangeRate = exchangeRate,
                timestamp = LocalDateTime.now(),
                status = TransferStatus.COMPLETED
            )
        }

        private fun validateBasicRules(
            sourceAccount: Account,
            targetAccount: Account,
            command: TransferCommand
        ) {
            require(command.fromAccountId != command.toAccountId) {
                "같은 계좌로 송금할 수 없습니다"
            }
            require(command.amount.isPositive()) {
                "송금액은 양수여야 합니다"
            }
            require(sourceAccount.id == command.fromAccountId) {
                "출금 계좌 ID가 일치하지 않습니다"
            }
            require(targetAccount.id == command.toAccountId) {
                "입금 계좌 ID가 일치하지 않습니다"
            }
        }
    }

    // ===== Repository Interface =====

    interface TransferHistoryRepository {
        fun getDailyTotal(accountId: AccountId, date: LocalDate): Money
        fun save(result: MoneyTransferService.TransferResult)
    }

    // 간단한 인메모리 구현
    class InMemoryTransferHistoryRepository : TransferHistoryRepository {
        private val history = mutableListOf<MoneyTransferService.TransferResult>()

        override fun getDailyTotal(accountId: AccountId, date: LocalDate): Money {
            return history
                .filter { it.fromAccountId == accountId }
                .filter { it.timestamp.toLocalDate() == date }
                .fold(Money.zero()) { acc, result -> acc + result.requestedAmount }
        }

        override fun save(result: MoneyTransferService.TransferResult) {
            history.add(result)
        }
    }

    // ===== Application Service =====

    class TransferApplicationService(
        private val accounts: MutableMap<AccountId, Account> = mutableMapOf(),
        private val transferService: MoneyTransferService,
        private val transferHistoryRepository: TransferHistoryRepository
    ) {
        fun createAccount(
            customerId: CustomerId,
            accountNumber: String,
            currency: Currency,
            type: AccountType
        ): Account {
            val account = Account.create(customerId, accountNumber, currency, type)
            accounts[account.id] = account
            return account
        }

        fun deposit(accountId: AccountId, amount: Money) {
            val account = accounts[accountId]
                ?: throw IllegalArgumentException("계좌를 찾을 수 없습니다")
            account.deposit(amount)
        }

        fun transfer(
            fromAccountId: AccountId,
            toAccountId: AccountId,
            amount: Money,
            description: String = ""
        ): MoneyTransferService.TransferResult {
            val fromAccount = accounts[fromAccountId]
                ?: throw IllegalArgumentException("출금 계좌를 찾을 수 없습니다")
            val toAccount = accounts[toAccountId]
                ?: throw IllegalArgumentException("입금 계좌를 찾을 수 없습니다")

            val command = MoneyTransferService.TransferCommand(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                description = description
            )

            // Domain Service에 위임
            val result = transferService.transfer(fromAccount, toAccount, command)

            // 이력 저장
            transferHistoryRepository.save(result)

            return result
        }

        fun getAccount(accountId: AccountId): Account? = accounts[accountId]
    }
}

fun main() {
    println("=== Domain Service 패턴 적용 데모 ===")
    println()

    // Domain Service 구성
    val transferHistoryRepository = Solution.InMemoryTransferHistoryRepository()
    val feeCalculationService = Solution.TransferFeeCalculationService()
    val exchangeRateService = Solution.DefaultExchangeRateService()
    val limitService = Solution.TransferLimitService(transferHistoryRepository)
    val amlCheckService = Solution.AmlCheckService()

    val transferService = Solution.MoneyTransferService(
        feeCalculationService = feeCalculationService,
        exchangeRateService = exchangeRateService,
        limitService = limitService,
        amlCheckService = amlCheckService
    )

    val appService = Solution.TransferApplicationService(
        transferService = transferService,
        transferHistoryRepository = transferHistoryRepository
    )

    // 계좌 생성
    val premiumAccount = appService.createAccount(
        Solution.CustomerId("CUST001"),
        "1234-5678-9012",
        Solution.Currency.KRW,
        Solution.AccountType.PREMIUM
    )
    val checkingAccount = appService.createAccount(
        Solution.CustomerId("CUST002"),
        "9876-5432-1098",
        Solution.Currency.KRW,
        Solution.AccountType.CHECKING
    )
    val usdAccount = appService.createAccount(
        Solution.CustomerId("CUST002"),
        "USD-1234-5678",
        Solution.Currency.USD,
        Solution.AccountType.SAVINGS
    )

    // 초기 입금
    appService.deposit(premiumAccount.id, Solution.Money.of(50_000_000))
    appService.deposit(checkingAccount.id, Solution.Money.of(1_000_000))
    appService.deposit(usdAccount.id, Solution.Money.of(10_000, Solution.Currency.USD))

    println("=== 초기 잔액 ===")
    println("프리미엄 계좌 (KRW): ${premiumAccount.balance}")
    println("체킹 계좌 (KRW): ${checkingAccount.balance}")
    println("USD 계좌: ${usdAccount.balance}")
    println()

    // 시나리오 1: 일반 송금
    println("=== 시나리오 1: 일반 국내 송금 ===")
    val result1 = appService.transfer(
        premiumAccount.id,
        checkingAccount.id,
        Solution.Money.of(1_000_000),
        "월세 송금"
    )
    println("송금 ID: ${result1.transferId}")
    println("송금액: ${result1.requestedAmount}")
    println("수수료: ${result1.fee}")
    println("상태: ${result1.status}")
    println("프리미엄 계좌 잔액: ${premiumAccount.balance}")
    println("체킹 계좌 잔액: ${checkingAccount.balance}")
    println()

    // 시나리오 2: 해외 송금 (환전)
    println("=== 시나리오 2: 해외 송금 (KRW → USD) ===")
    val result2 = appService.transfer(
        premiumAccount.id,
        usdAccount.id,
        Solution.Money.of(1_330_000),  // 약 $1,000
        "해외 송금"
    )
    println("송금 ID: ${result2.transferId}")
    println("송금액: ${result2.requestedAmount}")
    println("환전 후 금액: ${result2.exchangedAmount}")
    println("적용 환율: ${result2.exchangeRate}")
    println("수수료: ${result2.fee}")
    println("프리미엄 계좌 잔액: ${premiumAccount.balance}")
    println("USD 계좌 잔액: ${usdAccount.balance}")
    println()

    // 시나리오 3: 고액 송금 (AML 검토 대상)
    println("=== 시나리오 3: 고액 송금 (AML 검토) ===")
    val result3 = appService.transfer(
        premiumAccount.id,
        checkingAccount.id,
        Solution.Money.of(40_000_000),
        "부동산 계약금"
    )
    println("송금 ID: ${result3.transferId}")
    println("송금액: ${result3.requestedAmount}")
    println("상태: ${result3.status}")
    if (result3.status == Solution.MoneyTransferService.TransferStatus.PENDING_REVIEW) {
        println("→ 고액 거래로 검토 대기 중")
    }
    println()

    // Domain Service 개별 테스트
    println("=== Domain Service 개별 사용 예시 ===")

    // 수수료 계산 서비스
    println()
    println("--- 수수료 계산 서비스 ---")
    val feeResult = feeCalculationService.calculate(
        Solution.Money.of(5_000_000),
        Solution.AccountType.CHECKING,
        isInternalTransfer = false
    )
    println("기본 수수료: ${feeResult.baseFee}")
    println("할인율: ${feeResult.discountRate}")
    println("최종 수수료: ${feeResult.finalFee}")
    println("상세: ${feeResult.feeBreakdown}")

    // 환율 서비스
    println()
    println("--- 환율 서비스 ---")
    val krwToUsd = exchangeRateService.getRate(Solution.Currency.KRW, Solution.Currency.USD)
    println("KRW → USD 환율: $krwToUsd")
    val converted = exchangeRateService.convert(
        Solution.Money.of(1_000_000),
        Solution.Currency.USD
    )
    println("₩1,000,000 → $converted")

    // 한도 확인 서비스
    println()
    println("--- 한도 확인 서비스 ---")
    val limitCheck = limitService.checkLimit(
        premiumAccount.id,
        Solution.AccountType.PREMIUM,
        Solution.Money.of(50_000_000)
    )
    println("한도 내 여부: ${limitCheck.isWithinLimit}")
    println("일일 한도: ${limitCheck.dailyLimit}")
    println("오늘 사용: ${limitCheck.dailyUsed}")
    println("남은 한도: ${limitCheck.remainingDaily}")

    println()
    println("=== Domain Service 패턴 장점 ===")
    println("1. 송금 로직이 MoneyTransferService에 집중")
    println("2. 수수료, 환율, 한도, AML 각각 독립된 서비스로 분리")
    println("3. 각 서비스가 무상태(Stateless)로 테스트 용이")
    println("4. Entity는 자신의 상태만 관리 (캡슐화 유지)")
    println("5. 새로운 규칙 추가 시 해당 서비스만 수정")
    println("6. 도메인 언어로 비즈니스 규칙 표현")
}
