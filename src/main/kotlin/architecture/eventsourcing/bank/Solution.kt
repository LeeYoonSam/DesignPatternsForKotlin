package architecture.eventsourcing.bank

import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.util.UUID

class Solution {
    // 이벤트 정의
    sealed class AccountEvent {
        abstract val accountId: String
        abstract val timestamp: LocalDateTime
        abstract val eventId: String
    }

    data class AccountCreatedEvent(
        override val accountId: String,
        val initialBalance: Double,
        override val timestamp: LocalDateTime = LocalDateTime.now(),
        override val eventId: String = UUID.randomUUID().toString()
    ) : AccountEvent()

    data class MoneyDepositedEvent(
        override val accountId: String,
        val amount: Double,
        override val timestamp: LocalDateTime = LocalDateTime.now(),
        override val eventId: String = UUID.randomUUID().toString()
    ) : AccountEvent()

    data class MoneyWithdrawEvent(
        override val accountId: String,
        val amount: Double,
        override val timestamp: LocalDateTime = LocalDateTime.now(),
        override val eventId: String = UUID.randomUUID().toString()
    ) : AccountEvent()

    // 계좌 상태
    data class AccountState(
        val accountId: String,
        val balance: Double,
        val version: Long
    )

    // 이벤트 저장소
    class EventStore {
        private val events = mutableListOf<AccountEvent>()

        fun saveEvent(event: AccountEvent) {
            events.add(event)
        }

        fun getEvents(accountId: String): List<AccountEvent> {
            return events.filter { it.accountId == accountId }
        }

        fun getAllEvent(): List<AccountEvent> = events.toList()
    }

    // 계좌 집계자(Aggregator)
    class AccountAggregator {
        fun applyEvents(events: List<AccountEvent>): AccountState {
            var balance = 0.0
            var version = 0L
            var accountId = ""

            events.forEach { event ->
                when (event) {
                    is AccountCreatedEvent -> {
                        accountId = event.accountId
                        balance = event.initialBalance
                    }

                    is MoneyDepositedEvent -> {
                        balance += event.amount
                    }

                    is MoneyWithdrawEvent -> {
                        balance -= event.amount
                    }
                }
                version++
            }

            return AccountState(accountId, balance, version)
        }
    }

    // 이벤트 소싱 기반 은행 계좌 관리 시스템
    class EventSourcedBankAccount(
        private val eventStore: EventStore,
        private val aggregator: AccountAggregator
    ) {
        fun createAccount(accountId: String, initialBalance: Double) {
            val event = AccountCreatedEvent(accountId, initialBalance)
            eventStore.saveEvent(event)
        }

        fun deposit(accountId: String, amount: Double) {
            val event = MoneyDepositedEvent(accountId, amount)
            eventStore.saveEvent(event)
        }

        fun withdraw(accountId: String, amount: Double) {
            val currentState = getAccountState(accountId)
            if (currentState.balance >= amount) {
                val event = MoneyWithdrawEvent(accountId, amount)
                eventStore.saveEvent(event)
            } else {
                throw IllegalStateException("Insufficient funds")
            }
        }

        fun getAccountState(accountId: String): AccountState {
            val events = eventStore.getEvents(accountId)
            if (events.isEmpty()) {
                throw IllegalArgumentException("Account not found")
            }
            return aggregator.applyEvents(events)
        }

        fun getTransactionHistory(accountId: String): List<AccountEvent> {
            return eventStore.getEvents(accountId)
        }

        // 특정 시점의 계좌 상태 조회
        fun getAccountStateAt(accountId: String, timestamp: LocalDateTime): AccountState {
            val event = eventStore.getEvents(accountId)
                .filter { it.timestamp <= timestamp }

            return aggregator.applyEvents(event)
        }
    }
}

suspend fun main() {
    val eventStore = Solution.EventStore()
    val aggregator = Solution.AccountAggregator()
    val bank = Solution.EventSourcedBankAccount(eventStore, aggregator)

    printCurrentThread()

    // 계좌 생성 및 거래 수행
    bank.createAccount("ACC-001", 1000.0)
    println("Initial state: ${bank.getAccountState("ACC-001")}")

    bank.deposit("ACC-001", 500.0)
    println("After deposit: ${bank.getAccountState("ACC-001")}")

    performActionAfterDelay(5_000) {
        bank.withdraw("ACC-001", 200.0)
        println("After withdrawal: ${bank.getAccountState("ACC-001")}")

        // 거래 내역 조회
        println("\nTransaction History:")
        bank.getTransactionHistory("ACC-001").forEach { event ->
            println("- $event")
        }
    }

    delay(10_000)
    // 특정 시점의 계좌 상태 조회
    val pastTimestamp = LocalDateTime.now().minusSeconds(5)
    println("\nAccount state 5 seconds ago:")
    println(bank.getAccountStateAt("ACC-001", pastTimestamp))
}

suspend fun performActionAfterDelay(delayMillis: Long, action: () -> Unit) {
    delay(delayMillis)
    action()
}

fun printCurrentThread() {
    println(Thread.currentThread())
}